package com.github.cerealklla.blueprynts;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;
import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.construction.ActiveSiteRegistry;
import com.github.cerealklla.blueprynts.construction.BeginConstructionPayload;
import com.github.cerealklla.blueprynts.construction.BeginDesignPayload;
import com.github.cerealklla.blueprynts.construction.BlueprintPreviewImagePayload;
import com.github.cerealklla.blueprynts.construction.Column;
import com.github.cerealklla.blueprynts.construction.ClientConstructionRequests;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxBlockEntity;
import com.github.cerealklla.blueprynts.construction.ConstructionProtectionListener;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteBlock;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteBlockEntity;
import com.github.cerealklla.blueprynts.construction.FootprintSlabBlock;
import com.github.cerealklla.blueprynts.construction.LoadBlueprintPayload;
import com.github.cerealklla.blueprynts.construction.OpenConstructionBoxPickerPayload;
import com.github.cerealklla.blueprynts.construction.OpenConstructionSiteScreenPayload;
import com.github.cerealklla.blueprynts.construction.OpenSlabRemoveScreenPayload;
import com.github.cerealklla.blueprynts.construction.RemoveSlabPayload;
import com.github.cerealklla.blueprynts.construction.RepositionBuildingPayload;
import com.github.cerealklla.blueprynts.construction.RepositionSupplyBoxPayload;
import com.github.cerealklla.blueprynts.construction.RequestBlueprintPreviewPayload;
import com.github.cerealklla.blueprynts.construction.SaveBlueprintPayload;
import com.github.cerealklla.blueprynts.construction.SelectConstructionBoxBlueprintPayload;
import com.github.cerealklla.blueprynts.construction.SetConstructionSiteOptionsPayload;
import com.github.cerealklla.blueprynts.construction.SizeClass;
import com.github.cerealklla.blueprynts.registration.ModBlockEntities;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;
import com.github.cerealklla.blueprynts.registration.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(BluepryntsMod.MODID)
public class BluepryntsMod {

