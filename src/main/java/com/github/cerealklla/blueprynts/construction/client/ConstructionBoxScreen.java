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
 *
 * <p><b>Reworked again the same day</b>, live feedback after the per-item list above was restored:
 * "you're not using the top inventory at all, even though you were supposed to originally [remove it
 * for more room]... you do not need to center everything horizontally, make 2 columns... you can
 * make the resource panel scroll." The single real funnel slot this screen opened with (see git
 * history) is gone outright -- it was background-only decoration plus one functional slot, and
 * manual funding still works fine via shift-click ({@link ConstructionBoxMenu#quickMoveStack}),
 * which never depended on it. {@link #extractRequirements} is now a fixed-height, 2-column,
 * mouse-wheel-scrollable viewport rather than a single column capped at a "+N more" summary -- this
 * screen's total height no longer grows with the real required-item count at all.
 */
public class ConstructionBoxScreen extends AbstractContainerScreen<ConstructionBoxMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Must match ConstructionBoxMenu#layoutSlots' own copies of these constants exactly -- see that
    // class's own doc for the 2026-10-22 rework (funnel slot removed, requirements panel is now a
    // fixed-height 2-column scroll view instead of a single column that grew with the real item
    // count).
    private static final int STATUS_Y = 24;
    private static final int MANAGE_BUTTON_OFFSET = 14; // the Manage button's y, relative to STATUS_Y
    private static final int BUTTON_HEIGHT = 20;
    private static final int MANAGE_BUTTON_BOTTOM = STATUS_Y + MANAGE_BUTTON_OFFSET + BUTTON_HEIGHT;
    private static final int PANEL_MARGIN = 8;
    private static final int NEXT_NEEDED_Y = MANAGE_BUTTON_BOTTOM + PANEL_MARGIN;
    private static final int MISSING_ITEM_LINE_HEIGHT = 10;
    private static final int REQUIREMENTS_VIEWPORT_Y = NEXT_NEEDED_Y + MISSING_ITEM_LINE_HEIGHT + 4;
    private static final int REQUIREMENTS_VIEWPORT_HEIGHT = 50;
    // Real bug, found live 2026-10-22 ("These are not lined up at all"): this used to add an extra
    // "+14" specifically to compensate for extractBackground's own blit using "PLAYER_INV_Y - 14" as
    // its destination -- a self-cancelling fudge that only worked if ConstructionBoxMenu's own copy
    // of this constant was kept in perfect sync, which it wasn't (that one had drifted to a stale
    // hardcoded value -- see that class's own doc). Confirmed against Yconomics' own working
    // `bag.LootBagMenu`/`client.LootBagScreen` precedent that the correct rule has no fudge factor at
    // all: the background blit destination and the real slot Y must be the exact same number. Both
    // sides now compute this identically from the same real layout constants, and extractBackground's
    // own blit below uses this raw value directly, no "-14".
    private static final int PLAYER_INV_Y = REQUIREMENTS_VIEWPORT_Y + REQUIREMENTS_VIEWPORT_HEIGHT + PANEL_MARGIN;

    private boolean manageButtonAdded;
    private int requirementsScroll;

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
        // The top funnel-slot slice (18+17 tall) that used to render here is gone, 2026-10-22, along
        // with the slot itself -- see this class's own doc. The status text/Manage button/requirements
        // panel above the player-inventory block render directly over the plain dimmed screen
        // background now, same as they already did below the old funnel slice.
        //
        // Player-inventory panel art, pushed down to PLAYER_INV_Y (not immediately following the
        // slice above) -- the gap between them is where the status/button render, deliberately left
        // as plain screen background rather than more (non-functional) slot art.
        //
        // Real bug, found 2026-10-22 by measuring generic_54.png's actual pixels (not eyeballing it --
        // a real report that survived two earlier "fixes" turned out to need this): the source V here
        // used to be 126, derived by subtracting this blit's own height (96) from the texture's known
        // bottom-of-content boundary (222) -- but 222 - 96 lands 13px *above* the real top of the
        // player-inventory block. The texture's actual row layout, confirmed by sampling column x=8
        // down the whole image: 6 container rows end at y=125, then a 14px label/gap separator, then
        // the real 3-row-inventory+hotbar block starts at y=139 (matches vanilla's own generic
        // `inventoryTop = 18 + rows*18 + 13` formula for a full 6-row container: 18+108+13=139). Real
        // slot items render at the correct screen Y regardless (Slot x/y is unaffected by this), so
        // this only ever shifted the *background art* -- exactly the "item icons are vertically away
        // from the background cells, not a rounding error" symptom reported live: a fixed 13-logical-
        // pixel offset, which multiplies out to ~50 physical pixels at a 4x GUI Scale.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo + PLAYER_INV_Y, 0.0F, 139.0F, this.imageWidth, 96, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!menu.blueprintPlaced()) {
            int statusY = topPos + STATUS_Y;
            String status;
            int color;
            if (menu.legacyFundingDataLost()) {
                status = "Funding data unreadable -- use Change Blueprint to recompute";
                color = 0xFFFF5555;
            } else {
                status = "Construction progress: " + menu.fundedPercent() + "%";
                color = menu.isFullyFunded() ? 0xFF55FF55 : 0xFFAAAAAA;
            }
            graphics.text(font, status, leftPos + (imageWidth - font.width(status)) / 2, statusY, color);
            extractRequirements(graphics);
        }
    }

    /**
     * Real per-item "Cobblestone: 394/394, Stone Stair: 15/54" rows plus a "Next Needed: X" header,
     * 2026-10-22 -- explicit user design, replacing the first version of this (missing-amount-only)
     * the same day: "that'll help, and we could even show 'Next Needed: Stone Stair,' then it's
     * really obvious why it's stalled" (construction now paces by real build order, see {@code
     * ConstructionBoxBlockEntity#attemptCompletion}'s own doc -- a flat aggregate percentage no
     * longer tells a player *which* specific item is actually blocking progress).
     *
     * <p><b>Reworked the same day, second round</b> -- the first version was a single column capped
     * at {@code MAX_DISPLAYED_MISSING_ITEMS} with a "+N more" summary, which both wasted the screen's
     * full width and never actually showed a long blueprint's complete requirement list. "Next
     * Needed" stays pinned as a header immediately below the Manage button; the real requirement rows
     * below it are laid out two per line and clipped to a fixed-height viewport
     * ({@link #REQUIREMENTS_VIEWPORT_HEIGHT}), scrolled via {@link #mouseScrolled} -- so this panel's
     * height is fixed regardless of how many distinct item types a blueprint ends up needing.
     */
    private void extractRequirements(GuiGraphicsExtractor graphics) {
        var entries = ClientConstructionBoxRequirementsState.entries();
        var nextNeeded = ClientConstructionBoxRequirementsState.nextNeeded();
        if (entries.isEmpty()) {
            return;
        }
        int x = leftPos + 8;
        if (nextNeeded != null) {
            graphics.text(font, "Next Needed: " + itemName(nextNeeded), x, topPos + NEXT_NEEDED_Y, 0xFFFFAA55);
        }

        int viewportTop = topPos + REQUIREMENTS_VIEWPORT_Y;
        int viewportBottom = viewportTop + REQUIREMENTS_VIEWPORT_HEIGHT;
        int viewportLeft = leftPos;
        int viewportRight = leftPos + imageWidth;

        int rows = (entries.size() + 1) / 2;
        int contentHeight = rows * MISSING_ITEM_LINE_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - REQUIREMENTS_VIEWPORT_HEIGHT);
        requirementsScroll = Math.max(0, Math.min(maxScroll, requirementsScroll));

        int colWidth = (imageWidth - 24) / 2;
        int leftX = leftPos + 8;
        int rightX = leftX + colWidth + 8;

        graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        for (int i = 0; i < entries.size(); i++) {
            int row = i / 2;
            int rowY = viewportTop - requirementsScroll + row * MISSING_ITEM_LINE_HEIGHT;
            if (rowY + MISSING_ITEM_LINE_HEIGHT < viewportTop || rowY > viewportBottom) {
                continue;
            }
            var entry = entries.get(i);
            boolean satisfied = entry.supplied() >= entry.required();
            boolean blocking = entry.itemId().equals(nextNeeded);
            int color = blocking ? 0xFFFFAA55 : satisfied ? 0xFF55FF55 : 0xFFAAAAAA;
            String text = itemName(entry.itemId()) + ": " + entry.supplied() + "/" + entry.required();
            int colX = (i % 2 == 0) ? leftX : rightX;
            graphics.text(font, fitText(text, colWidth), colX, rowY, color);
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = viewportRight - 3;
            graphics.fill(trackX, viewportTop, trackX + 2, viewportBottom, 0x55FFFFFF);
            int thumbHeight = Math.max(6, REQUIREMENTS_VIEWPORT_HEIGHT * REQUIREMENTS_VIEWPORT_HEIGHT / contentHeight);
            int thumbY = viewportTop + (REQUIREMENTS_VIEWPORT_HEIGHT - thumbHeight) * requirementsScroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCCFFFFFF);
        }
    }

    /** Scrolls the requirements viewport when the cursor is over it; falls through to vanilla otherwise. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewportTop = topPos + REQUIREMENTS_VIEWPORT_Y;
        int viewportBottom = viewportTop + REQUIREMENTS_VIEWPORT_HEIGHT;
        if (mouseX >= leftPos && mouseX <= leftPos + imageWidth && mouseY >= viewportTop && mouseY <= viewportBottom) {
            var entries = ClientConstructionBoxRequirementsState.entries();
            int rows = (entries.size() + 1) / 2;
            int contentHeight = rows * MISSING_ITEM_LINE_HEIGHT;
            int maxScroll = Math.max(0, contentHeight - REQUIREMENTS_VIEWPORT_HEIGHT);
            int delta = (int) Math.signum(scrollY) * MISSING_ITEM_LINE_HEIGHT;
            requirementsScroll = Math.max(0, Math.min(maxScroll, requirementsScroll - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String fitText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }

    private static String itemName(Identifier itemId) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return item != null ? new net.minecraft.world.item.ItemStack(item).getHoverName().getString() : itemId.toString();
    }
}
