package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.blueprynts.api.BuildableArea;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Before a Blueprint pastes over (or a "Reposition Building" restores) a set of exact world
 * positions, moves any Building Supply Box currently standing at one of them out to a free spot at
 * the edge of the plot's buildable area, instead of letting the paste/restore silently overwrite
 * (and thus destroy, losing all its funding/identity state) it. Real bug, 2026-09-30: "I can move
 * the construction box... into the build area for the building. That is correct, I want that.
 * However if I then move the building and it happens to overlap the construction box... it just
 * deletes them and there's no way to move them." A future Plot Config Sign belongs in this same
 * check the moment it exists -- same failure mode (a real block silently sitting inside a footprint
 * the player just relocated the building onto), same fix -- see {@link #relocateObstructions}'s
 * single {@code instanceof} branch, structured so adding a second sign-detecting branch needs no
 * other change here.
 *
 * <p><b>Corrected 2026-09-30, same day</b>: the first version only ever checked a *single* Y (the
 * site's own ground floor) per footprint column, so a box placed anywhere off ground level -- an
 * upper floor, or simply wherever the player happened to click while the structure was still
 * mid-build -- was invisible to this check and still got silently destroyed. Callers now hand over
 * the real 3D set of exact positions the Blueprint occupies ({@code
 * RealBlueprintPlacement#computeFootprintPositions}, or a {@code TerrainSnapshot}'s own captured
 * positions, both already exact per-cell {@link BlockPos}es) instead of a flattened 2D column set.
 */
public final class ConstructionBoxOverlapGuard {

    private ConstructionBoxOverlapGuard() {
    }

    /**
     * @param footprintPositions every exact world position about to be pasted into or restored --
     *                           real per-cell positions (each with its own real Y), not a flattened
     *                           2D projection.
     * @param groundY            the Y a *relocated* box should land on -- the site's own floor level,
     *                           matching where a box/sign normally sits (relocation always targets
     *                           ground level, regardless of what Y the obstruction was actually found
     *                           at).
     * @param area                the owning plot's buildable rectangle to relocate along the edge of,
     *                            or {@code null} for an unbound box (falls back to a ring just outside
     *                            the footprint itself).
     * @param selfId              the {@code constructionId} of the box whose own in-progress method is
     *                            calling this (e.g. {@code attemptCompletion}, {@code
     *                            BuildingLocatorItem#useOn}) -- if that specific box turns out to be
     *                            one of the obstructions relocated here, its Java object is now stale
     *                            (a fresh {@link ConstructionBoxBlockEntity} was created at the new
     *                            position instead), so the caller MUST re-fetch and continue against
     *                            the position this returns rather than keep using its own {@code
     *                            this}/{@code box} reference. Empty if {@code selfId} wasn't among the
     *                            boxes relocated (including if nothing needed relocating at all).
     */
    public static Optional<BlockPos> relocateObstructions(ServerLevel level, Set<BlockPos> footprintPositions, int groundY, BuildableArea area, UUID selfId) {
        Set<Column> footprintColumns = new HashSet<>();
        for (BlockPos pos : footprintPositions) {
            footprintColumns.add(new Column(pos.getX(), pos.getZ()));
        }
        Set<Column> reserved = new HashSet<>();
        BlockPos selfNewPos = null;
        for (BlockPos pos : footprintPositions) {
            if (level.getBlockEntity(pos) instanceof ConstructionBoxBlockEntity box) {
                UUID movedId = box.constructionId();
                BlockPos newPos = relocateBox(level, box, pos, footprintColumns, reserved, groundY, area);
                reserved.add(new Column(newPos.getX(), newPos.getZ()));
                if (movedId != null && movedId.equals(selfId)) {
                    selfNewPos = newPos;
                }
            }
        }
        return Optional.ofNullable(selfNewPos);
    }

    private static BlockPos relocateBox(ServerLevel level, ConstructionBoxBlockEntity box, BlockPos oldPos,
                                         Set<Column> footprintColumns, Set<Column> reserved, int groundY, BuildableArea area) {
        BlockState state = level.getBlockState(oldPos);
        BlockPos newPos = findFreeEdgeSpot(oldPos, footprintColumns, reserved, groundY, area);

        level.removeBlock(oldPos, false);
        level.setBlock(newPos, state, 3);
        if (level.getBlockEntity(newPos) instanceof ConstructionBoxBlockEntity fresh) {
            fresh.copyStateFrom(box);
            ConstructionBoxIndex.get(level.getServer()).put(fresh.constructionId(), GlobalPos.of(level.dimension(), newPos));
        }
        return newPos;
    }

    /** Nearest column (to the box's own old position) on the buildable area's perimeter -- or, if unbound, a ring just outside the footprint -- that isn't part of the incoming footprint or already claimed by another obstruction relocated this same pass. */
    private static BlockPos findFreeEdgeSpot(BlockPos oldPos, Set<Column> footprintColumns, Set<Column> reserved, int groundY, BuildableArea area) {
        List<Column> candidates = area != null ? perimeterOf(area) : ringAround(footprintColumns);
        Column nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (Column candidate : candidates) {
            if (footprintColumns.contains(candidate) || reserved.contains(candidate)) {
                continue;
            }
            long dx = candidate.x() - oldPos.getX();
            long dz = candidate.z() - oldPos.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            // Every perimeter/ring cell is somehow already spoken for (a maximal-size building, or
            // several obstructions in one pass) -- better to land somewhere than nowhere.
            return oldPos.offset(1, 0, 0);
        }
        return new BlockPos(nearest.x(), groundY, nearest.z());
    }

    private static List<Column> perimeterOf(BuildableArea area) {
        List<Column> perimeter = new ArrayList<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            perimeter.add(new Column(x, area.minZ()));
            perimeter.add(new Column(x, area.maxZ()));
        }
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            perimeter.add(new Column(area.minX(), z));
            perimeter.add(new Column(area.maxX(), z));
        }
        return perimeter;
    }

    private static List<Column> ringAround(Set<Column> footprintColumns) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Column c : footprintColumns) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        List<Column> ring = new ArrayList<>();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            ring.add(new Column(x, minZ - 1));
            ring.add(new Column(x, maxZ + 1));
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            ring.add(new Column(minX - 1, z));
            ring.add(new Column(maxX + 1, z));
        }
        return ring;
    }
}
