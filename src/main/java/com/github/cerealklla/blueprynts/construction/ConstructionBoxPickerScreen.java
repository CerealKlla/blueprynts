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
 * The Building Supply Box's Blueprint-selection step (design-document.md Section 11 points 4-6) --
 * opened via {@link OpenConstructionBoxPickerPayload}. Same paged single-item browsing style as
 * {@link BlueprintPickerScreen} (Prev/Next, no scrollable list widget exists in this codebase).
 * Selecting a Blueprint pastes it directly into the world -- see {@code BluepryntsMod}'s handler for
 * {@link SelectConstructionBoxBlueprintPayload}.
 *
 * <p>Gained preview images the same day (user request, 2026-09-29: "add the preview image into the
 * Blueprint Selection on the Construction Box") -- reuses {@link BlueprintPickerScreen}'s exact
 * fetch/cache/render machinery ({@link ClientBlueprintPreviewCache}, {@link
 * RequestBlueprintPreviewPayload}, the shared {@link BluepryntsClientConfig} Preview Quality
 * setting) rather than duplicating it; the only difference is which payload supplies the
 * name/mtime lists ({@link OpenConstructionBoxPickerPayload} here instead of {@code
 * OpenConstructionSiteScreenPayload}).
 */
public class ConstructionBoxPickerScreen extends Screen {

    private static final int FULL_IMAGE_WIDTH = 1024;
    private static final int FULL_IMAGE_HEIGHT = 768;
    private static final int SMALL_IMAGE_WIDTH = 256;
    private static final int SMALL_IMAGE_HEIGHT = 192;

    private static final int PREVIEW_BOX_WIDTH = 200;
    private static final int PREVIEW_BOX_HEIGHT = 150;
    private static final int MIN_PREVIEW_BOX_HEIGHT = 60;
    private static final int TOP_MARGIN = 60;
    private static final int BOTTOM_MARGIN = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    // 4 buttons (quality/select/reposition/cancel), each BUTTON_HEIGHT tall with BUTTON_GAP between.
    private static final int BUTTONS_BLOCK_HEIGHT = BUTTON_HEIGHT * 4 + BUTTON_GAP * 3;

    private final OpenConstructionBoxPickerPayload data;
    private int index;
    private String lastRequestedKey;
    private Button qualityButton;
    private Button selectButton;
    // Computed in init() -- see its own doc for why this shrinks instead of staying a fixed constant.
    private int previewBoxHeight = PREVIEW_BOX_HEIGHT;

    public ConstructionBoxPickerScreen(OpenConstructionBoxPickerPayload data) {
        super(Component.literal("Select Blueprint"));
        this.data = data;
    }

    /**
     * Laid out bottom-up as of 2026-10-09 (real report, screenshotted at two different window sizes:
     * "Reposition Supply Box" and "Cancel" were missing entirely off the bottom of the screen at a
     * smaller resolution/GUI scale) -- the old version used fixed pixel offsets that assumed enough
     * window height for a full-size preview box AND all 4 buttons below it, with no fallback. The
     * 4-button block is now always anchored {@link #BOTTOM_MARGIN} above the bottom of the screen
     * (guaranteeing every button is always visible), and the preview box shrinks (down to {@link
     * #MIN_PREVIEW_BOX_HEIGHT}) to fill whatever space is actually left above it.
     */
    @Override
    protected void init() {
        boolean hasEntries = !data.names().isEmpty();
        int centerX = width / 2;
        int previewTop = TOP_MARGIN;

        int buttonsTop = Math.max(previewTop + MIN_PREVIEW_BOX_HEIGHT + 12, height - BOTTOM_MARGIN - BUTTONS_BLOCK_HEIGHT);
        previewBoxHeight = Math.max(MIN_PREVIEW_BOX_HEIGHT, Math.min(PREVIEW_BOX_HEIGHT, buttonsTop - previewTop - 12));

        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1))
                .bounds(centerX - PREVIEW_BOX_WIDTH / 2 - 24, previewTop + previewBoxHeight / 2 - 10, 20, 20).build())
                .active = hasEntries;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1))
                .bounds(centerX + PREVIEW_BOX_WIDTH / 2 + 4, previewTop + previewBoxHeight / 2 - 10, 20, 20).build())
                .active = hasEntries;

        qualityButton = addRenderableWidget(Button.builder(qualityLabel(), b -> cycleQuality())
                .bounds(centerX - 100, buttonsTop, 200, BUTTON_HEIGHT).build());
        selectButton = addRenderableWidget(Button.builder(Component.literal("Select This Blueprint"), b -> select())
                .bounds(centerX - 100, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP), 200, BUTTON_HEIGHT).build());
        selectButton.active = hasEntries;

        // "Same ability to reposition the Building Supply Box too" -- user request, 2026-09-29.
        // Reachable here too (not just from ConstructionBoxManageScreen) since a box that hasn't had
        // a Blueprint picked yet still opens this screen directly on right-click.
        addRenderableWidget(Button.builder(Component.literal("Reposition Supply Box"), b -> repositionBox())
                .bounds(centerX - 100, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP) * 2, 200, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(centerX - 50, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP) * 3, 100, BUTTON_HEIGHT).build());
    }

    private void repositionBox() {
        send(new RepositionSupplyBoxPayload(data.boxPos()));
        onClose();
    }

    private void page(int delta) {
        int count = data.names().size();
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

    private void select() {
        if (data.names().isEmpty()) {
            return;
        }
        send(new SelectConstructionBoxBlueprintPayload(data.boxPos(), data.names().get(index)));
        onClose();
    }

    private void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 15, 0xFFFFFFFF);

        int centerX = width / 2;
        int previewTop = TOP_MARGIN;
        int boxX = centerX - PREVIEW_BOX_WIDTH / 2;

        if (data.names().isEmpty()) {
            String empty = "No saved Blueprints yet.";
            graphics.text(font, empty, centerX - font.width(empty) / 2, previewTop + previewBoxHeight / 2, 0xFFAAAAAA);
            return;
        }

        String name = data.names().get(index);
        String position = "(" + (index + 1) + " / " + data.names().size() + ")";
        graphics.text(font, name, centerX - font.width(name) / 2, previewTop - 22, 0xFFFFFFFF);
        graphics.text(font, position, centerX - font.width(position) / 2, previewTop - 10, 0xFFAAAAAA);

        graphics.fill(boxX, previewTop, boxX + PREVIEW_BOX_WIDTH, previewTop + previewBoxHeight, 0xFF202020);
        renderPreview(graphics, boxX, previewTop);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int boxX, int boxY) {
        PreviewQuality quality = BluepryntsClientConfig.previewQuality();
        if (quality == PreviewQuality.NONE) {
            return;
        }

        String name = data.names().get(index);
        boolean full = quality == PreviewQuality.FULL;
        String variant = full ? "full" : "small";
        long requiredMtime = full ? data.fullPreviewMtimes().get(index) : data.smallPreviewMtimes().get(index);
        if (requiredMtime < 0) {
            // No preview generated for this Blueprint yet (BluepryntImager hasn't caught up, or it predates that feature).
            return;
        }

        Optional<Identifier> texture = ClientBlueprintPreviewCache.get(name, variant, requiredMtime);
        if (texture.isEmpty()) {
            String key = name + "|" + variant;
            if (!key.equals(lastRequestedKey)) {
                lastRequestedKey = key;
                send(new RequestBlueprintPreviewPayload(data.boxPos(), name, variant));
            }
            return;
        }

        int imageWidth = full ? FULL_IMAGE_WIDTH : SMALL_IMAGE_WIDTH;
        int imageHeight = full ? FULL_IMAGE_HEIGHT : SMALL_IMAGE_HEIGHT;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.get(), boxX, boxY, 0f, 0f,
                PREVIEW_BOX_WIDTH, previewBoxHeight, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
