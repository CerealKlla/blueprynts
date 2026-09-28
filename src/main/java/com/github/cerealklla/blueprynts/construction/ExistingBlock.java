package com.github.cerealklla.blueprynts.construction;

import net.minecraft.world.level.block.Block;

/**
 * The "untouched site filler" sentinel {@link SiteTerrainOps} places for a Construction Site's
 * floor and below-ground depth -- visually identical to {@code Blocks.BROWN_WOOL} (same texture,
 * see its blockstate/model), but a genuinely distinct block. Using real brown wool as that sentinel
 * meant a player who legitimately wanted to build *with* brown wool couldn't -- their own wool would
 * be indistinguishable from the filler and silently treated as "not really built," both for {@code
 * BlueprintCell}'s {@code PRE_EXISTING} save check and for {@link SiteTerrainOps#spawnRefundPile}'s
 * "don't refund the filler" skip.
 *
 * <p>Indestructible by ordinary mining and an empty loot table (same convention as {@link
 * FootprintSlabBlock}/{@code Blocks.BEDROCK}) -- it's never meant to be obtained at all, by mining or
 * otherwise, so it has no registered {@code BlockItem}. The refund pile can never hand one back.
 */
public class ExistingBlock extends Block {

    public ExistingBlock(Properties properties) {
        super(properties);
    }
}