    public static final String MODID = "blueprynts";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BluepryntsMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        com.github.cerealklla.blueprynts.registration.ModMenus.MENU_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(BluepryntsMod::registerPayloads);
        NeoForge.EVENT_BUS.register(new ConstructionProtectionListener());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.blueprynts.construction.FootprintSlabGuard());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.blueprynts.construction.BuildingLocatorTicker());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.blueprynts.construction.SupplyBoxLocatorTicker());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.blueprynts.construction.LocatorCancelListener());
        NeoForge.EVENT_BUS.addListener(BluepryntsMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                com.github.cerealklla.blueprynts.debug.DebugCommands.register(event.getDispatcher()));

        // Replaces the old unconditional-on-login debug item grant (removed 2026-10-02, see
        // decisions.md) -- the Construction Site item is now only ever handed out via Kyt's
        // mod-managed "Dev" Kyt (/kyt getDev). Same soft-dependency gate as the Settlemynts/
        // Yconomics dependencies above.
        if (ModList.get().isLoaded("kyt")) {
            com.github.cerealklla.kyt.api.Kyt.registerDevKytContribution(() ->
                    java.util.List.of(new ItemStack(ModBlocks.CONSTRUCTION_SITE_ITEM.get())));
        }
    }

    // Construction Site debug item grant removed 2026-10-02 (see decisions.md) -- now only ever
    // handed out via Kyt's mod-managed "Dev" Kyt (/kyt getDev). This method stays for the real
    // cleanup below, unrelated to that grant.
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        // A Footprint Slab is only ever meant to exist in its owning player's inventory while a
        // session is active -- if one survived a login (e.g. a server crash mid-session, before
        // FootprintSlabGuard's drop/container protections existed, or from a save predating them),
        // clear it out rather than leave a permanently-useless orphaned stack.
        var inventory = player.getInventory();
        int cleared = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (com.github.cerealklla.blueprynts.construction.FootprintSlabBlock.isFootprintSlab(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
                cleared++;
            }
        }
        if (cleared > 0) {
            LOGGER.info("Cleared {} stray Footprint Slab stack(s) from {}", cleared, player.getName().getString());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Blueprynts common setup");
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "farm"), "Farm", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "lumberyard"), "Lumberyard", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "blacksmith"), "Blacksmith", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "private_residence"), "Private Residence", 1.0));
        // Design doc Section 14a -- structure/Plot Type only this pass, no guards/garrison/patrolling
        // yet (those are real, separate, explicitly-deferred follow-up work, not silently dropped).
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "guardhouse"), "Guardhouse", 1.0, true));
        // Four new vendor-style Zone/Blueprint Types, 2026-10-05 user request -- plain BlueprintTypes,
        // same shape as every other built-in here (no special-cased logic anywhere, unlike Guardhouse's
        // npcOwnedOnly flag). The underlying shop/Yconomics-registration design (design doc Section 14a)
        // is still undecided -- these just give the Construction Box/Plot Type picker real entries to
        // build against.
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "grocer"), "Grocer", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "armorer"), "Armorer", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "restaurant"), "Restaurant", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "building_supplier"), "Building Supplier", 1.0));
        // Stonemason (2026-10-05, explicit user request, same shop-auto-seeding feature as the Shop
        // Seed Catalogs below) -- same plain shape as the four vendor types above.
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "stonemason"), "Stonemason", 1.0));

        // Guarded so a Settlemynts-less server never force-loads its classes -- see
        // bridge.SettlemyntsZoneBridge's own doc.
        if (ModList.get().isLoaded("settlemynts")) {
            com.github.cerealklla.blueprynts.bridge.SettlemyntsZoneBridge.registerBuiltins();
            // Shop listing auto-registration (see bridge.ShopSeedCatalogs' own doc) -- registers
            // this mod's own Zone Types' Shop listings (Lumberyard/Building Supplier/Stonemason), so
            // an owner doesn't have to manually add every item. Never auto-stocks any of them.
            com.github.cerealklla.blueprynts.bridge.ShopSeedCatalogs.registerAll();
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(OpenConstructionSiteScreenPayload.TYPE, OpenConstructionSiteScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientConstructionRequests.requestConstructionSiteScreen(payload));
        registrar.playToClient(OpenSlabRemoveScreenPayload.TYPE, OpenSlabRemoveScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientConstructionRequests.requestSlabRemoveScreen(payload));
        registrar.playToClient(BlueprintPreviewImagePayload.TYPE, BlueprintPreviewImagePayload.STREAM_CODEC,
                (payload, context) -> ClientConstructionRequests.requestStorePreviewImage(payload));
        registrar.playToClient(OpenConstructionBoxPickerPayload.TYPE, OpenConstructionBoxPickerPayload.STREAM_CODEC,
                (payload, context) -> ClientConstructionRequests.requestConstructionBoxPickerScreen(payload));

        registrar.playToServer(SelectConstructionBoxBlueprintPayload.TYPE, SelectConstructionBoxBlueprintPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                            || !(level.getBlockEntity(payload.boxPos()) instanceof ConstructionBoxBlockEntity box)) {
                        return;
                    }
                    if (box.blueprintPlaced() || box.placedBlueprintName() != null) {
                        return;
                    }
                    // Binds the box to this Blueprint and opens its funding screen -- it no longer
                    // pastes immediately (2026-09-29's "Passive Construction & Funding" pass). The
                    // structure only actually appears once every required resource is deposited (see
                    // ConstructionBoxBlockEntity#attemptCompletion), via RealBlueprintPlacement at
                    // 100% -- same underlying paste mechanism, just gated on funding now instead of
                    // firing the instant a Blueprint is picked. A box with no configured cost (no
                    // Settlemynts, or nothing set for this Zone Type + Tier) builds immediately anyway,
                    // since attemptCompletion() is called right away below.
                    var found = BlueprintStorage.get().load(payload.name());
                    if (found.isEmpty()) {
                        player.sendSystemMessage(Component.literal("No Blueprint named '" + payload.name() + "'."));
                        return;
                    }
                    int tier = found.get().tier();
                    com.github.cerealklla.blueprynts.construction.FundingRequirements requirements =
                            ModList.get().isLoaded("settlemynts") && box.zoneTypeId() != null
                                    ? com.github.cerealklla.blueprynts.bridge.SettlemyntsConstructionConfigBridge.getRequirements(box.zoneTypeId(), tier)
                                    : com.github.cerealklla.blueprynts.construction.FundingRequirements.NONE;
                    box.setPlacedBlueprintName(payload.name());
                    box.setTier(tier);
                    box.initializeRequirements(requirements);
                    box.attemptCompletion(level);
                    player.sendSystemMessage(Component.literal(box.blueprintPlaced()
                            ? "Blueprint \"" + payload.name() + "\" placed."
                            : "Blueprint \"" + payload.name() + "\" bound -- deposit the required resources to begin construction."));
                    player.openMenu(box);
                });

        registrar.playToServer(RepositionBuildingPayload.TYPE, RepositionBuildingPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                            || !(level.getBlockEntity(payload.boxPos()) instanceof ConstructionBoxBlockEntity box)) {
                        return;
                    }
                    if (box.placedBlueprintName() == null) {
                        return;
                    }
                    if (!box.blueprintPlaced() && box.everCompleted()) {
                        // Mid-Reposition already (a Locator is already out there somewhere) -- nothing
                        // new to do.
                        return;
                    }
                    boolean wasBuilt = box.everCompleted();
                    // Restores real captured terrain, not a blanket "set to air" -- a real bug,
                    // 2026-09-29 ("picking up the building tore the ground out with it instead of
                    // restoring it"): any cell the structure had pasted over real terrain (a
                    // below-ground basement wall, a floor tile over grass, etc.) used to just become
                    // air. See RealBlueprintPlacement's own doc for the full fix. Also handles the
                    // *partial*-construction case now (incremental, percentage-driven building means
                    // "Relocate Building" can be clicked mid-build) -- real playtest request, same
                    // date: "as soon as someone hits 'Relocate Building' it needs to clear the
                    // partially built ones." See ConstructionBoxBlockEntity#clearPartialConstruction's
                    // own doc.
                    // clearPartialConstruction may itself relocate this very box (see its own doc) --
                    // always continue with the instance it returns, never the original `box` variable.
                    box = box.clearPartialConstruction(level);
                    box.setBlueprintPlaced(false);
                    // Suspends attemptCompletion/the once-a-second ticker until the player actually
                    // places a new anchor with the Locator -- a real bug, 2026-09-29: without this,
                    // the ticker resumed building at the *old* anchor within about a second (funding/
                    // elapsed time hadn't changed), completing it again before the player ever clicked
                    // a new spot, and leaving the stray rebuild behind as orphaned blocks once they
                    // finally did. See ConstructionBoxBlockEntity#repositionPending's own doc.
                    box.setRepositionPending(true);
                    ItemStack locator = com.github.cerealklla.blueprynts.construction.BuildingLocatorItem.grantFor(box, level.getGameTime());
                    if (!player.getInventory().add(locator)) {
                        player.drop(locator, false);
                    }
                    player.sendSystemMessage(Component.literal(wasBuilt
                            ? "Building removed -- use the Building Locator to place it somewhere new."
                            : "Use the Building Locator to choose where the pending structure will build."));
                });

        registrar.playToServer(RepositionSupplyBoxPayload.TYPE, RepositionSupplyBoxPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                            || !(level.getBlockEntity(payload.boxPos()) instanceof ConstructionBoxBlockEntity box)) {
                        return;
                    }
                    Direction facing = level.getBlockState(payload.boxPos()).getValue(HorizontalDirectionalBlock.FACING);
                    java.util.UUID constructionId = box.constructionId();
                    // Pin the build target to wherever it currently is *before* stashing/relocating
                    // the box itself -- anchorPos()/anchorFacing() fall back to the box's own position
                    // when no explicit pendingAnchor has ever been set (the common case: nobody has
                    // used "Reposition Building" yet). Left un-pinned, that fallback keeps tracking the
                    // box's *new* position once relocated here, so the pending/mid-build structure
                    // silently followed the Supply Box instead of staying put -- these are two
                    // deliberately independent repositions (real bug report, 2026-09-30).
                    box.setPendingAnchor(box.anchorPos(), box.anchorFacing());
                    com.github.cerealklla.blueprynts.construction.PendingSupplyBoxRelocation.store(box, facing);
                    level.removeBlock(payload.boxPos(), false);
                    ItemStack locator = com.github.cerealklla.blueprynts.construction.SupplyBoxLocatorItem.grantFor(constructionId, level.getGameTime());
                    if (!player.getInventory().add(locator)) {
                        player.drop(locator, false);
                    }
                    player.sendSystemMessage(Component.literal("Building Supply Box removed -- use the Supply Box Locator to place it somewhere new."));
                });

        registrar.playToServer(RequestBlueprintPreviewPayload.TYPE, RequestBlueprintPreviewPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    var storage = com.github.cerealklla.blueprynts.blueprint.BlueprintStorage.get();
                    storage.readPreviewBytes(payload.name(), payload.variant()).ifPresent(bytes -> {
                        var availability = storage.previewAvailability(payload.name());
                        long mtime = "small".equals(payload.variant()) ? availability.smallMtime() : availability.fullMtime();
                        PacketDistributor.sendToPlayer(player, new BlueprintPreviewImagePayload(payload.name(), payload.variant(), mtime, bytes));
                    });
                });

        registrar.playToServer(SetConstructionSiteOptionsPayload.TYPE, SetConstructionSiteOptionsPayload.STREAM_CODEC,
                (payload, context) -> withSite(payload.sitePos(), context, (level, player, site) -> {
                    site.setPendingOptions(SizeClass.valueOf(payload.sizeClass()), payload.tier(),
                            payload.blueprintTypeId().isEmpty() ? null : Identifier.parse(payload.blueprintTypeId()));
                    ConstructionSiteBlock.sendScreen(player, payload.sitePos(), site);
                }));

        registrar.playToServer(BeginDesignPayload.TYPE, BeginDesignPayload.STREAM_CODEC,
                (payload, context) -> withSite(payload.sitePos(), context, (level, player, site) -> {
                    String error = site.beginDesign(level, player);
                    reportAndRefresh(player, payload.sitePos(), site, error);
                }));

        registrar.playToServer(BeginConstructionPayload.TYPE, BeginConstructionPayload.STREAM_CODEC,
                (payload, context) -> withSite(payload.sitePos(), context, (level, player, site) -> {
                    String error = site.beginConstruction(level, player);
                    reportAndRefresh(player, payload.sitePos(), site, error);
                }));

        registrar.playToServer(SaveBlueprintPayload.TYPE, SaveBlueprintPayload.STREAM_CODEC,
                (payload, context) -> withSite(payload.sitePos(), context, (level, player, site) -> {
                    String error = site.saveBlueprint(level, payload.name(), player);
                    reportAndRefresh(player, payload.sitePos(), site, error == null ? "Blueprint saved." : error);
                }));

        registrar.playToServer(LoadBlueprintPayload.TYPE, LoadBlueprintPayload.STREAM_CODEC,
                (payload, context) -> withSite(payload.sitePos(), context, (level, player, site) -> {
                    String error = site.loadBlueprint(level, payload.name(), player);
                    reportAndRefresh(player, payload.sitePos(), site, error);
                }));

        registrar.playToServer(RemoveSlabPayload.TYPE, RemoveSlabPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    ActiveSiteRegistry.activeSiteFor(player.getUUID()).ifPresent(active -> {
                        if (!active.dimension().equals(level.dimension())
                                || !(level.getBlockEntity(active.pos()) instanceof ConstructionSiteBlockEntity site)) {
                            return;
                        }
                        Column column = new Column(payload.slabPos().getX(), payload.slabPos().getZ());
                        if (!site.markedColumns().contains(column)) {
                            return;
                        }
                        site.unmarkColumn(column);
                        FootprintSlabBlock.removeAndReturn(level, payload.slabPos(), active.pos(), player);
                        ConstructionSiteBlock.sendScreen(player, active.pos(), site);
                    });
                });
    }

    private interface SiteAction {
        void run(ServerLevel level, ServerPlayer player, ConstructionSiteBlockEntity site);
    }

    private static void withSite(BlockPos sitePos, net.neoforged.neoforge.network.handling.IPayloadContext context, SiteAction action) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(sitePos) instanceof ConstructionSiteBlockEntity site)) {
            return;
        }
        action.run(level, player, site);
    }

    private static void reportAndRefresh(ServerPlayer player, BlockPos sitePos, ConstructionSiteBlockEntity site, String message) {
        if (message != null) {
            player.sendSystemMessage(Component.literal(message));
        }
        ConstructionSiteBlock.sendScreen(player, sitePos, site);
    }
}
