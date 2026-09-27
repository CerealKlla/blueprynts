package com.github.cerealklla.blueprynts.registration;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
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
}
