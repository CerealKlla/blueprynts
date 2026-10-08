package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.blueprynts.api.PlotArea;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Live "where can the Building Supply Box go" minimap preview while holding a Supply Box Locator --
 * user report, 2026-09-29 ("Picking up the box did not show me valid areas to drop it, it let me put
 * it anywhere"): no preview of any kind existed for this item, unlike {@link BuildingLocatorTicker}'s
 * equivalent for the Building Locator, which this mirrors exactly in shape. Two roles: {@code
 * "supply_zone"} is the pending relocation's own {@link PlotArea} outline (yellow, doesn't depend on
 * where the player is aiming), and {@code "supply_footprint"} is a single-cell marker at the player's
 * current look target (green if inside the plot, red if not). An unbound box ({@code plotArea() ==
 * null}) shows no yellow zone but still shows the single-cell marker, always green.
 */
public final class SupplyBoxLocatorTicker {

    private static final int INTERVAL_TICKS = 10;
    private static final double REACH_BLOCKS = 48.0;
    private static final BlockState ZONE_STATE = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
    private static final BlockState FIT_STATE = Blocks.LIME_STAINED_GLASS.defaultBlockState();
    private static final BlockState NO_FIT_STATE = Blocks.RED_STAINED_GLASS.defaultBlockState();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            SupplyBoxLocatorItem.cancelIfHeld(level, player, player.getMainHandItem(), false,
                    s -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            SupplyBoxLocatorItem.cancelIfHeld(level, player, player.getOffhandItem(), false,
                    s -> player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));

            UUID constructionId = resolveHeldConstructionId(player);
            if (constructionId == null) {
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "supply_zone");
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "supply_footprint");
                continue;
            }

            PlotArea area = PendingSupplyBoxRelocation.peekPlotArea(constructionId);
            GhostBuildingPreviewEntity.regenerate(level, player.getUUID(), "supply_zone", ZONE_STATE,
                    area == null ? List.of() : interiorOutline(area));

            BlockPos target = raytraceGround(player, level);
            if (target == null) {
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "supply_footprint");
                continue;
            }
            boolean fits = area == null || area.contains(target.getX(), target.getZ());
            GhostBuildingPreviewEntity.regenerate(level, player.getUUID(), "supply_footprint",
                    fits ? FIT_STATE : NO_FIT_STATE, List.of(new Column(target.getX(), target.getZ())));
        }
    }

    private static UUID resolveHeldConstructionId(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModBlocks.SUPPLY_BOX_LOCATOR_ITEM.get())) {
            return main.get(ModDataComponents.SUPPLY_BOX_LOCATOR_BOX_ID.get());
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModBlocks.SUPPLY_BOX_LOCATOR_ITEM.get())) {
            return off.get(ModDataComponents.SUPPLY_BOX_LOCATOR_BOX_ID.get());
        }
        return null;
    }

    private static BlockPos raytraceGround(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH_BLOCKS));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        // relative(hit direction), matching SupplyBoxLocatorItem's own placement position exactly --
        // must always agree with the actual placement, or the preview would lie (2026-09-29 fix).
        return hit.getBlockPos().relative(hit.getDirection());
    }

    /** Every interior cell with at least one non-interior (or out-of-window) orthogonal neighbor -- a plain border walk over the real per-cell shape, not a rectangle perimeter, since a plot can be non-convex. */
    private static List<Column> interiorOutline(PlotArea area) {
        List<Column> outline = new ArrayList<>();
        for (int lz = 0; lz < area.height(); lz++) {
            for (int lx = 0; lx < area.width(); lx++) {
                int x = area.minX() + lx;
                int z = area.minZ() + lz;
                if (!area.contains(x, z)) {
                    continue;
                }
                if (!area.contains(x - 1, z) || !area.contains(x + 1, z)
                        || !area.contains(x, z - 1) || !area.contains(x, z + 1)) {
                    outline.add(new Column(x, z));
                }
            }
        }
        return outline;
    }
}
