package com.github.cerealklla.blueprynts.bridge;

import java.util.List;

import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.zone.SeedListing;
import com.github.cerealklla.settlemynts.zone.ShopResource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Registers Shop Seed Catalogs (see Settlemynts' {@code zone.ShopSeeding}'s own doc) for the three
 * Zone Types this mod fully understands the meaning of without needing any Lyfe knowledge --
 * Lumberyard, Building Supplier, and Stonemason. The equivalent for Armorer/Blacksmith/Restaurant
 * lives in Lyfe's own {@code structure.ShopSeedCatalogs} instead. This class must only ever be
 * referenced from behind a {@code ModList.get().isLoaded("settlemynts")} check at the call site.
 *
 * <p><b>Mineshaft</b> (added 2026-10-11) -- the full {@code mineshaft.MineLootTable} item pool, so a
 * freshly-finalized plot's owner can list its own ore output without first discovering the exact
 * item ids by hand. Real gap found live: a brand-new Mineshaft plot had no default listings at all
 * and "would not be functional without player intervention," unlike Lumberyard/Farm/Stonemason,
 * which all get a catalog here already. Prices below are placeholder fallbacks only (see the note
 * right below) -- Yconomics' derived pricing takes over automatically once that mod is loaded.
 *
 * <p><b>Listing-registration only -- never auto-stocked, 2026-10-06 correction</b> -- a prior pass
 * had every one of these entries auto-deposited into the shop's own boxes (a full stack of every
 * wood species, every door/stair, every cut-stone good), none of which was ever actually asked for.
 * Real user correction: "I said gold and research notes/recipes get restocked overnight, nothing
 * else. The idea is that the plot would have to buy from other plots/settlements if it needed such
 * resources." What *was* actually asked for (clarified the same day): pre-registering a listing's
 * item+price so an owner doesn't have to manually "Add Listing (Held Item)" one at a time for every
 * single good a shop of this type would plausibly sell -- the actual stock a buyer can purchase
 * still only ever comes from whatever's really sitting in the plot's own boxes (an NPC worker's
 * production, the owner's own stock, a delivery from another plot). {@code
 * zone.ShopSeeding#applyCatalog} enforces this generically (never deposits for a resource-backed
 * {@link SeedListing}) -- nothing below needs to special-case it.
 */
public final class ShopSeedCatalogs {

    private ShopSeedCatalogs() {
    }

