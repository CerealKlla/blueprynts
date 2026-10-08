package com.github.cerealklla.blueprynts.registration;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    private ModDataComponents() {
    }

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BluepryntsMod.MODID);

    // Embedded on a granted Footprint Slab stack at Begin Design time -- carries which Construction
    // Site it belongs to, so placement never has to search nearby for an owning site (same reasoning
    // as Settlemynts' PlotSessionData on its Plot Placement Stake).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> FOOTPRINT_SLAB_ORIGIN =
            DATA_COMPONENTS.registerComponentType("footprint_slab_origin", builder -> builder
                    .persistent(BlockPos.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(BlockPos.CODEC)));

    // Embedded on a granted Building Locator stack -- carries which Building Supply Box it's bound to
    // (2026-09-29, "Reposition Building"), so the box's own Blueprint/facing/plot-boundary can always
    // be re-resolved via ConstructionBoxIndex without needing anything else stored on the item itself.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> BUILDING_LOCATOR_BOX_ID =
            DATA_COMPONENTS.registerComponentType("building_locator_box_id", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)));

    // Embedded on a granted Supply Box Locator stack -- "Reposition Supply Box," 2026-09-29. Only the
    // id: the box's full state (zone type, buildable area, placed Blueprint, removal snapshot, etc.)
    // stays entirely server-side in PendingSupplyBoxRelocation between removal and re-placement,
    // never round-tripped through this item or the network -- see that class's own doc for why.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> SUPPLY_BOX_LOCATOR_BOX_ID =
            DATA_COMPONENTS.registerComponentType("supply_box_locator_box_id", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)));

    // Shared by both Locator items above -- the game time (ServerLevel#getGameTime()) the stack was
    // granted, so their walk-away auto-cancel (2026-09-30) can skip a short grace window right after
    // granting instead of potentially firing on the very next tick. Real bug found live: a player
    // right-clicking a Building Supply Box to start a reposition is very often standing just outside
    // the plot's own strict interior (the box sits at the plot's edge, facing in) -- without a grace
    // window, "walked outside the plot" could already be true the instant the item was granted,
    // making it vanish again before the player had any chance to actually move it.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LOCATOR_GRANTED_AT_GAME_TIME =
            DATA_COMPONENTS.registerComponentType("locator_granted_at_game_time", builder -> builder
                    .persistent(com.mojang.serialization.Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));
}
