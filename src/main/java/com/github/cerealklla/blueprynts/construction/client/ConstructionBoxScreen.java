package com.github.cerealklla.blueprynts.construction.client;

import com.github.cerealklla.blueprynts.construction.ConstructionBoxLayout;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

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
 * which never depended on it. {@link #extractRequirements} is now a fixed-height, mouse-wheel-
 * scrollable viewport rather than a single column capped at a "+N more" summary -- this screen's
 * total height no longer grows with the real required-item count at all.
 *
 * <p><b>Reworked a third time, same day</b> -- the 2-column text rows above were replaced outright
 * with a {@code GRID_COLUMNS}-wide grid of real item icons ({@link GuiGraphicsExtractor#item}) plus
 * just the "supplied/required" numbers next to each one, per explicit request ("would it be easy
 * enough to just have the image of the item with the numbers next to it? that would save space").
 * An icon alone can't distinguish near-identical items (e.g. stone-brick variants), so hovering one
 * shows the real item's own vanilla tooltip, same precedent {@code kyt.loadout.client
 * .LoadoutEditorScreen}'s own item grid already established.
 */
public class ConstructionBoxScreen extends AbstractContainerScreen<ConstructionBoxMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Geometry now lives in one shared place, ConstructionBoxLayout -- see that class's own doc for
    // why (this exact "two copies of the same constants drift apart" bug hit this screen three times
    // in one day, 2026-10-22, before this fix).
    private static final int STATUS_Y = ConstructionBoxLayout.STATUS_Y;
    private static final int MANAGE_BUTTON_OFFSET = ConstructionBoxLayout.MANAGE_BUTTON_OFFSET;
    private static final int BUTTON_HEIGHT = ConstructionBoxLayout.BUTTON_HEIGHT;
    private static final int NEXT_NEEDED_Y = ConstructionBoxLayout.NEXT_NEEDED_Y;
    private static final int REQUIREMENTS_VIEWPORT_Y = ConstructionBoxLayout.REQUIREMENTS_VIEWPORT_Y;
    private static final int ICON_SIZE = ConstructionBoxLayout.ICON_SIZE;
    private static final int GRID_COLUMNS = ConstructionBoxLayout.GRID_COLUMNS;
    private static final int CELL_HEIGHT = ConstructionBoxLayout.CELL_HEIGHT;
    private static final int REQUIREMENTS_VIEWPORT_HEIGHT = ConstructionBoxLayout.REQUIREMENTS_VIEWPORT_HEIGHT;
    private static final int PLAYER_INV_Y = ConstructionBoxLayout.PLAYER_INV_Y;
    // The solid panel strip behind the vanilla "Inventory" label (inventoryLabelY, set in the
    // constructor below) -- real report, 2026-10-22: without it the label floats directly over the
    // dimmed in-game background with nothing behind it, same as the status/button/requirements area
    // above it deliberately does, but a label specifically needs to stay readable the way every other
    // screen's own "Inventory" label already is. Matches the real texture's own 14px label/separator
    // strip (texture y=125-139, confirmed by the same pixel-measurement that found the v=139 slot-row
    // start -- see extractBackground's own doc).
    private static final int LABEL_STRIP_HEIGHT = 14;

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
        //
        // Extended upward by LABEL_STRIP_HEIGHT, same day, separate real report ("it needs a
        // background behind the 'Inventory' label"): this blit used to start exactly at the real slot
        // rows (v=139, screen y=PLAYER_INV_Y), skipping the solid 14px label/separator strip (texture
        // v=125-139) that vanilla's own chest screens always render the "Inventory" label over -- so
        // this screen's own label floated over the plain dimmed background with nothing behind it,
        // unlike every other screen's. Starting the source at v=125 instead (height +14 to match) and
        // shifting the destination up by the same 14px pulls that strip in without moving the real
        // slot rows at all: v=139 (now 14px into this wider blit) still lands at screen y=PLAYER_INV_Y
        // exactly, same as before.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo + PLAYER_INV_Y - LABEL_STRIP_HEIGHT,
                0.0F, 125.0F, this.imageWidth, 96 + LABEL_STRIP_HEIGHT, 256, 256);
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
            extractRequirements(graphics, mouseX, mouseY);
        }
    }

    /**
     * A "Next Needed: X" header, pinned immediately below the Manage button, plus a {@link
     * #GRID_COLUMNS}-wide grid of real item icons with their "supplied/required" numbers next to
     * each one -- 2026-10-22, replacing an earlier all-text version the same day (see this class's
     * own "reworked a third time" doc for the full history: a single column capped at "+N more",
     * then 2 columns of full "Name: supplied/required" text, then this). Clipped to a fixed-height
     * viewport ({@link #REQUIREMENTS_VIEWPORT_HEIGHT}) and scrolled via {@link #mouseScrolled} --
     * this panel's height is fixed regardless of how many distinct item types a blueprint needs.
     */
    private void extractRequirements(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var entries = ClientConstructionBoxRequirementsState.entries();
        var nextNeeded = ClientConstructionBoxRequirementsState.nextNeeded();
        if (entries.isEmpty()) {
            return;
        }
        if (nextNeeded != null) {
            graphics.text(font, "Next Needed: " + itemName(nextNeeded), leftPos + 8, topPos + NEXT_NEEDED_Y, 0xFFFFAA55);
        }

        int viewportTop = topPos + REQUIREMENTS_VIEWPORT_Y;
        int viewportBottom = viewportTop + REQUIREMENTS_VIEWPORT_HEIGHT;
        int viewportLeft = leftPos;
        int viewportRight = leftPos + imageWidth;

        int rows = (entries.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        int contentHeight = rows * CELL_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - REQUIREMENTS_VIEWPORT_HEIGHT);
        requirementsScroll = Math.max(0, Math.min(maxScroll, requirementsScroll));

        int colWidth = (imageWidth - 16) / GRID_COLUMNS;

        graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        for (int i = 0; i < entries.size(); i++) {
            int cellX = leftPos + 8 + (i % GRID_COLUMNS) * colWidth;
            int cellY = viewportTop - requirementsScroll + (i / GRID_COLUMNS) * CELL_HEIGHT;
            if (cellY + ICON_SIZE < viewportTop || cellY > viewportBottom) {
                continue;
            }
            var entry = entries.get(i);
            ItemStack stack = itemStack(entry.itemId());
            graphics.item(stack, cellX, cellY);
            boolean satisfied = entry.supplied() >= entry.required();
            boolean blocking = entry.itemId().equals(nextNeeded);
            int color = blocking ? 0xFFFFAA55 : satisfied ? 0xFF55FF55 : 0xFFAAAAAA;
            graphics.text(font, entry.supplied() + "/" + entry.required(), cellX + ICON_SIZE + 2, cellY + 4, color);
            if (mouseX >= cellX && mouseX < cellX + ICON_SIZE && mouseY >= cellY && mouseY < cellY + ICON_SIZE
                    && mouseY >= viewportTop && mouseY < viewportBottom) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
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
            int rows = (entries.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
            int contentHeight = rows * CELL_HEIGHT;
            int maxScroll = Math.max(0, contentHeight - REQUIREMENTS_VIEWPORT_HEIGHT);
            int delta = (int) Math.signum(scrollY) * CELL_HEIGHT;
            requirementsScroll = Math.max(0, Math.min(maxScroll, requirementsScroll - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static ItemStack itemStack(Identifier itemId) {
        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private static String itemName(Identifier itemId) {
        ItemStack stack = itemStack(itemId);
        return stack.isEmpty() ? itemId.toString() : stack.getHoverName().getString();
    }
}