    public static void registerAll() {
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "lumberyard"),
                (level, plotAnchor, plotTier) -> lumberyardListings(level, plotAnchor));
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "farm"),
                (level, plotAnchor, plotTier) -> farmListings(level, plotAnchor));
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "building_supplier"),
                (level, plotAnchor, plotTier) -> BUILDING_SUPPLIER_LISTINGS);
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "stonemason"),
                (level, plotAnchor, plotTier) -> STONEMASON_LISTINGS);
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "mineshaft"),
                (level, plotAnchor, plotTier) -> MINESHAFT_LISTINGS);
    }

    // ---- Lumberyard: every wood species' logs/planks, the plot's own local species priced well
    // below the "has to be shipped in" exotic ones. Not Tier-gated -- wood has no Tier concept
    // anywhere in this suite. stockCount below is the listing's own reference/cap value only -- see
    // this class's own doc, nothing is ever actually deposited for these at seed time.

    private static final int LOCAL_LOG_PRICE = 2;
    private static final int EXOTIC_LOG_PRICE = 6;
    private static final int LOCAL_PLANK_PRICE = 1;
    private static final int EXOTIC_PLANK_PRICE = 3;
    private static final int LOG_STOCK = 64;
    private static final int PLANK_STOCK = 64;
    private static final int STICK_PRICE = 1;
    private static final int STICK_STOCK = 64;
    private static final int LOCAL_APPLE_PRICE = 2;
    private static final int EXOTIC_APPLE_PRICE = 4;
    private static final int APPLE_STOCK = 16;

    private static final List<String> WOOD_SPECIES = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak");

    private static List<SeedListing> lumberyardListings(ServerLevel level, BlockPos plotAnchor) {
        String localSpecies = localWoodSpecies(level, plotAnchor);
        List<SeedListing> listings = new java.util.ArrayList<>();
        for (String species : WOOD_SPECIES) {
            boolean local = species.equals(localSpecies);
            listings.add(SeedListing.ofResource(
                    ShopResource.ofItem(Identifier.withDefaultNamespace(species + "_log")),
                    local ? LOCAL_LOG_PRICE : EXOTIC_LOG_PRICE, LOG_STOCK));
            listings.add(SeedListing.ofResource(
                    ShopResource.ofItem(Identifier.withDefaultNamespace(species + "_planks")),
                    local ? LOCAL_PLANK_PRICE : EXOTIC_PLANK_PRICE, PLANK_STOCK));
        }
        // Sticks and Apples (added 2026-10-21, real request: "it should list anything a tree can drop
        // automatically") -- the only other real items a Lumberjack's own harvest can ever produce,
        // via its leaf-loot-table roll (see settlemynts' lumberyard.LumberjackWorkGoal#harvest's own
        // doc: every leaf type can drop a Stick, Oak specifically can also drop an Apple; a dropped
        // Sapling is deliberately discarded there, never obtainable this way, so it's not listed
        // here either). Stick has no species concept, one flat listing; Apple gets the same local/
        // exotic split as logs/planks, keyed off whether this plot's own biome is genuinely Oak.
        listings.add(SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("stick")), STICK_PRICE, STICK_STOCK));
        listings.add(SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("apple")),
                localSpecies.equals("oak") ? LOCAL_APPLE_PRICE : EXOTIC_APPLE_PRICE, APPLE_STOCK));
        return listings;
    }

    /**
     * The wood species native to the biome at {@code pos} -- direct vanilla biome checks, not
     * Cartographyr's coarser {@code natural.NaturalRegionProfile} families (those group e.g. every
     * taiga variant together, which is fine for naming a region but too coarse for "which single
     * species is actually native here"). Defaults to oak, vanilla's own most common/default wood.
     */
    private static String localWoodSpecies(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(Biomes.SPARSE_JUNGLE) || biome.is(Biomes.JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE)) {
            return "jungle";
        }
        if (biome.is(Biomes.TAIGA) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.GROVE) || biome.is(Biomes.SNOWY_SLOPES)) {
            return "spruce";
        }
        if (biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
            return "acacia";
        }
        if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return "mangrove";
        }
        if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN)) {
            return biome.is(Biomes.PALE_GARDEN) ? "pale_oak" : "dark_oak";
        }
        if (biome.is(Biomes.CHERRY_GROVE)) {
            return "cherry";
        }
        if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            return "birch";
        }
        return "oak";
    }

    // ---- Farm: every produce type a Farm worker can grow (explicit user request, 2026-10-06: "a
    // farm should seed the store with entries for buying/selling any kind of plant they can grow,
    // wheat, potatoes, carrots, etc"), the plot's own biome-grown crop priced well below the "has to
    // be shipped in" ones -- same shape as Lumberyard's wood-species listings above. Carrot is
    // listed for sale even though no biome ever makes it the worker's chosen crop (see {@code
    // farm.FarmCropSpecies}) -- it's still a real, generally-grown produce item a farm shop should
    // offer, just always priced as imported.

    private static final int LOCAL_CROP_PRICE = 1;
    private static final int EXOTIC_CROP_PRICE = 3;
    private static final int CROP_STOCK = 64;

    private static final List<String> FARM_PRODUCE = List.of("wheat", "potato", "carrot", "beetroot", "melon_slice");

    /**
     * Real Seed items (added 2026-10-28, Plant Selection, explicit user spec: "All Seeds should be
     * listed for sale by default from Farm plots, with 0 preferred inventory") -- {@code
     * SEED_STOCK = 0} is the "0 preferred inventory" itself: a real, priced, sellable listing that
     * starts with zero physical stock, so nothing is ever conjured for free -- only what the worker
     * NPC's Bonus Seed roll (see {@code farm.FarmerWorkGoal#harvest}) or a player actually deposits
     * ever shows up to sell. Pumpkin/Wheat/Beetroot/Melon match {@code farm.FarmCropSpecies#ALL}'s
     * own 4 real seed items (Potato/Carrot have no distinct Seeds item -- their own produce item,
     * already listed in {@link #FARM_PRODUCE} above, doubles as the planting material). Torchflower
     * Seeds/Pitcher Pod are listed too even though no Farm worker here can grow either (same
     * established precedent {@link #FARM_PRODUCE}'s own Carrot entry already set -- "still a real,
     * generally-grown [item] a farm shop should offer, just always priced as imported"), always at
     * the exotic tier since neither has a real "local to this biome" concept.
     */
    private static final List<String> FARM_SEEDS = List.of(
            "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "torchflower_seeds", "pitcher_pod");
    private static final int SEED_STOCK = 0;

    private static List<SeedListing> farmListings(ServerLevel level, BlockPos plotAnchor) {
        String localProduce = localCropItem(level, plotAnchor);
        List<SeedListing> listings = new java.util.ArrayList<>();
        for (String produce : FARM_PRODUCE) {
            boolean local = produce.equals(localProduce);
            listings.add(SeedListing.ofResource(
                    ShopResource.ofItem(Identifier.withDefaultNamespace(produce)),
                    local ? LOCAL_CROP_PRICE : EXOTIC_CROP_PRICE, CROP_STOCK));
        }
        String localSeed = localSeedItem(level, plotAnchor);
        for (String seed : FARM_SEEDS) {
            boolean local = seed.equals(localSeed);
            listings.add(SeedListing.ofResource(
                    ShopResource.ofItem(Identifier.withDefaultNamespace(seed)),
                    local ? LOCAL_CROP_PRICE : EXOTIC_CROP_PRICE, SEED_STOCK));
        }
        return listings;
    }

    /** The real Seed item matching {@link #localCropItem}'s own biome pick, for pricing the matching Seed listing as local instead of exotic -- {@code null} for Potato/Carrot (no distinct Seeds item), which simply never matches any entry in {@link #FARM_SEEDS}. */
    private static String localSeedItem(ServerLevel level, BlockPos pos) {
        return switch (localCropItem(level, pos)) {
            case "wheat" -> "wheat_seeds";
            case "beetroot" -> "beetroot_seeds";
            case "melon_slice" -> "melon_seeds";
            default -> null;
        };
    }

    /** Mirrors {@code farm.FarmCropSpecies#chooseFor}'s own biome logic exactly, duplicated here per the same precedent {@link #localWoodSpecies} already set. */
    private static String localCropItem(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(Biomes.TAIGA) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)
                || biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.GROVE) || biome.is(Biomes.SNOWY_BEACH)) {
            return "potato";
        }
        if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return "beetroot";
        }
        if (biome.is(Biomes.JUNGLE) || biome.is(Biomes.SPARSE_JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE)) {
            return "melon_slice";
        }
        return "wheat";
    }

    // ---- Building Supplier: doors/stairs/glass panes. Not Tier-gated -- plain construction goods.

    private static final List<SeedListing> BUILDING_SUPPLIER_LISTINGS = List.of(
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("oak_door")), 4, 16),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("spruce_door")), 4, 16),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("iron_door")), 10, 8),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("oak_stairs")), 1, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("cobblestone_stairs")), 1, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("stone_brick_stairs")), 2, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("glass_pane")), 1, 64)
    );

    // ---- Stonemason: cut stone goods. Not Tier-gated.

    private static final List<SeedListing> STONEMASON_LISTINGS = List.of(
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("stone")), 2, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("smooth_stone")), 2, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("stone_bricks")), 2, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("chiseled_stone_bricks")), 3, 32),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("polished_deepslate")), 3, 64),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("bricks")), 3, 64)
    );

    // ---- Mineshaft: every item mineshaft.MineLootTable can actually produce, so a fresh plot has
    // real default listings from the start instead of requiring the owner to discover item ids by
    // hand. Not Tier-gated here (every item is listable regardless of Tier) -- the Tier only affects
    // which items the Miner actually finds via MineLootTable itself, not what the Shop can sell.
    //
    // stockCount is 0 for every entry here -- real correction, 2026-10-11, live report: a nonzero
    // stockCount here doesn't just inform the Shop UI, it's also reused verbatim as this plot's
    // Planned Inventory "happy state" target (ShopSeeding#autoPopulatePlannedInventory), which would
    // make a brand-new Mineshaft plot try to *buy* its own ore output from other plots overnight to
    // reach that target -- backwards for a plot whose only job is selling what its own Miner already
    // produces. 0 means "happy with none," so it never participates as a Planned Inventory buyer for
    // these, while still supplying a real price/listing suggestion in Manage Shop.
    private static final List<SeedListing> MINESHAFT_LISTINGS = List.of(
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("cobblestone")), 1, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("coal")), 2, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("raw_copper")), 3, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("raw_iron")), 4, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("raw_gold")), 6, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("redstone")), 3, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("lapis_lazuli")), 3, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("diamond")), 20, 0),
            SeedListing.ofResource(ShopResource.ofItem(Identifier.withDefaultNamespace("emerald")), 15, 0)
    );
}
