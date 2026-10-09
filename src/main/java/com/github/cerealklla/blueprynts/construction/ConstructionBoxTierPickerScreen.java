package com.github.cerealklla.blueprynts.construction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Pick a Tier" step (added 2026-10-09, explicit request: "I'd like to add a picker for the tier
 * and a separate picker for the blueprint itself to help filter") -- opened via {@link
 * OpenConstructionBoxTierPickerPayload}, ahead of {@link ConstructionBoxPickerScreen}. One button
 * per Tier from 1 through the box's own {@code allowedTier} cap; selecting one sends {@link
 * SelectConstructionBoxTierPayload}, which re-opens the Blueprint-name picker filtered to it.
 */
public class ConstructionBoxTierPickerScreen extends Screen {

    private final OpenConstructionBoxTierPickerPayload data;

    public ConstructionBoxTierPickerScreen(OpenConstructionBoxTierPickerPayload data) {
        super(Component.literal("Select Tier"));
        this.data = data;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = 70;
        for (int tier = 1; tier <= data.allowedTier(); tier++) {
            int finalTier = tier;
            addRenderableWidget(Button.builder(Component.literal("Tier " + tier), b -> selectTier(finalTier))
                    .bounds(centerX - 75, y, 150, 20).build());
            y += 24;
        }
        y += 6;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(centerX - 50, y, 100, 20).build());
    }

    private void selectTier(int tier) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(
                new SelectConstructionBoxTierPayload(data.boxPos(), tier)));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 20, 0xFFFFFFFF);
        if (data.allowedTier() <= 0) {
            String empty = "No Tier unlocked yet.";
            graphics.text(font, empty, width / 2 - font.width(empty) / 2, 50, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
