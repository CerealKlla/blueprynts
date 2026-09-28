package com.github.cerealklla.blueprynts.registration;

import com.github.cerealklla.blueprynts.BluepryntsMod;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteBlock;
import com.github.cerealklla.blueprynts.construction.ExistingBlock;
import com.github.cerealklla.blueprynts.construction.FootprintSlabBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BluepryntsMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BluepryntsMod.MODID);

    // v1: visually just a crafting-bench-alike (blockstate/model reuse vanilla crafting table
    // textures) -- see the plan's explicit "just looks like a normal crafting bench" instruction.
    public static final DeferredBlock<ConstructionSiteBlock> CONSTRUCTION_SITE = BLOCKS.register(
            "construction_site",
            id -> new ConstructionSiteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    // Indestructible-by-mining marker (see FootprintSlabBlock's own doc) -- v1 visually reuses
    // vanilla quartz block textures (a "marble slab" look with no new art needed).
    public static final DeferredBlock<FootprintSlabBlock> FOOTPRINT_SLAB = BLOCKS.register(
            "footprint_slab",
            id -> new FootprintSlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(-1.0F, 3600000.0F) // unbreakable, same convention as Blocks.BEDROCK
                    .sound(SoundType.STONE)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    // The "untouched site filler" sentinel (see ExistingBlock's own doc) -- visually brown wool,
    // genuinely a different block, so a player's own real brown wool is never mistaken for it.
    // Deliberately no registered BlockItem below -- this block is never meant to be *obtainable* at
    // all (empty loot table handles that), only ever placed programmatically by SiteTerrainOps.
    // Breakable like real wool, NOT unbreakable like FootprintSlabBlock/Bedrock -- a player needs to
    // be able to dig through/replace it while building, same as they always could with real dirt or
    // real wool before this sentinel existed; "never obtainable" and "unbreakable" are different
    // properties, and this block only wants the former (a real playtest report caught this: making
    // it bedrock-hard by copying FootprintSlabBlock's convention blocked normal building entirely).
    public static final DeferredBlock<ExistingBlock> EXISTING_BLOCK = BLOCKS.register(
            "existing_block",
            id -> new ExistingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.8F) // matches Blocks.BROWN_WOOL's own hardness
                    .sound(SoundType.WOOL)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredItem<BlockItem> CONSTRUCTION_SITE_ITEM = ITEMS.registerSimpleBlockItem(CONSTRUCTION_SITE);
    public static final DeferredItem<BlockItem> FOOTPRINT_SLAB_ITEM = ITEMS.registerSimpleBlockItem(FOOTPRINT_SLAB);
}
