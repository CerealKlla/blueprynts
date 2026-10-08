package com.github.cerealklla.blueprynts.construction;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.blueprynts.api.PlotArea;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Holds a Building Supply Box's full state between "Reposition Supply Box" removing the old block
 * (destroying its {@link ConstructionBoxBlockEntity} and all its NBT) and a {@code
 * SupplyBoxLocatorItem} placing a fresh one somewhere else -- user request, 2026-09-29 ("I'd like
 * the same ability to reposition the Building Supply Box too"). Plain server-side, in-memory, never
 * persisted -- deliberately not NBT/network-carried on the Locator item itself, since {@link
 * ConstructionBoxBlockEntity#removalSnapshot()} can be arbitrarily large (every block a placed
 * structure overwrote) and has no business round-tripping through an ItemStack's data components or
 * the network just to move a box a few blocks. The item only ever needs to carry the box's {@code
 * constructionId} (a plain UUID) -- everything else stays server-side and is looked up here. A
 * server restart mid-reposition loses the pending entry (the box's real state at that point was
 * already destroyed along with the old block anyway) -- an accepted, low-stakes edge case for a
 * transient window the player controls the length of.
 *
 * <p><b>Real bug fixed 2026-09-30</b>: an earlier version of this class decomposed the box into a
 * hand-picked subset of fields (identity, Blueprint binding, terrain snapshot) and left out the
 * funding tally (`required`/`supplied`), `everCompleted`, `minTimeTicks`/`boundAtGameTime`,
 * `pendingAnchor`/`pendingFacing`, `repositionPending`, and `lastBuiltPercent` entirely -- so a
 * relocated box came back with a blank, all-zero `required` array, and {@code
 * ConstructionBoxBlockEntity#fundedPercent}/{@code timeBasedPercent} both trivially return 100 when
 * nothing is required/no time gate is configured, instantly completing (or re-completing) whatever
 * structure the box was bound to the moment it re-placed, no matter how little was actually funded.
 * Fixed by holding the removed {@link ConstructionBoxBlockEntity} object itself (still a perfectly
 * valid, readable Java object after {@code level.removeBlock} -- that only evicts it from the level's
 * own tracking, it doesn't invalidate the reference) and restoring via its own {@link
 * ConstructionBoxBlockEntity#copyStateFrom} -- the same single, already-comprehensive "every field
 * that matters" method {@code ConstructionBoxOverlapGuard} already relies on, so this can never again
 * drift out of sync with that class by hand-copying a subset of fields here too.
 */
public final class PendingSupplyBoxRelocation {

    private record Snapshot(ConstructionBoxBlockEntity box, Direction facing, BlockPos originalPos) {
    }

    private static final Map<UUID, Snapshot> PENDING = new ConcurrentHashMap<>();

    private PendingSupplyBoxRelocation() {
    }

    /** Read-only peek at a pending relocation's plot area, for the live preview while the Locator is held -- doesn't consume the entry. {@code null} if nothing pending or the box was unbound. */
    public static PlotArea peekPlotArea(UUID constructionId) {
        Snapshot snapshot = PENDING.get(constructionId);
        return snapshot == null ? null : snapshot.box().plotArea();
    }

    /** Read-only peek at a pending relocation's preserved facing -- doesn't consume the entry. {@code null} if nothing pending for this id (an "already used" or never-relocated locator). */
    public static Direction peekFacing(UUID constructionId) {
        Snapshot snapshot = PENDING.get(constructionId);
        return snapshot == null ? null : snapshot.facing();
    }

    /** Read-only peek at the position this box was removed from -- used to auto-restore it there if the Locator is lost (walked away, died, logged out) instead of ever placed. Doesn't consume the entry. {@code null} if nothing pending. */
    public static BlockPos peekOriginalPos(UUID constructionId) {
        Snapshot snapshot = PENDING.get(constructionId);
        return snapshot == null ? null : snapshot.originalPos();
    }

    /** Must be called with {@code box} still fully valid -- i.e. before (or without) removing its block from the level. */
    public static void store(ConstructionBoxBlockEntity box, Direction facing) {
        PENDING.put(box.constructionId(), new Snapshot(box, facing, box.getBlockPos()));
    }

    /** @return the preserved facing to place the new block with, or {@code null} if nothing is pending for this id. */
    public static Direction applyTo(ConstructionBoxBlockEntity box, UUID constructionId) {
        Snapshot snapshot = PENDING.remove(constructionId);
        if (snapshot == null) {
            return null;
        }
        box.copyStateFrom(snapshot.box());
        return snapshot.facing();
    }
}
