package com.github.cerealklla.blueprynts.construction.client;

import com.github.cerealklla.blueprynts.blueprint.GenericResource;
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
 * Supplied/Needed funding rows plus a single "Manage" button -- replaces the old plain-{@code
 * Screen}, payload-only {@code ConstructionBoxManageScreen} once a Blueprint is bound
 * (design-document.md's own "Construction Box inventory screen," captured 2026-09-29).
 *
 * <p><b>Reworked 2026-10-12</b> -- this screen used to carry four separate buttons (Reposition
 * Building/Reposition Supply Box/Change Blueprint/View Ledger) directly, and every time one was
 * added, {@link #PLAYER_INV_Y} needed hand-incrementing to make room -- it kept landing short
 * (132 -> 156 -> 180, each one still visibly overlapping live) because a fix would patch only this
 * class's own copy of that constant and forget {@link ConstructionBoxMenu}'s matching one (which
 * actually controls slot/item positions), leaving item icons rendered at the old position while the
 * background art moved -- a second real bug on top of the first (real report: "the item icons are no
 * longer tied to their background so they're just floating"). Per explicit user request, those four
 * buttons are now on their own separate screen ({@link ConstructionBoxActionsScreen}), reached
 * through a single "Manage" button here -- gated to {@link ConstructionBoxMenu#canManage()}
 * (Mayor/Town Planner/Owner only, closing a permission gap flagged since this screen was first
 * built), with the four underlying payload handlers now also re-checking permission server-side
 * (see {@code bridge.SettlemyntsPermissionBridge}) rather than trusting this button's own visibility
 * as the only gate. {@link #PLAYER_INV_Y} must still match {@link ConstructionBoxMenu}'s own copy
 * exactly -- both are small, single-button-sized constants now, much less likely to drift.
 */
public class ConstructionBoxScreen extends AbstractContainerScreen<ConstructionBoxMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Must match ConstructionBoxMenu#layoutSlots' own funding-slot y and player-inventory y exactly.
    private static final int TEXT_START_Y = 40;
    private static final int TEXT_LINE_HEIGHT = 10;
    private static final int MANAGE_BUTTON_OFFSET = 14; // the Manage button's y, relative to statusY
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_MARGIN = 12; // real clearance between the button's bottom and the inventory panel
    private static final int STATUS_Y = TEXT_START_Y + GenericResource.values().length * TEXT_LINE_HEIGHT + 4;
    private static final int MANAGE_BUTTON_BOTTOM = STATUS_Y + MANAGE_BUTTON_OFFSET + BUTTON_HEIGHT;
    // +14 compensates for extractBackground's own "yo + PLAYER_INV_Y - 14" panel-position offset.
    // MUST match ConstructionBoxMenu.PLAYER_INV_Y exactly -- that constant drives real slot/item
    // positions, this one only drives where the background art/label are drawn; letting them diverge
    // is exactly the "floating icons" bug described in this class's own doc above.
    private static final int PLAYER_INV_Y = MANAGE_BUTTON_BOTTOM + PANEL_MARGIN + 14;

    public ConstructionBoxScreen(ConstructionBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PLAYER_INV_Y + 76 + 6);
        this.inventoryLabelY = PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        if (!menu.canManage()) {
            return;
        }
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
        // Top slice: just the one real funding-slot row (rows=1 -> 18+17 tall), same formula the
        // Loot Bag screen uses for its own row count.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        // Player-inventory panel art, pushed down to PLAYER_INV_Y (not immediately following the
        // slice above) -- the gap between them is where the funding text/status/button render,
        // deliberately left as plain screen background rather than more (non-functional) slot art.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo + PLAYER_INV_Y - 14, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        GenericResource[] resources = GenericResource.values();
        int textX = leftPos + 8;
        for (int i = 0; i < resources.length; i++) {
            GenericResource resource = resources[i];
            int required = menu.required(resource);
            if (required <= 0) {
                continue;
            }
            String text = resource.label() + ": " + menu.supplied(resource) + " / " + required;
            int lineY = topPos + TEXT_START_Y + i * TEXT_LINE_HEIGHT;
            graphics.text(font, text, textX, lineY, menu.remaining(resource) <= 0 ? 0xFF55FF55 : 0xFFFFFFFF);
        }
        int statusY = topPos + STATUS_Y;
        if (!menu.blueprintPlaced()) {
            String status = "Construction progress: " + menu.fundedPercent() + "%";
            int color = menu.fundedPercent() >= 100 ? 0xFF55FF55 : 0xFFAAAAAA;
            graphics.text(font, status, leftPos + (imageWidth - font.width(status)) / 2, statusY, color);
        }
    }
}
