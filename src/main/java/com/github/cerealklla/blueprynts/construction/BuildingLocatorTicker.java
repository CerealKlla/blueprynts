package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.blueprynts.api.BuildableArea;
import com.github.cerealklla.blueprynts.api.Blueprynts;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Live "where can this building go" minimap preview while holding a Building Locator (user request,
 * 2026-09-29) -- same tick-driven ghost-marker-regeneration shape as Settlemynts' own {@code
 * GhostRoadAccessPreviewEntity}/{@code onServerTick}. Two roles, regenerated independently: {@code
 * "zone"} is the box's own {@link BuildableArea} rectangle outline (yellow, doesn't depend on where
 * the player is aiming), and {@code "footprint"} is the specific proposed placement at the player's
 * current look target (green if it fits, red if it doesn't). Lyfe's minimap reads these entities
 * directly (no compiled dependency, no new payload) -- see that mod's own {@code
 * minimap.ClientGhostMarkerOutlines}. An unbound box ({@code buildableArea() == null}) shows no
 * yellow zone (nothing to outline) but still shows the footprint preview, always green.
 */
public final class BuildingLocatorTicker {

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
            BuildingLocatorItem.cancelIfHeld(level, player, player.getMainHandItem(), false,
                    s -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            BuildingLocatorItem.cancelIfHeld(level, player, player.getOffhandItem(), false,
                    s -> player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));

            UUID boxId = resolveHeldBoxId(player);
            if (boxId == null) {
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "zone");
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "footprint");
                continue;
            }
            Optional<BlockPos> boxPos = Blueprynts.getConstructionBoxPos(level, boxId);
            if (boxPos.isEmpty() || !(level.getBlockEntity(boxPos.get()) instanceof ConstructionBoxBlockEntity box)
                    || box.blueprintPlaced()) {
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "zone");
                GhostBuildingPreviewEntity.clear(level, player.getUUID(), "footprint");
                continue;
            }

            BuildableArea area = box.buildableArea();
            GhostBuildingPreviewEntity.regenerate(level, player.getUUID(), "zone", ZONE_STATE,
                    area == null ? List.of() : rectangleOutline(area));

            Direction facing = level.getBlockState(boxPos.get()).getValue(HorizontalDirectionalBlock.FACING);
            Direction intoSite = facing.getOpposite();
            int size = BuildingLocatorItem.resolveSize(box.placedBlueprintName());

            BlockPos anchor = raytraceGround(player, level);
            if (anchor == null) {
                GhostBuildingPreviewEntity.regenerate(level, player.getUUID(), "footprint", FIT_STATE, List.of());
                continue;
            }
            BuildingFootprintCheck check = BuildingFootprintCheck.evaluate(area, anchor, intoSite, size);
            GhostBuildingPreviewEntity.regenerate(level, player.getUUID(), "footprint",
                    check.fits() ? FIT_STATE : NO_FIT_STATE, check.outlineCells());
        }
    }

    private static UUID resolveHeldBoxId(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModBlocks.BUILDING_LOCATOR_ITEM.get())) {
            return main.get(ModDataComponents.BUILDING_LOCATOR_BOX_ID.get());
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModBlocks.BUILDING_LOCATOR_ITEM.get())) {
            return off.get(ModDataComponents.BUILDING_LOCATOR_BOX_ID.get());
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
        return hit.getBlockPos().above();
    }

    /** The rectangle's own border cells only -- a plain perimeter walk, no polygon/grid math needed now that this is just a rectangle. */
    private static List<Column> rectangleOutline(BuildableArea area) {
        List<Column> outline = new ArrayList<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            outline.add(new Column(x, area.minZ()));
            outline.add(new Column(x, area.maxZ()));
        }
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            outline.add(new Column(area.minX(), z));
            outline.add(new Column(area.maxX(), z));
        }
        return outline;
    }
}
