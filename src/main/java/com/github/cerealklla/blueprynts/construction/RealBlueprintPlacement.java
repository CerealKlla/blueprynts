package com.github.cerealklla.blueprynts.construction;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.cerealklla.blueprynts.blueprint.BlueprintCell;
import com.github.cerealklla.blueprynts.blueprint.BlueprintRecord;
import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * Pastes a saved Blueprint directly into the world at a Building Supply Box -- the real, "done"
 * placement a Blueprint picker selection should produce, as opposed to loading it into a
 * {@link ConstructionSiteBlockEntity} for further editing. Deliberately does none of what that class
 * does: no {@code ConstructionSiteBlock} is created, no {@code ExistingBlock} ("Brown Wool") floor
 * sentinel is painted, and no {@link GhostConstructionWallEntity} scaffolding is raised -- all three
 * are editor-only concepts (design-document.md), and a real playtest report, 2026-09-29, found all
 * three leaking into this flow before this class existed (see decisions.md, same date).
 *
 * <p>Anchored directly at the box's own position, with no extra offset -- an earlier version of this
 * flow walked one additional block off the box in its {@code FACING} direction before loading a
 * {@code ConstructionSiteBlock} there, which (combined with {@code computeOuterArea}'s own 1-block
 * forward gap, itself correct for the *editor*, where a player needs somewhere to stand) doubled up
 * into a real 1-2 block overrun past the plot's actual buildable (BLUE) area -- also a 2026-09-29
 * playtest report. A Blueprint's own {@code relativeColumns}/{@code cells} already start at forward
 * offset 1 (see {@code SiteTerrainOps#toRelativeColumn}), so anchoring at the box position with no
 * extra hop reproduces the same alignment the editor itself uses internally when designing/loading in
 * place.
 *
 * <p><b>Now captures a {@link TerrainSnapshot} of every position it overwrites, same day</b> -- a
 * real bug found live: "Reposition Building" used to just set every placed cell back to air, which
 * is only correct for cells that started as air. Any cell that had overwritten real terrain (a
 * below-ground basement wall dug into stone, a floor tile pasted over grass, etc.) got blasted to
 * air too instead of restored, visibly "tearing the ground out" instead of putting it back. {@link
 * #place} now captures each position's pre-paste state (mirroring {@code TerrainSnapshot}'s existing
 * use in the editor flow) and returns it in {@link Result} for the caller to hold onto (on {@code
 * ConstructionBoxBlockEntity}) until a reposition needs to undo it via {@code
 * SiteTerrainOps#restore}.
 */
public final class RealBlueprintPlacement {

    private RealBlueprintPlacement() {
    }

    public record Result(String error, TerrainSnapshot snapshot) {
        public static Result failure(String error) {
            return new Result(error, new TerrainSnapshot());
        }
    }

    /**
     * @param boxPos    the anchor position to paste at (the Building Supply Box's own position for an
     *                  initial placement, or a Building Locator's proposed position for a reposition).
     * @param boxFacing the box's stored {@code FACING} -- per the furnace convention this whole suite
     *                  already uses ({@code ConstructionSiteBlockEntity#intoSite}), the direction cells
     *                  actually extend into is the opposite of this.
     */
    public static Result place(ServerLevel level, BlockPos boxPos, Direction boxFacing, String name) {
        return place(level, boxPos, boxFacing, name, 100);
    }

    /**
     * @param pct the construction percentage to build up to (1-100) -- pastes every non-pre-existing
     *            cell whose {@link BlueprintCell#sequence()} falls at or below that percentage's
     *            cutoff, in ascending sequence order (not on-disk array order -- pasting a
     *            wall-mounted block before the neighbor it's attached to pops it off as a dropped
     *            item, see {@code blueprint.SequenceComputer}'s own doc). A Blueprint saved before
     *            the Sequence field existed has every cell at the {@code -1} backfill sentinel, which
     *            sorts identically for all cells -- falls back to on-disk order, matching this
     *            method's own pre-Sequence behavior exactly.
     */
    public static Result place(ServerLevel level, BlockPos boxPos, Direction boxFacing, String name, int pct) {
        return place(level, boxPos, boxFacing, name, pct, null);
    }

    /**
     * @param accumulateInto an existing {@link TerrainSnapshot} to keep capturing into (its own
     *                       {@code captureIfAbsent} already only ever records a position's *first*
     *                       pre-construction state), or {@code null} for a fresh one -- passing the
     *                       same snapshot across successive, increasing-{@code pct} calls (see
     *                       {@code ConstructionBoxBlockEntity#attemptCompletion}) is what lets
     *                       incremental, funding-driven construction correctly remember terrain from
     *                       *every* call, not just the most recent one.
     */
    public static Result place(ServerLevel level, BlockPos boxPos, Direction boxFacing, String name, int pct, TerrainSnapshot accumulateInto) {
        Optional<BlueprintRecord> found = BlueprintStorage.get().load(name);
        if (found.isEmpty()) {
            return Result.failure("No Blueprint named '" + name + "'.");
        }
        BlueprintRecord record = found.get();
        Direction intoSite = boxFacing.getOpposite();
        int groundY = SiteTerrainOps.siteFloorY(boxPos);

        // Same re-orientation SiteTerrainOps#rotationBetween already does for the editor's own Load --
        // see ConstructionSiteBlockEntity#loadBlueprint's identical comment for why this is needed.
        Rotation cellRotation = record.facing() == null
                ? Rotation.NONE
                : SiteTerrainOps.rotationBetween(record.facing(), intoSite);

        List<BlueprintCell> placeable = record.cells().stream()
                .filter(cell -> !cell.isPreExisting())
                .sorted(Comparator.comparingInt(BlueprintCell::sequence))
                .toList();
        int cutoff = Math.max(0, Math.min(placeable.size(), (int) Math.ceil(placeable.size() * (pct / 100.0))));

        TerrainSnapshot snapshot = accumulateInto != null ? accumulateInto : new TerrainSnapshot();
        for (int i = 0; i < cutoff; i++) {
            BlueprintCell cell = placeable.get(i);
            Column world = SiteTerrainOps.toWorldColumn(boxPos, intoSite, new Column(cell.relX(), cell.relZ()));
            BlockPos worldPos = new BlockPos(world.x(), groundY + cell.relY(), world.z());
            snapshot.captureIfAbsent(worldPos, level.getBlockState(worldPos));
            level.setBlock(worldPos, cell.state().orElseThrow().rotate(cellRotation), 3);
        }
        return new Result(null, snapshot);
    }

    /**
     * Every exact world position this Blueprint's non-pre-existing cells would occupy at {@code
     * anchor}/{@code facing}, across every floor/{@code relY} -- the real 3D volume, computed exactly
     * the same way {@link #place} itself computes each {@code worldPos} (not a flattened 2D column
     * projection). Used by {@link ConstructionBoxOverlapGuard} to detect a Building Supply Box (or a
     * future Plot Config Sign) sitting somewhere a paste/restore is about to overwrite, *before* it
     * runs -- see that class's own doc. Added/corrected 2026-09-30: an earlier, column-only version of
     * this (and the overlap guard using it) only ever checked a *single* Y (the site's own ground
     * floor) per column, so a Building Supply Box placed on an upper floor, or anywhere not exactly at
     * ground level, was invisible to the overlap check and still got silently destroyed by a
     * paste/restore that reached that exact cell. Empty if {@code name} doesn't resolve to a saved
     * Blueprint.
     */
    public static Set<BlockPos> computeFootprintPositions(BlockPos anchor, Direction facing, String name) {
        Optional<BlueprintRecord> found = BlueprintStorage.get().load(name);
        if (found.isEmpty()) {
            return Set.of();
        }
        BlueprintRecord record = found.get();
        Direction intoSite = facing.getOpposite();
        int groundY = SiteTerrainOps.siteFloorY(anchor);
        Set<BlockPos> positions = new HashSet<>();
        for (BlueprintCell cell : record.cells()) {
            if (cell.isPreExisting()) {
                continue;
            }
            Column world = SiteTerrainOps.toWorldColumn(anchor, intoSite, new Column(cell.relX(), cell.relZ()));
            positions.add(new BlockPos(world.x(), groundY + cell.relY(), world.z()));
        }
        return positions;
    }
}
