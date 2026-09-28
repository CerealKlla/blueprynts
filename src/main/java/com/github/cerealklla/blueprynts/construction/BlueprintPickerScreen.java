package com.github.cerealklla.blueprynts.construction;

import java.util.Optional;

import com.github.cerealklla.blueprynts.construction.client.BluepryntsClientConfig;
import com.github.cerealklla.blueprynts.construction.client.BluepryntsClientConfig.PreviewQuality;
import com.github.cerealklla.blueprynts.construction.client.ClientBlueprintPreviewCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Replaces the old free-text "type the exact Blueprint name" Load field with an actual browsable
 * picklist -- the original complaint that motivated the whole preview-image feature. Pages through
 * saved Blueprints one at a time (Prev/Next) rather than a scrollable list: no custom list widget
 * exists anywhere in this codebase yet, every other screen here already uses this same cycle-button
 * style (see {@code ConstructionSiteScreen}'s Size/Tier/Type buttons), and it naturally bounds image
 * fetching to at most one in-flight request at a time.
 *
 * <p>Opened entirely client-side from {@code ConstructionSiteScreen}'s Load button -- the data it
 * needs ({@code savedBlueprintNames} and both preview-mtime lists) already arrived as part of the
 * normal {@code OpenConstructionSiteScreenPayload} snapshot, so no extra server round trip is needed
 * just to open this screen.
 */
public class BlueprintPickerScreen extends Screen {

    /** Must match BluepryntImager's own RenderConfig defaults -- see that project's context docs. Not shared code, kept in sync by hand. */
    private static final int FULL_IMAGE_WIDTH = 1024;
    private static final int FULL_IMAGE_HEIGHT = 768;
    private static final int SMALL_IMAGE_WIDTH = 256;
    private static final int SMALL_IMAGE_HEIGHT = 192;

    private static final int PREVIEW_BOX_WIDTH = 200;
    private static final int PREVIEW_BOX_HEIGHT = 150;

    private final OpenConstructionSiteScreenPayload data;
    private int index;
    private String lastRequestedKey;
    private Button qualityButton;
    private Button loadButton;

    public BlueprintPickerScreen(OpenConstructionSiteScreenPayload data) {
        super(Component.literal("Load Blueprint"));
        this.data = data;
    }

    @Override
    protected void init() {
        boolean hasEntries = !data.savedBlueprintNames().isEmpty();
        int centerX = width / 2;
        int previewTop = 60;

        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1))
                .bounds(centerX - PREVIEW_BOX_WIDTH / 2 - 24, previewTop + PREVIEW_BOX_HEIGHT / 2 - 10, 20, 20).build())
                .active = hasEntries;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1))
                .bounds(centerX + PREVIEW_BOX_WIDTH / 2 + 4, previewTop + PREVIEW_BOX_HEIGHT / 2 - 10, 20, 20).build())
                .active = hasEntries;

        int belowPreview = previewTop + PREVIEW_BOX_HEIGHT + 12;
        qualityButton = addRenderableWidget(Button.builder(qualityLabel(), b -> cycleQuality())
                .bounds(centerX - 100, belowPreview, 200, 20).build());
        loadButton = addRenderableWidget(Button.builder(Component.literal("Load This"), b -> loadCurrent())
                .bounds(centerX - 100, belowPreview + 24, 200, 20).build());
        loadButton.active = hasEntries;

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> Minecraft.getInstance().setScreen(new ConstructionSiteScreen(data)))
                .bounds(centerX - 50, belowPreview + 52, 100, 20).build());
    }

    private void page(int delta) {
        int count = data.savedBlueprintNames().size();
        if (count == 0) {
            return;
        }
        index = Math.floorMod(index + delta, count);
        lastRequestedKey = null;
    }

    private void cycleQuality() {
        BluepryntsClientConfig.cyclePreviewQuality();
        lastRequestedKey = null;
        qualityButton.setMessage(qualityLabel());
    }

    private Component qualityLabel() {
        return Component.literal("Preview Quality: " + BluepryntsClientConfig.previewQuality().name());
    }

    private void loadCurrent() {
        if (data.savedBlueprintNames().isEmpty()) {
            return;
        }
        send(new LoadBlueprintPayload(data.sitePos(), data.savedBlueprintNames().get(index)));
    }

    private void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 15, 0xFFFFFF);

        int centerX = width / 2;
        int previewTop = 60;
        int boxX = centerX - PREVIEW_BOX_WIDTH / 2;

        if (data.savedBlueprintNames().isEmpty()) {
            String empty = "No saved Blueprints yet.";
            graphics.text(font, empty, centerX - font.width(empty) / 2, previewTop + PREVIEW_BOX_HEIGHT / 2, 0xAAAAAA);
            return;
        }

        String name = data.savedBlueprintNames().get(index);
        String position = "(" + (index + 1) + " / " + data.savedBlueprintNames().size() + ")";
        graphics.text(font, name, centerX - font.width(name) / 2, previewTop - 22, 0xFFFFFF);
        graphics.text(font, position, centerX - font.width(position) / 2, previewTop - 10, 0xAAAAAA);

        graphics.fill(boxX, previewTop, boxX + PREVIEW_BOX_WIDTH, previewTop + PREVIEW_BOX_HEIGHT, 0xFF202020);
        renderPreview(graphics, boxX, previewTop);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int boxX, int boxY) {
        PreviewQuality quality = BluepryntsClientConfig.previewQuality();
        if (quality == PreviewQuality.NONE) {
            return;
        }

        String name = data.savedBlueprintNames().get(index);
        boolean full = quality == PreviewQuality.FULL;
        String variant = full ? "full" : "small";
        long requiredMtime = full
                ? data.savedBlueprintFullPreviewMtimes().get(index)
                : data.savedBlueprintSmallPreviewMtimes().get(index);
        if (requiredMtime < 0) {
            // No preview generated for this Blueprint yet (BluepryntImager hasn't caught up, or it predates that feature).
            return;
        }

        Optional<Identifier> texture = ClientBlueprintPreviewCache.get(name, variant, requiredMtime);
        if (texture.isEmpty()) {
            String key = name + "|" + variant;
            if (!key.equals(lastRequestedKey)) {
                lastRequestedKey = key;
                send(new RequestBlueprintPreviewPayload(data.sitePos(), name, variant));
            }
            return;
        }

        int imageWidth = full ? FULL_IMAGE_WIDTH : SMALL_IMAGE_WIDTH;
        int imageHeight = full ? FULL_IMAGE_HEIGHT : SMALL_IMAGE_HEIGHT;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.get(), boxX, boxY, 0f, 0f,
                PREVIEW_BOX_WIDTH, PREVIEW_BOX_HEIGHT, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
