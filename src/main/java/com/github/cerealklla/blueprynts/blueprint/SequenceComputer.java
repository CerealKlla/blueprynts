package com.github.cerealklla.blueprynts.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.cerealklla.blueprynts.construction.Column;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computes each {@link BlueprintCell}'s build-order {@code sequence} once, at Blueprint Save time
 * (design-document.md's "Planned: Passive Construction & Funding" section, captured 2026-09-29).
 *
 * <p><b>Two-part algorithm, both parts real correctness requirements, not just cosmetic ordering</b>:
 * <ol>
 *   <li><b>Layer order</b>: ground floor ({@code relY == 0}) first, then alternating below/above
 *   (-1, 1, -2, 2, ...). This alone guarantees any cell rests on already-placed support directly
 *   below it -- for any layer at or above ground, its own supporting layer (one below) is always
 *   scheduled earlier by this ordering, so a torch/sign/etc. standing on the floor is always placed
 *   after the floor itself.</li>
 *   <li><b>Same-layer support-dependency ordering</b>: a *horizontally* wall-mounted block (a ladder,
 *   wall torch, wall sign/banner/skull, a button/lever attached to a wall) isn't covered by the
 *   layer rule above -- its support is a neighbor in the *same* layer, which a pure random shuffle
 *   could place either side of. {@link #isWallMounted} detects this generically (a non-full-cube
 *   shape with a horizontal {@code FACING}, and -- for blocks with an {@code AttachFace} property
 *   like buttons/levers -- only when that face is {@code WALL}) rather than hand-listing block
 *   classes, then a same-layer topological sort (Kahn's algorithm, random tie-break among cells with
 *   no unmet dependency) ensures the wall it's mounted on is always sequenced first. Confirmed real:
 *   this is the same "support-dependent block pops off as a dropped item if placed before its
 *   neighbor" bug class already hit once during "The Ancestors" hand-authoring (see decisions.md).
 * </ol>
 *
 * <p><b>Known, accepted v1 limitations, not silently ignored</b>: a ceiling-mounted attachment
 * ({@code AttachFace.CEILING}) isn't given a same-layer dependency by this rule (its support is the
 * layer above, already safe per the layer-order guarantee, as long as {@link #isWallMounted} correctly
 * declines to generate a same-layer edge for it -- which it does, since it only fires for {@code
 * AttachFace.WALL}). Multi-anchor blocks (vines, scaffolding -- anything that can legally attach to
 * more than one specific neighbor) aren't handled; picking one neighbor as "the" dependency could be
 * wrong for those. This is still <b>not</b> full player-physical-reachability validation (could a
 * player actually stand somewhere, without flying, to place this block by hand) -- that's a
 * materially harder, separate, still-deferred problem.
 */
public final class SequenceComputer {

    private SequenceComputer() {
    }

    public static List<BlueprintCell> assign(List<BlueprintCell> cells) {
        Map<Integer, List<BlueprintCell>> byLayer = cells.stream()
                .collect(Collectors.groupingBy(BlueprintCell::relY, HashMap::new, Collectors.toCollection(ArrayList::new)));

        List<Integer> layerOrder = new ArrayList<>();
        if (byLayer.containsKey(0)) {
            layerOrder.add(0);
        }
        int maxAbs = cells.stream().mapToInt(c -> Math.abs(c.relY())).max().orElse(0);
        for (int depth = 1; depth <= maxAbs; depth++) {
            if (byLayer.containsKey(-depth)) {
                layerOrder.add(-depth);
            }
            if (byLayer.containsKey(depth)) {
                layerOrder.add(depth);
            }
        }

        RandomSource random = RandomSource.create();
        List<BlueprintCell> result = new ArrayList<>(cells.size());
        int sequence = 0;
        for (int layerY : layerOrder) {
            for (BlueprintCell cell : orderLayer(byLayer.get(layerY), random)) {
                result.add(new BlueprintCell(cell.relX(), cell.relY(), cell.relZ(), cell.state(), sequence++));
            }
        }
        return result;
    }

    /** Same-layer topological sort -- a wall-mounted cell always comes after the neighbor it's attached to. */
    private static List<BlueprintCell> orderLayer(List<BlueprintCell> layerCells, RandomSource random) {
        Map<Column, BlueprintCell> byColumn = new HashMap<>();
        for (BlueprintCell cell : layerCells) {
            byColumn.put(new Column(cell.relX(), cell.relZ()), cell);
        }

        Map<Column, Column> dependsOn = new HashMap<>();
        for (BlueprintCell cell : layerCells) {
            if (cell.state().isEmpty() || !isWallMounted(cell.state().get())) {
                continue;
            }
            Column column = new Column(cell.relX(), cell.relZ());
            Direction facing = cell.state().get().getValue(BlockStateProperties.HORIZONTAL_FACING);
            Column support = new Column(column.x() - facing.getStepX(), column.z() - facing.getStepZ());
            if (!support.equals(column) && byColumn.containsKey(support)) {
                dependsOn.put(column, support);
            }
        }

        List<Column> remaining = new ArrayList<>(byColumn.keySet());
        Set<Column> placed = new HashSet<>();
        List<BlueprintCell> ordered = new ArrayList<>(layerCells.size());
        while (!remaining.isEmpty()) {
            List<Column> ready = new ArrayList<>();
            for (Column column : remaining) {
                Column support = dependsOn.get(column);
                if (support == null || placed.contains(support)) {
                    ready.add(column);
                }
            }
            // A dependency cycle shouldn't normally occur (the heuristic only ever names one
            // neighbor), but if it somehow did, fall back to placing everything left rather than
            // looping forever.
            if (ready.isEmpty()) {
                ready.addAll(remaining);
            }
            Column chosen = ready.get(random.nextInt(ready.size()));
            ordered.add(byColumn.get(chosen));
            placed.add(chosen);
            remaining.remove(chosen);
        }
        return ordered;
    }

    /** A horizontally wall-mounted block: ladders, wall torches, wall signs/banners/skulls, wall-attached buttons/levers. */
    private static boolean isWallMounted(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE) && state.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.WALL) {
            return false;
        }
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return !Block.isShapeFullBlock(shape);
    }
}
