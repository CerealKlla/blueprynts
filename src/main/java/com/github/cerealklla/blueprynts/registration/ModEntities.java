package com.github.cerealklla.blueprynts.registration;

import com.github.cerealklla.blueprynts.BluepryntsMod;
import com.github.cerealklla.blueprynts.construction.GhostBuildingPreviewEntity;
import com.github.cerealklla.blueprynts.construction.GhostConstructionWallEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BluepryntsMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<GhostConstructionWallEntity>> GHOST_CONSTRUCTION_WALL =
            ENTITY_TYPES.register("ghost_construction_wall", () -> EntityType.Builder.<GhostConstructionWallEntity>of(
                    GhostConstructionWallEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "ghost_construction_wall"))));

    public static final DeferredHolder<EntityType<?>, EntityType<GhostBuildingPreviewEntity>> GHOST_BUILDING_PREVIEW =
            ENTITY_TYPES.register("ghost_building_preview", () -> EntityType.Builder.<GhostBuildingPreviewEntity>of(
                    GhostBuildingPreviewEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "ghost_building_preview"))));
}
