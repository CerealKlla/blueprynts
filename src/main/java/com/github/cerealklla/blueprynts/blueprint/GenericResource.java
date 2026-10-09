package com.github.cerealklla.blueprynts.blueprint;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A generic, item-agnostic resource category a Construction Box's funding cost is expressed in --
 * "Wood," not "Pine Log" (design-document.md's "Planned: Passive Construction & Funding" section,
 * captured 2026-09-29). A closed enum for v1, not an open trust-based registry like {@link
 * BlueprintType}/{@code ZoneType} -- nothing else needs to contribute a resource category yet, and
 * this is easy to open up later if that changes.
 *
 * <p>Each constant names a {@link TagKey} deciding which real items count toward it (so any wood log
 * satisfies "Logs," not one specific species) and a single representative {@link Item} for the
 * Construction Box screen's row icon.
 *
 * <p><b>{@code WOOD}'s tag was narrowed to logs only, 2026-10-09</b> -- real report: "Currently Plot
 * shows 'wood', this should say 'any logs' or 'any planks', but they are not the same thing, so
 * shouldn't be interchangeable" (one log yields 4 planks, so letting either satisfy the same amount
 * at 1:1 silently misprices the cost). {@code generic_wood} now matches only {@code #minecraft:logs}
 * -- the enum constant/tag id keep their original names to avoid a wider rename, but the label is
 * "Logs," not "Wood."
 */
public enum GenericResource {
    WOOD("Logs", tag("generic_wood"), Items.OAK_LOG),
    STONE("Stone", tag("generic_stone"), Items.COBBLESTONE);

    private final String label;
    private final TagKey<Item> tag;
    private final Item representativeItem;

    GenericResource(String label, TagKey<Item> tag, Item representativeItem) {
        this.label = label;
        this.tag = tag;
        this.representativeItem = representativeItem;
    }

    public String label() {
        return label;
    }

    public TagKey<Item> tag() {
        return tag;
    }

    public Item representativeItem() {
        return representativeItem;
    }

    public boolean matches(Item item) {
        return new ItemStack(item).is(tag);
    }

    // Real file: src/main/resources/data/blueprynts/tags/item/<path>.json -- singular "item", not
    // "items" (26.1.2 flattened vanilla's own registry tag folders to singular; a plural folder here
    // is silently never loaded as a real tag, no error, just an always-empty one -- real bug,
    // 2026-09-30, caught by every Construction Box deposit failing no matter the resource).
    private static TagKey<Item> tag(String path) {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath("blueprynts", path));
    }
}
