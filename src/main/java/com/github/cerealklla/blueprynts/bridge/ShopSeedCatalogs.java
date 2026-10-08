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

    private static List<SeedListing> farmListings(ServerLevel level, BlockPos plotAnchor) {
        String localProduce = localCropItem(level, plotAnchor);
        List<SeedListing> listings = new java.util.ArrayList<>();
        for (String produce : FARM_PRODUCE) {
            boolean local = produce.equals(localProduce);
            listings.add(SeedListing.ofResource(
                    ShopResource.ofItem(Identifier.withDefaultNamespace(produce)),
                    local ? LOCAL_CROP_PRICE : EXOTIC_CROP_PRICE, CROP_STOCK));
        }
        return listings;
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
}
