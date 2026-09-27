package com.github.cerealklla.blueprynts.construction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** The one-button "Remove" context menu for a specific placed Footprint Slab -- see {@code FootprintSlabBlock}. */
public class SlabRemoveScreen extends Screen {

    private final OpenSlabRemoveScreenPayload data;

    public SlabRemoveScreen(OpenSlabRemoveScreenPayload data) {
        super(Component.literal("Footprint Slab"));
        this.data = data;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
            Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new RemoveSlabPayload(data.slabPos())));
            onClose();
        }).bounds(width / 2 - 60, height / 2 - 10, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 35, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
