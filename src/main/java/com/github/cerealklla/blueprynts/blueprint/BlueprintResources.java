package com.github.cerealklla.blueprynts.blueprint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Derives {@link BlueprintRecord#resources()} from a Blueprint's own cells (added 2026-10-09,
 * explicit request: "a 'Resources:' map&lt;String, Int&gt; to list everything contained in the
 * blueprint... attainable items only; things like the custom blocks for allowing a plot to be
 * colorized... won't be reflected in this list" -- "will, in the future, be used... as a cost for
 * creating and repairing the building (currently free)").
 *
 * <p>Never hand-entered -- always recomputed from {@link BlueprintRecord#cells()} at Save time, same
 * "derived, not independently settable" precedent as Yconomics' {@code shop.ShopListing#buyPricePerUnit}.
 * A cell counts toward the total only if its {@link BlockState#getBlock()} has a real {@link
 * Item} form ({@code Block#asItem()} returns {@code Items.AIR} both for actual air and for any block
 * with no registered {@code BlockItem} -- this is exactly how the "untouched site filler" sentinel
 * ({@code construction.ExistingBlock}, intentionally never given an item form) and natural, no-item
 * placeholder blocks like farmland fall out of the count for free, with no hardcoded exclusion list
 * needed). A cell with no {@link BlueprintCell#state()} at all (the {@code PRE_EXISTING} sentinel,
 * i.e. "leave whatever terrain is already there") was never really part of the structure, so it's
 * skipped the same way.
 */
public final class BlueprintResources {

    private BlueprintResources() {
    }

    public static Map<Identifier, Integer> computeFrom(List<BlueprintCell> cells) {
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        for (BlueprintCell cell : cells) {
            if (cell.state().isEmpty()) {
                continue;
            }
            BlockState state = cell.state().get();
            Block block = state.getBlock();
            Item item = block.asItem();
            if (item == Items.AIR) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }
}
