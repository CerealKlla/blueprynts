package com.github.cerealklla.blueprynts.construction.client;

import com.github.cerealklla.blueprynts.construction.ConstructionBoxMenu;
import com.github.cerealklla.blueprynts.construction.RepositionBuildingPayload;
import com.github.cerealklla.blueprynts.construction.RepositionSupplyBoxPayload;
import com.github.cerealklla.blueprynts.construction.RequestConstructionBoxTierPickerPayload;
import com.github.cerealklla.blueprynts.construction.RequestLedgerPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Manage" sub-screen -- the four actions {@code client.ConstructionBoxScreen} used to carry
 * directly (Reposition Building/Reposition Supply Box/Change Blueprint/View Ledger), split out onto
 * their own plain screen 2026-10-12 per explicit user request, reached only through that screen's
 * own "Manage" button (itself gated to {@link ConstructionBoxMenu#canManage()}). See
 * {@code ConstructionBoxScreen}'s own doc for the full reasoning. A plain {@link Screen}, not a
 * container menu -- no slots/inventory involved here at all, same shape as {@code SlabRemoveScreen}.
 */
public class ConstructionBoxActionsScreen extends Screen {

    private final Screen parent;
    private final ConstructionBoxMenu menu;

    public ConstructionBoxActionsScreen(Screen parent, ConstructionBoxMenu menu) {
        super(Component.literal("Manage Construction Box"));
        this.parent = parent;
        this.menu = menu;
    }

    @Override
    protected void init() {
        int buttonWidth = 150;
        int buttonX = width / 2 - buttonWidth / 2;
        int y = height / 2 - 78;

        // Active whenever a Blueprint is bound at all -- this screen only ever opens for a bound
        // box, and "Reposition Building" now works both before and after funding completes (see
        // ConstructionBoxBlockEntity#setPendingAnchor's own doc).
        addRenderableWidget(Button.builder(Component.literal("Reposition Building"), b -> send(new RepositionBuildingPayload(menu.boxPos())))
                .bounds(buttonX, y, buttonWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Reposition Supply Box"), b -> send(new RepositionSupplyBoxPayload(menu.boxPos())))
                .bounds(buttonX, y, buttonWidth, 20).build());
        y += 24;
        // "Change Blueprint" -- the only way to pick/rebuild at a different, already-unlocked Tier
        // (or just a different Blueprint of the same Tier). Opens the Tier-picker step first, see
        // ConstructionBoxTierPickerScreen's own doc.
        addRenderableWidget(Button.builder(Component.literal("Change Blueprint"), b -> send(new RequestConstructionBoxTierPickerPayload(menu.boxPos())))
                .bounds(buttonX, y, buttonWidth, 20).build());
        y += 24;
        // "View Ledger" -- a read-only record of every purchase/sale touching this plot's Shop, see
        // LedgerEntry's own doc. Opens a real BookViewScreen built fresh from the box's own state,
        // never a real held item.
        addRenderableWidget(Button.builder(Component.literal("View Ledger"), b -> send(new RequestLedgerPayload(menu.boxPos())))
                .bounds(buttonX, y, buttonWidth, 20).build());
        y += 32;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(buttonX, y, buttonWidth, 20).build());
    }

    private void send(CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
        // Unlike "Back", every real action here leads somewhere else entirely (a Locator item to go
        // place, the Tier picker, the Ledger book) -- fully close rather than returning to the parent
        // Construction Box screen, matching these buttons' own original behavior before this split.
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 100, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
