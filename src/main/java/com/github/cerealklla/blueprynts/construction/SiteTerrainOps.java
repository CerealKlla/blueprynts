package com.github.cerealklla.blueprynts.construction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.cerealklla.blueprynts.blueprint.TierSpec;
import com.github.cerealklla.blueprynts.registration.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pure-ish terrain operations for the Construction Site mechanic. Everything here works in
 * absolute world (x, z) columns (a live session's own coordinate space); only {@code
 * blueprint.BlueprintRecord} deals in positions relative to an origin corner, for storage/paste at
 * a different location.
 */
public final class SiteTerrainOps {

    // Generous clearing height above ground -- comfortably above the tallest Tier's above-ground
    // allowance (T5 = 14), so no floating overhangs survive leveling regardless of Tier.
    private static final int CLEAR_HEIGHT_ABOVE_GROUND = 24;

    // How far past a *loaded* Blueprint's own footprint bounding box to level, on top of the 1-block
    // margin the boundary ring itself needs to stand on. A real playtest bug: 1 block total wasn't
    // enough to read as "a cleared plot" on uneven/hilly natural terrain -- from a normal viewing
    // distance, untouched bumpy terrain was still visibly right up against the loaded structure,
    // making it hard to tell the load had worked at all. This is a placeholder value (tune by feel),
    // deliberately not tied to SizeClass -- Load's footprint size stays independent of whatever
    // Size/Tier is currently selected, per the existing design decision.
    private static final int LOAD_CLEARING_EXTRA_MARGIN = 4;

    private SiteTerrainOps() {
    }

