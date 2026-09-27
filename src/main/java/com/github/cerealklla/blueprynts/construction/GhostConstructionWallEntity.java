package com.github.cerealklla.blueprynts.construction;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.blueprynts.registration.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * One point of a Construction Site's boundary-ring wall, floating {@code Blocks.GLASS} -- same
 * existence-gated/regenerated/walk-through-only pattern as Settlemynts' {@code
 * GhostBoundaryWallEntity}, but visible only to the one specific player currently working that
 * site (not a group), since a Construction Site session belongs to exactly one player at a time.
 */
public class GhostConstructionWallEntity extends Display.BlockDisplay {

    // Generous search radius -- a Construction Site's outer area tops out at Large (50x50), so this
    // comfortably covers it with margin.
    private static final double SEARCH_RADIUS_BLOCKS = 64.0;

    private BlockPos ownerSitePos;
    private UUID viewerId;

    public GhostConstructionWallEntity(EntityType<? extends GhostConstructionWallEntity> type, Level level) {
        super(type, level);
    }

    public static GhostConstructionWallEntity create(ServerLevel level, int x, int y, int z, BlockPos ownerSitePos, UUID viewerId) {
        GhostConstructionWallEntity wall = new GhostConstructionWallEntity(ModEntities.GHOST_CONSTRUCTION_WALL.get(), level);
        wall.setPos(x, y, z);
        wall.ownerSitePos = ownerSitePos;
        wall.viewerId = viewerId;
        GhostBlockDisplays.setBlockState(wall, Blocks.GLASS.defaultBlockState());
        level.addFreshEntity(wall);
        return wall;
    }

    /** Raises one glass column per ring cell, {@code height} blocks tall, starting at {@code groundY}. */
    public static void raise(ServerLevel level, BlockPos sitePos, Set<Column> ring, int groundY, int height, UUID viewerId) {
        for (Column column : ring) {
            for (int dy = 0; dy < height; dy++) {
                create(level, column.x(), groundY + dy, column.z(), sitePos, viewerId);
            }
        }
    }

    public static List<GhostConstructionWallEntity> findByOwnerSite(ServerLevel level, BlockPos ownerSitePos) {
        AABB searchBox = new AABB(
                ownerSitePos.getX() - SEARCH_RADIUS_BLOCKS, level.getMinY(), ownerSitePos.getZ() - SEARCH_RADIUS_BLOCKS,
                ownerSitePos.getX() + SEARCH_RADIUS_BLOCKS, level.getMaxY(), ownerSitePos.getZ() + SEARCH_RADIUS_BLOCKS);
        return level.getEntities(ModEntities.GHOST_CONSTRUCTION_WALL.get(), searchBox,
                wall -> ownerSitePos.equals(wall.ownerSitePos));
    }

    public static void discardAll(ServerLevel level, BlockPos ownerSitePos) {
        findByOwnerSite(level, ownerSitePos).forEach(wall -> wall.discard());
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
        return Component.literal("Construction Site Boundary");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ownerSitePos = input.read("OwnerSitePos", BlockPos.CODEC).orElse(null);
        viewerId = input.read("ViewerId", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("OwnerSitePos", BlockPos.CODEC, ownerSitePos);
        output.storeNullable("ViewerId", UUIDUtil.CODEC, viewerId);
    }
}
