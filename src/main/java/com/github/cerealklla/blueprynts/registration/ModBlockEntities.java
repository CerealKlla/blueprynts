package com.github.cerealklla.blueprynts.registration;

import com.github.cerealklla.blueprynts.BluepryntsMod;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BluepryntsMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstructionSiteBlockEntity>> CONSTRUCTION_SITE =
            BLOCK_ENTITIES.register("construction_site",
                    () -> new BlockEntityType<>(ConstructionSiteBlockEntity::new, ModBlocks.CONSTRUCTION_SITE.get()));
}