    /** The outer clearing rectangle behind a Construction Site, in absolute world space. */
    public record OuterArea(int minX, int maxX, int minZ, int maxZ, int groundY) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** 0 if {@code (x, z)} is inside (or on the boundary of) this rectangle; otherwise the horizontal distance to its nearest edge. */
        public double distanceTo(double x, double z) {
            double dx = Math.max(0, Math.max(minX - x, x - maxX));
            double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /**
     * Computes the outer clearing area extending away from the placer (opposite {@code
     * intoSite}... actually {@code intoSite} already IS the away-from-placer direction the caller
     * resolved from the block's own FACING) starting immediately behind the site block, centered on
     * the perpendicular axis. Works for any of the four horizontal directions via {@code
     * intoSite}'s step vector and its clockwise perpendicular.
     */
    public static OuterArea computeOuterArea(BlockPos sitePos, Direction intoSite, int outerDimension) {
        Direction right = intoSite.getClockWise();
        int half = outerDimension / 2;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int forwardStep = 1; forwardStep <= outerDimension; forwardStep++) {
            for (int lateralStep = -half; lateralStep < outerDimension - half; lateralStep++) {
                int x = sitePos.getX() + intoSite.getStepX() * forwardStep + right.getStepX() * lateralStep;
                int z = sitePos.getZ() + intoSite.getStepZ() * forwardStep + right.getStepZ() * lateralStep;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        return new OuterArea(minX, maxX, minZ, maxZ, sitePos.getY());
    }

    /**
     * Flattens {@code area} to {@code area.groundY()}: clears everything above it up to a generous
     * height (no floating overhangs), unconditionally sets the floor layer itself to {@link
     * ExistingBlock} (not just when it happened to be air), and unconditionally replaces a solid
     * bedrock foundation reaching down to {@link TierSpec#maxDepthBelowGround()} below that.
     *
     * <p>The floor/foundation used to only touch the surface layer, and only filled it if it was
     * literally air -- fine over ordinary land, but a real playtest bug in ice/ocean biomes: the
     * "ground" at a site's own Y can be ice, snow, or open water over nothing solid at all for many
     * blocks down (icebergs, frozen ocean, ravines). Leveling only the top layer left a site's floor
     * sitting on top of whatever unstable terrain was actually there, visibly floating above the
     * surrounding landscape once the area above it was cleared. Every touched position's original
     * state is still captured first, so a full clear/restore is unaffected.
     *
     * <p>The floor uses the same {@code ExistingBlock} sentinel as {@link #applyBelowGroundWool} (not
     * real dirt, and not real brown wool either -- see that class's own doc for why it needs to be a
     * genuinely distinct block) so the two share one "untouched site filler, don't count it as
     * player-built" meaning end to end -- the same sentinel {@link #spawnRefundPile} already treats
     * as free below ground now also covers the floor row itself, and a footprint with subterranean
     * access reads as one continuous placeholder material rather than two different ones split at the
     * surface line. The floor is still always captured/pasted as a real state on save/load
     * (`BlueprintCell`'s own {@code PRE_EXISTING} check stays scoped to below ground only, per the
     * existing "the floor is never below ground" design decision) -- only the unconditional fill
     * material itself changed, not that behavior.
     */
    public static void levelClearingArea(ServerLevel level, OuterArea area, TerrainSnapshot snapshot) {
        int foundationDepth = TierSpec.maxDepthBelowGround();
        BlockState existingBlock = ModBlocks.EXISTING_BLOCK.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                cursor.set(x, area.groundY(), z);
                snapshot.captureIfAbsent(cursor, level.getBlockState(cursor));
                level.setBlock(cursor, existingBlock, 3);

                for (int y = area.groundY() - 1; y >= area.groundY() - foundationDepth; y--) {
                    cursor.set(x, y, z);
                    snapshot.captureIfAbsent(cursor, level.getBlockState(cursor));
                    level.setBlock(cursor, Blocks.BEDROCK.defaultBlockState(), 3);
                }

                for (int y = area.groundY() + 1; y <= area.groundY() + CLEAR_HEIGHT_ABOVE_GROUND; y++) {
                    // Captured unconditionally, even when already air -- a real playtest bug:
                    // skipping the capture for already-air positions meant restore() had nothing to
                    // revert them to later, so anything a player built in what used to be empty
                    // space survived a clear/auto-clear untouched instead of being wiped with
                    // everything else.
                    cursor.set(x, y, z);
                    snapshot.captureIfAbsent(cursor, level.getBlockState(cursor));
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Every column NOT in {@code markedColumns} that is 4-adjacent to one that is -- a plain set/neighbor check, no polygon math. */
    public static Set<Column> computeBoundaryRing(Set<Column> markedColumns) {
        Set<Column> ring = new HashSet<>();
        for (Column column : markedColumns) {
            addIfUnmarked(markedColumns, ring, column.x() + 1, column.z());
            addIfUnmarked(markedColumns, ring, column.x() - 1, column.z());
            addIfUnmarked(markedColumns, ring, column.x(), column.z() + 1);
            addIfUnmarked(markedColumns, ring, column.x(), column.z() - 1);
        }
        return ring;
    }

    private static void addIfUnmarked(Set<Column> markedColumns, Set<Column> ring, int x, int z) {
        Column candidate = new Column(x, z);
        if (!markedColumns.contains(candidate)) {
            ring.add(candidate);
        }
    }

    /**
     * For every marked column, replaces relative Y {@code -1} through {@code -depth} with {@link
     * ExistingBlock} -- never the ground surface layer itself (relative Y 0), per the user's explicit
     * clarification that a structure's own floor is never "below ground."
     */
    public static void applyBelowGroundWool(ServerLevel level, Set<Column> markedColumns, int groundY, int depth, TerrainSnapshot snapshot) {
        if (depth <= 0) {
            return;
        }
        BlockState existingBlock = ModBlocks.EXISTING_BLOCK.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Column column : markedColumns) {
            for (int i = 1; i <= depth; i++) {
                cursor.set(column.x(), groundY - i, column.z());
                BlockState state = level.getBlockState(cursor);
                snapshot.captureIfAbsent(cursor, state);
                level.setBlock(cursor, existingBlock, 3);
            }
        }
    }

    /** Replays every captured original state verbatim -- used for both a deliberate clear and the auto-clear/anti-farming safeguard. */
    public static void restore(ServerLevel level, TerrainSnapshot snapshot) {
        snapshot.capturedStates().forEach((pos, state) -> level.setBlock(pos, state, 3));
    }

    /** Where a refund drop actually ends up -- a plain scattered {@code ItemEntity} by default, or a Yconomics Loot Bag if that's loaded (see {@code bridge.YconomicsLootBridge}). */
    @FunctionalInterface
    public interface RefundSink {
        void deposit(ServerLevel level, Vec3 pos, ItemStack stack);
    }

    /**
     * For every captured position whose live state no longer matches its snapshotted original (i.e.
     * something the player actually built or changed), computes that block's normal drops and hands
     * them to {@code sink} at {@code pileCenter} instead of at each individual position -- must be
     * called before {@link #restore} reverts the terrain back, since it reads the live state.
     *
     * <p>Untouched {@link ExistingBlock} at or below the floor row ({@code pos.getY() <= groundY}) is
     * excluded even though it technically differs from the *pre-leveling* snapshot, since it's never
     * something the player actually built -- {@link #levelClearingArea}/{@link #applyBelowGroundWool}
     * both use it as the "untouched site filler" sentinel (the same one {@code BlueprintCell}'s own
     * {@code PRE_EXISTING} check already uses for save), floor and below-ground alike. A real playtest
     * report ("free dirt, presumably from the floor row") is what surfaced this before the floor
     * itself was unified onto this sentinel -- see {@link #levelClearingArea}'s own note. A floor or
     * below-ground position the player deliberately replaced with something else -- including with
     * real {@code Blocks.BROWN_WOOL}, genuinely distinct from the {@code ExistingBlock} sentinel
     * despite looking identical -- still refunds normally, since only the *untouched sentinel itself*
     * is skipped.
     */
    public static void spawnRefundPile(ServerLevel level, TerrainSnapshot snapshot, int groundY, Vec3 pileCenter, RefundSink sink) {
        BlockState existingBlock = ModBlocks.EXISTING_BLOCK.get().defaultBlockState();
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.capturedStates().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            BlockState live = level.getBlockState(pos);
            if (live.equals(original)) {
                continue;
            }
            if (pos.getY() <= groundY && live.equals(existingBlock)) {
                continue;
            }
            BlockEntity blockEntity = live.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            for (ItemStack drop : Block.getDrops(live, level, pos, blockEntity)) {
                if (!drop.isEmpty()) {
                    sink.deposit(level, pileCenter, drop);
                }
            }
        }
    }

    /**
     * The vanilla {@link net.minecraft.world.level.block.Rotation} that maps {@code from} onto
     * {@code to} -- e.g. {@code rotationBetween(NORTH, SOUTH) == CLOCKWISE_180}. Used to re-orient a
     * loaded Blueprint cell's own {@code BlockState} (see {@code ConstructionSiteBlockEntity#loadBlueprint})
     * the same way {@link #toWorldColumn} already re-orients its position -- both directions are
     * always one of the four horizontal cardinals here, so exactly one of the four {@code Rotation}
     * values always matches.
     */
    public static net.minecraft.world.level.block.Rotation rotationBetween(Direction from, Direction to) {
        for (net.minecraft.world.level.block.Rotation rotation : net.minecraft.world.level.block.Rotation.values()) {
            if (rotation.rotate(from) == to) {
                return rotation;
            }
        }
        return net.minecraft.world.level.block.Rotation.NONE;
    }

    /**
     * Converts an absolute world column to one relative to a site, in a forward/lateral basis
     * derived from {@code intoSite} (not raw world X/Z deltas) -- storing a saved Blueprint's
     * columns this way is what lets {@link #toWorldColumn} correctly re-orient it against a
     * *different* site's own facing when loaded, rather than always pasting in a fixed absolute
     * orientation.
     */
    public static Column toRelativeColumn(BlockPos sitePos, Direction intoSite, int worldX, int worldZ) {
        Direction right = intoSite.getClockWise();
        int dx = worldX - sitePos.getX();
        int dz = worldZ - sitePos.getZ();
        int forward = dx * intoSite.getStepX() + dz * intoSite.getStepZ();
        int lateral = dx * right.getStepX() + dz * right.getStepZ();
        return new Column(forward, lateral);
    }

    /** Inverse of {@link #toRelativeColumn} -- re-orients a relative (forward, lateral) column against {@code sitePos}/{@code intoSite}. */
    public static Column toWorldColumn(BlockPos sitePos, Direction intoSite, Column relative) {
        Direction right = intoSite.getClockWise();
        int x = sitePos.getX() + intoSite.getStepX() * relative.x() + right.getStepX() * relative.z();
        int z = sitePos.getZ() + intoSite.getStepZ() * relative.x() + right.getStepZ() * relative.z();
        return new Column(x, z);
    }

    /**
     * The clearing area for Loading a Blueprint: the bounding box of the given relative columns
     * (re-oriented against this site's own facing), expanded by one block of margin (so the boundary
     * ring wall has somewhere to stand) plus {@link #LOAD_CLEARING_EXTRA_MARGIN} more (so the result
     * actually reads as a cleared plot against uneven natural terrain, not just a tight box around
     * the structure) -- sized to the Blueprint's own stored footprint, not the Construction Site's
     * current Size selection.
     */
    public static OuterArea computeOuterAreaForFootprint(BlockPos sitePos, Direction intoSite, Collection<Column> relativeColumns) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Column relative : relativeColumns) {
            Column world = toWorldColumn(sitePos, intoSite, relative);
            minX = Math.min(minX, world.x());
            maxX = Math.max(maxX, world.x());
            minZ = Math.min(minZ, world.z());
            maxZ = Math.max(maxZ, world.z());
        }
        int margin = 1 + LOAD_CLEARING_EXTRA_MARGIN;
        return new OuterArea(minX - margin, maxX + margin, minZ - margin, maxZ + margin, sitePos.getY());
    }
}
