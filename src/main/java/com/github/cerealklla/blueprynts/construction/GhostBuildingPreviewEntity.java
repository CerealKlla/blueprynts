package com.github.cerealklla.blueprynts.construction;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.blueprynts.registration.ModEntities;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Ghost markers driving the Building Locator's own minimap preview (user request, 2026-09-29) --
 * visible only to the one player holding the Locator, never persisted, purely a transient live
 * preview, same "existence-gated, regenerated fresh" shape as {@code
 * settlemynts.zone.GhostRoadAccessPreviewEntity}. Deliberately carries no color/role data of its
 * own beyond its floating {@link BlockState} -- Lyfe's minimap (a client-only consumer, no compiled
 * dependency on this mod) reads that state back directly off the vanilla {@link Display.BlockDisplay}
 * base class to decide what color dot to draw, so no new cross-mod payload or synced field is needed.
 * {@code role} distinguishes the static buildable-zone outline ("zone", drawn yellow) from the
 * player's live proposed-footprint outline ("footprint", drawn green/red depending on fit) so
 * regenerating one never has to discard the other.
 *
 * <p>Outline cells only, never a filled area -- a Large plot's buildable zone can be up to 50x50;
 * filling every cell would mean thousands of entities regenerated repeatedly while the player walks
 * around aiming, same performance reasoning {@code GhostRoadAccessPreviewEntity} already established
 * (perimeter cells only, not a filled region).
 */
public class GhostBuildingPreviewEntity extends Display.BlockDisplay {

    private static final double SEARCH_RADIUS_BLOCKS = 128.0;

    private UUID viewerId;
    private String role;

    public GhostBuildingPreviewEntity(EntityType<? extends GhostBuildingPreviewEntity> type, Level level) {
        super(type, level);
    }

    public static GhostBuildingPreviewEntity create(ServerLevel level, int x, int y, int z, UUID viewerId, String role, BlockState state) {
        GhostBuildingPreviewEntity marker = new GhostBuildingPreviewEntity(ModEntities.GHOST_BUILDING_PREVIEW.get(), level);
        marker.setPos(x, y, z);
        marker.viewerId = viewerId;
        marker.role = role;
        GhostBlockDisplays.setBlockState(marker, state);
        level.addFreshEntity(marker);
        return marker;
    }

    public static List<GhostBuildingPreviewEntity> findByViewerAndRole(ServerLevel level, UUID viewerId, String role) {
        net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewerId);
        if (player == null) {
            return List.of();
        }
        AABB searchBox = new AABB(
                player.getX() - SEARCH_RADIUS_BLOCKS, level.getMinY(), player.getZ() - SEARCH_RADIUS_BLOCKS,
                player.getX() + SEARCH_RADIUS_BLOCKS, level.getMaxY(), player.getZ() + SEARCH_RADIUS_BLOCKS);
        return level.getEntities(ModEntities.GHOST_BUILDING_PREVIEW.get(), searchBox,
                marker -> viewerId.equals(marker.viewerId) && role.equals(marker.role));
    }

    // Matches Settlemynts' own identical fix (util.GroundHeightFinder, see that class's own doc for
    // the full write-up) -- generous enough to find a genuine nearby gap without reaching into some
    // unrelated terrain feature far away if nothing reasonable exists near the reference at all.
    private static final int GROUND_SEARCH_RADIUS_BLOCKS = 24;

    /**
     * Discards every existing marker for this (viewer, role), then rebuilds one per given world
     * (x, z) at ground height near {@code referenceY} -- an empty list just clears it.
     *
     * @param referenceY real bug fix, 2026-10-29: used to be a plain {@code
     *                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} lookup, scanning down from the
     *                    level's absolute top -- wrong in the Nether (hits the solid roof first,
     *                    see Settlemynts' own {@code util.GroundHeightFinder} for the full incident)
     *                    and, more generally, wrong for any reference point genuinely standing in
     *                    open air (a cave, a tunnel, a cliffside build) rather than open sky, since
     *                    it could tunnel through solid rock looking for a surface further away than
     *                    a walkable gap in the other direction. Now searches outward from {@code
     *                    referenceY} in both directions for the nearest genuinely walkable gap --
     *                    callers pass the viewing player's own current Y, a real position they're
     *                    already standing at.
     */
    public static void regenerate(ServerLevel level, UUID viewerId, String role, BlockState state, List<Column> cells, int referenceY) {
        for (GhostBuildingPreviewEntity existing : findByViewerAndRole(level, viewerId, role)) {
            existing.discard();
        }
        for (Column cell : cells) {
            int groundY = findGroundNear(level, cell.x(), cell.z(), referenceY);
            create(level, cell.x(), groundY, cell.z(), viewerId, role, state);
        }
    }

    private static int findGroundNear(ServerLevel level, int x, int z, int referenceY) {
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        for (int d = 0; d <= GROUND_SEARCH_RADIUS_BLOCKS; d++) {
            int up = referenceY + d;
            if (up <= maxY && isWalkableSpace(level, x, up, z)) {
                return up;
            }
            if (d > 0) {
                int down = referenceY - d;
                if (down >= minY && isWalkableSpace(level, x, down, z)) {
                    return down;
                }
            }
        }
        // Last-resort fallback -- bounded near referenceY, never the level's absolute top.
        int top = Math.min(maxY, referenceY + 8);
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= minY; y--) {
            pos.setY(y);
            if (isSolid(level, pos)) {
                return y + 1;
            }
        }
        return referenceY;
    }

    /** {@code y} itself is open (non-solid, non-leaf) and {@code y - 1} is real solid support -- i.e. {@code y} is somewhere you could actually stand. */
    private static boolean isWalkableSpace(ServerLevel level, int x, int y, int z) {
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        return !isSolid(level, pos) && isSolid(level, pos.below());
    }

    private static boolean isSolid(ServerLevel level, net.minecraft.core.BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.getCollisionShape(level, pos).isEmpty();
    }

    public static void clear(ServerLevel level, UUID viewerId, String role) {
        for (GhostBuildingPreviewEntity existing : findByViewerAndRole(level, viewerId, role)) {
            existing.discard();
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return viewerId != null && viewerId.equals(player.getUUID());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Building Locator Preview");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        viewerId = input.read("ViewerId", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        role = input.getStringOr("Role", "zone");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("ViewerId", net.minecraft.core.UUIDUtil.CODEC, viewerId);
        output.putString("Role", role == null ? "zone" : role);
    }
}
