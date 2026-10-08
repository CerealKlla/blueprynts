package com.github.cerealklla.blueprynts;

import com.github.cerealklla.blueprynts.construction.ClientConstructionRequests;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxPickerScreen;
import com.github.cerealklla.blueprynts.construction.ConstructionSiteScreen;
import com.github.cerealklla.blueprynts.construction.GhostConstructionWallRenderer;
import com.github.cerealklla.blueprynts.construction.SlabRemoveScreen;
import com.github.cerealklla.blueprynts.construction.client.BluepryntsClientConfig;
import com.github.cerealklla.blueprynts.construction.client.ClientBlueprintPreviewCache;
import com.github.cerealklla.blueprynts.construction.client.ConstructionBoxScreen;
import com.github.cerealklla.blueprynts.registration.ModEntities;
import com.github.cerealklla.blueprynts.registration.ModMenus;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BluepryntsMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BluepryntsMod.MODID, value = Dist.CLIENT)
public class BluepryntsModClient {

    public BluepryntsModClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, BluepryntsClientConfig.SPEC);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHOST_CONSTRUCTION_WALL.get(), GhostConstructionWallRenderer::new);
        // Real client crash, 2026-09-29 ("EntityRenderDispatcher.shouldRender ... renderer is null"):
        // this entity type (added the same day for the Building Locator's minimap preview) never got
        // a renderer registered, so the moment one became visible to a client it crashed outright.
        // GhostConstructionWallRenderer is a generic Display.BlockDisplay renderer -- reused as-is.
        event.registerEntityRenderer(ModEntities.GHOST_BUILDING_PREVIEW.get(), GhostConstructionWallRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONSTRUCTION_BOX.get(), ConstructionBoxScreen::new);
    }

    // Polls the zero-server-refs bridge (see ClientConstructionRequests' own doc) for a pending
    // screen-open request every client tick -- same pattern as Settlemynts' SettlemyntsModClient,
    // except this always replaces the screen (never gated on "only if nothing's open") since every
    // server-side mutation deliberately resends this payload to refresh the already-open screen
    // with fresh authoritative state, not just to open it the first time.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientConstructionRequests.takePendingConstructionSiteScreen()
                .ifPresent(request -> Minecraft.getInstance().setScreen(new ConstructionSiteScreen(request)));
        ClientConstructionRequests.takePendingSlabRemoveScreen()
                .ifPresent(request -> Minecraft.getInstance().setScreen(new SlabRemoveScreen(request)));
        ClientConstructionRequests.takePendingConstructionBoxPickerScreen()
                .ifPresent(request -> Minecraft.getInstance().setScreen(new ConstructionBoxPickerScreen(request)));
        for (var image : ClientConstructionRequests.takePendingPreviewImages()) {
            ClientBlueprintPreviewCache.store(image.name(), image.variant(), image.mtime(), image.pngBytes());
        }
    }
}
