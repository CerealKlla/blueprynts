package com.github.cerealklla.blueprynts.construction.client;

import com.github.cerealklla.blueprynts.construction.ConstructionBoxMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * A funding progress readout plus a single "Manage" button -- replaces the old plain-{@code
 * Screen}, payload-only {@code ConstructionBoxManageScreen} once a Blueprint is bound
 * (design-document.md's own "Construction Box inventory screen," captured 2026-09-29).
 *
 * <p><b>Rebuilt 2026-10-22</b> alongside {@link ConstructionBoxMenu}'s own funding-model rework
 * (real exact Items, not a small fixed {@code GenericResource} enum) -- the old per-resource
 * "Logs: 12/64" text rows are gone (there's no cheap way to sync an arbitrary-length per-item list
 * through {@code ContainerData}, and funding is now mostly automatic -- plot stock, then the
 * settlement economy at midnight -- rather than something a player needs a row-by-row readout to
 * manually chase), replaced by one aggregate "Construction progress: X%" line, same text this
 * screen already showed below the old rows.
 */
public class ConstructionBoxScreen extends AbstractContainerScreen<ConstructionBoxMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Must match ConstructionBoxMenu#layoutSlots' own funding-slot y and player-inventory y exactly.
    private static final int STATUS_Y = 40;
    private static final int MANAGE_BUTTON_OFFSET = 14; // the Manage button's y, relative to STATUS_Y
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_MARGIN = 12; // real clearance between the button's bottom and the inventory panel
    private static final int MANAGE_BUTTON_BOTTOM = STATUS_Y + MANAGE_BUTTON_OFFSET + BUTTON_HEIGHT;
    // +14 compensates for extractBackground's own "yo + PLAYER_INV_Y - 14" panel-position offset.
    // MUST match ConstructionBoxMenu.PLAYER_INV_Y exactly -- that constant drives real slot/item
    // positions, this one only drives where the background art/label are drawn; letting them diverge
    // is exactly the "floating icons" bug described in this class's own earlier doc.
    private static final int PLAYER_INV_Y = MANAGE_BUTTON_BOTTOM + PANEL_MARGIN + 14;

    private boolean manageButtonAdded;

    public ConstructionBoxScreen(ConstructionBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PLAYER_INV_Y + 76 + 6);
        this.inventoryLabelY = PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        tryAddManageButton();
    }

    /**
     * Real bug found 2026-10-12, live: {@link #init()} used to check {@link ConstructionBoxMenu
     * #canManage()} directly and only ever ran once, at screen-open time -- but {@code canManage}
     * reaches the client through the same {@code ContainerData} sync every other field here uses
     * (`boxPos`/`everCompleted`/`blueprintPlaced`), which arrives in a *separate* packet from the one
     * that triggers this screen's own construction. Every other field reads that sync continuously in
     * {@code extractRenderState}, so an occasional late arrival self-heals invisibly within a frame;
     * this one made a one-time, permanent decision ("add the button, or don't") off whatever value
     * happened to be in the client-side cache at that exact instant -- almost always still the default
     * {@code 0} (not-yet-synced), which is exactly why the "Manage" button was reported missing even
     * once the server-side permission computation itself was confirmed correct. Fixed by checking on
     * every client tick instead of once -- {@link #containerTick()} already runs every tick while this
     * screen is open, same mechanism vanilla's own furnace screen uses for its live burn-progress
     * arrow, so this is the standing shape for "a value this screen needs for its initial layout, but
     * won't have on the very first frame" going forward, not a one-off patch. {@code canManage} itself
     * never changes mid-session (same permission for the life of one menu open), so this only ever
     * adds the button once, the first tick it becomes available, and never removes it again.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        tryAddManageButton();
    }

    private void tryAddManageButton() {
        if (manageButtonAdded || !menu.canManage()) {
            return;
        }
        manageButtonAdded = true;
        int buttonWidth = 150;
        int buttonX = leftPos + (imageWidth - buttonWidth) / 2;
        addRenderableWidget(Button.builder(Component.literal("Manage"),
                b -> Minecraft.getInstance().setScreen(new ConstructionBoxActionsScreen(this, menu)))
                .bounds(buttonX, topPos + STATUS_Y + MANAGE_BUTTON_OFFSET, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        // Top slice: just the one real funding funnel slot (rows=1 -> 18+17 tall), same formula the
        // Loot Bag screen uses for its own row count.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        // Player-inventory panel art, pushed down to PLAYER_INV_Y (not immediately following the
        // slice above) -- the gap between them is where the status/button render, deliberately left
        // as plain screen background rather than more (non-functional) slot art.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo + PLAYER_INV_Y - 14, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!menu.blueprintPlaced()) {
            int statusY = topPos + STATUS_Y;
            String status = "Construction progress: " + menu.fundedPercent() + "%";
            int color = menu.isFullyFunded() ? 0xFF55FF55 : 0xFFAAAAAA;
            graphics.text(font, status, leftPos + (imageWidth - font.width(status)) / 2, statusY, color);
        }
    }
}
