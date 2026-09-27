package com.github.cerealklla.blueprynts;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;
import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.construction.ActiveSiteRegistry;
import com.github.cerealklla.blueprynts.construction.BeginConstructionPayload;
import com.github.cerealklla.blueprynts.construction.BeginDesignPayload;
import com.github.cerealklla.blueprynts.construction.Column;
import com.github.cerealklla.blueprynts.construction.ClientConstructionRequests;
import com.github.cerealklla.blueprynts.construction.ConstructionProtectionListener;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteBlock;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteBlockEntity;
import com.github.cerealklla.blueprynts.construction.FootprintSlabBlock;
import com.github.cerealklla.blueprynts.construction.LoadBlueprintPayload;
import com.github.cerealklla.blueprynts.construction.OpenConstructionSiteScreenPayload;
import com.github.cerealklla.blueprynts.construction.OpenSlabRemoveScreenPayload;
import com.github.cerealklla.blueprynts.construction.RemoveSlabPayload;
import com.github.cerealklla.blueprynts.construction.SaveBlueprintPayload;
import com.github.cerealklla.blueprynts.construction.SetConstructionSiteOptionsPayload;
import com.github.cerealklla.blueprynts.construction.SizeClass;
import com.github.cerealklla.blueprynts.registration.ModBlockEntities;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;
import com.github.cerealklla.blueprynts.registration.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
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

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(BluepryntsMod::registerPayloads);
        NeoForge.EVENT_BUS.register(new ConstructionProtectionListener());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Blueprynts common setup");
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "farm"), "Farm", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "lumberyard"), "Lumberyard", 1.0));
        BlueprintTypeRegistry.register(new BlueprintType(Identifier.fromNamespaceAndPath(MODID, "blacksmith"), "Blacksmith", 1.0));

        // Guarded so a Settlemynts-less server never force-loads its classes -- see
        // bridge.SettlemyntsZoneBridge's own doc.
        if (ModList.get().isLoaded("settlemynts")) {
            com.github.cerealklla.blueprynts.bridge.SettlemyntsZoneBridge.registerBuiltins();
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(OpenConstructionSiteScreenPayload.TYPE, OpenConstructionSiteScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientConstructionRequests.requestConstructionSiteScreen(payload));
        registrar.playToClient(OpenSlabRemoveScreenPayload.TYPE, OpenSlabRemoveScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientConstructionRequests.requestSlabRemoveScreen(payload));

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
