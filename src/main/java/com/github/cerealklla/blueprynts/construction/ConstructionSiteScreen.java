package com.github.cerealklla.blueprynts.construction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Construction Site's main UI -- Size/Tier/Blueprint Type toggles (Begin Design), Begin
 * Construction, and Save/Load (each a name field + confirm button, no scrolling list needed for a
 * v1 this small). Purely payload-driven, no container menu -- same convention as Settlemynts'
 * {@code FoundingScreen}. Rebuilt fresh every time the server resends {@code
 * OpenConstructionSiteScreenPayload}, so this screen never predicts state locally.
 */
public class ConstructionSiteScreen extends Screen {

    private static final String[] SIZE_CLASSES = {"SMALL", "LARGE"};

    private final OpenConstructionSiteScreenPayload data;
    private EditBox saveNameBox;

    public ConstructionSiteScreen(OpenConstructionSiteScreenPayload data) {
        super(Component.literal("Construction Site"));
        this.data = data;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = 40;
        boolean idle = data.phase().equals("IDLE");
        boolean designing = data.phase().equals("DESIGNING");
        boolean constructing = data.phase().equals("CONSTRUCTING");

        Button sizeButton = addRenderableWidget(Button.builder(Component.literal("Size: " + data.sizeClass()), b -> cycleSize())
                .bounds(x, y, 200, 20).build());
        sizeButton.active = idle;
        y += 24;
        Button tierButton = addRenderableWidget(Button.builder(Component.literal("Tier: T" + data.tier()), b -> cycleTier())
                .bounds(x, y, 200, 20).build());
        tierButton.active = idle;
        y += 24;
        String typeLabel = data.blueprintTypeId().isEmpty() ? "(pick a type)" : labelFor(data.blueprintTypeId());
        Button typeButton = addRenderableWidget(Button.builder(Component.literal("Type: " + typeLabel), b -> cycleType())
                .bounds(x, y, 200, 20).build());
        typeButton.active = idle && !data.availableBlueprintTypeIds().isEmpty();
        y += 24;

        Button beginDesign = addRenderableWidget(Button.builder(Component.literal("Begin Design"), b -> send(new BeginDesignPayload(data.sitePos())))
                .bounds(x, y, 200, 20).build());
        beginDesign.active = idle && !data.blueprintTypeId().isEmpty();
        y += 24;
        Button beginConstruction = addRenderableWidget(Button.builder(
                Component.literal("Begin Construction (" + data.markedColumnCount() + " marked)"),
                b -> send(new BeginConstructionPayload(data.sitePos())))
                .bounds(x, y, 200, 20).build());
        beginConstruction.active = designing;
        y += 28;

        saveNameBox = addRenderableWidget(new EditBox(font, x, y, 140, 20, Component.literal("Blueprint name")));
        Button saveButton = addRenderableWidget(Button.builder(Component.literal("Save"),
                b -> send(new SaveBlueprintPayload(data.sitePos(), saveNameBox.getValue())))
                .bounds(x + 144, y, 56, 20).build());
        saveButton.active = constructing;
        y += 24;

        addRenderableWidget(Button.builder(Component.literal("Load..."),
                b -> Minecraft.getInstance().setScreen(new BlueprintPickerScreen(data)))
                .bounds(x, y, 200, 20).build());
        y += 30;

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 50, y, 100, 20).build());
    }

    private String labelFor(String id) {
        int index = data.availableBlueprintTypeIds().indexOf(id);
        return index >= 0 ? data.availableBlueprintTypeLabels().get(index) : id;
    }

    private void cycleSize() {
        int index = (java.util.Arrays.asList(SIZE_CLASSES).indexOf(data.sizeClass()) + 1) % SIZE_CLASSES.length;
        send(new SetConstructionSiteOptionsPayload(data.sitePos(), SIZE_CLASSES[index], data.tier(), data.blueprintTypeId()));
    }

    private void cycleTier() {
        // Tier is 1-based (T1-T5, 2026-09-29) -- cycles 1->2->3->4->5->1.
        int next = (data.tier() % 5) + 1;
        send(new SetConstructionSiteOptionsPayload(data.sitePos(), data.sizeClass(), next, data.blueprintTypeId()));
    }

    private void cycleType() {
        if (data.availableBlueprintTypeIds().isEmpty()) {
            return;
        }
        int index = data.availableBlueprintTypeIds().indexOf(data.blueprintTypeId());
        String next = data.availableBlueprintTypeIds().get((index + 1) % data.availableBlueprintTypeIds().size());
        send(new SetConstructionSiteOptionsPayload(data.sitePos(), data.sizeClass(), data.tier(), next));
    }

    private void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 15, 0xFFFFFFFF);
        String phaseText = "Phase: " + data.phase();
        graphics.text(font, phaseText, width / 2 - font.width(phaseText) / 2, 27, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
