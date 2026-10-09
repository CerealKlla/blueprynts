package com.github.cerealklla.blueprynts.construction.client;

import com.github.cerealklla.blueprynts.blueprint.GenericResource;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxMenu;
import com.github.cerealklla.blueprynts.construction.RepositionBuildingPayload;
import com.github.cerealklla.blueprynts.construction.RepositionSupplyBoxPayload;
import com.github.cerealklla.blueprynts.construction.RequestConstructionBoxTierPickerPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Supplied/Needed funding rows plus Relocate Building/Relocate Construction Box buttons -- replaces
 * the old plain-{@code Screen}, payload-only {@code ConstructionBoxManageScreen} once a Blueprint is
 * bound (design-document.md's own "Construction Box inventory screen," captured 2026-09-29).
 *
 * <p><b>Layout, revised 2026-09-29</b> -- a real playtest report found the first version's text
 * overlapping (resource rows and the "fully funded" status were both drawn at the same y as
 * vanilla's own default title label, and the Relocate buttons sat above {@link #topPos}, which can
 * land off-screen depending on window size). Every custom text line and button now gets its own
 * fixed, non-overlapping row within a taller window instead: the funding slots stay at the
 * standard vanilla row-1 position ({@code y = 18}, matching the background art), each resource's
 * text sits on its own line below that, the "fully funded" status gets its own line below the
 * resource lines, both buttons sit below that (all still comfortably inside the window, above the
 * player inventory), and the player inventory panel is pushed down to make room for all of it.
 *
 * <p><b>No permission gate on the Relocate buttons yet</b>, matching the screen this replaces (which
 * had none either) -- the plan called for gating to Mayor/Town Planner/Plot Owner, but no
 * cross-mod API to check that exists in Settlemynts today, and adding one is real, separate scope
 * beyond this pass. Flagged, not silently narrowed (see decisions.md's same-day entry).
 */
public class ConstructionBoxScreen extends AbstractContainerScreen<ConstructionBoxMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Must match ConstructionBoxMenu#layoutSlots' own funding-slot y and player-inventory y exactly.
    private static final int TEXT_START_Y = 40;
    private static final int TEXT_LINE_HEIGHT = 10;
    // Bumped 132 -> 156 (2026-10-09) to make room for the new third "Change Blueprint" button below.
    private static final int PLAYER_INV_Y = 156;

    public ConstructionBoxScreen(ConstructionBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PLAYER_INV_Y + 76 + 6);
        this.inventoryLabelY = PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 150;
        int statusY = TEXT_START_Y + GenericResource.values().length * TEXT_LINE_HEIGHT + 4;
        int buttonX = leftPos + (imageWidth - buttonWidth) / 2;
        // Active whenever a Blueprint is bound at all -- this screen only ever opens for a bound
        // box, and "Reposition Building" now works both before and after funding completes (see
        // ConstructionBoxBlockEntity#setPendingAnchor's own doc).
        addRenderableWidget(Button.builder(Component.literal("Reposition Building"), b -> send(new RepositionBuildingPayload(menu.boxPos())))
                .bounds(buttonX, topPos + statusY + 14, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reposition Supply Box"), b -> send(new RepositionSupplyBoxPayload(menu.boxPos())))
                .bounds(buttonX, topPos + statusY + 38, buttonWidth, 20)
                .build());
        // "Change Blueprint" (added 2026-10-09) -- the only way to pick/rebuild at a different,
        // already-unlocked Tier (or just a different Blueprint of the same Tier). Opens the new
        // Tier-picker step first, see ConstructionBoxTierPickerScreen's own doc.
        addRenderableWidget(Button.builder(Component.literal("Change Blueprint"), b -> send(new RequestConstructionBoxTierPickerPayload(menu.boxPos())))
                .bounds(buttonX, topPos + statusY + 62, buttonWidth, 20)
                .build());
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        // Top slice: just the one real funding-slot row (rows=1 -> 18+17 tall), same formula the
        // Loot Bag screen uses for its own row count.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        // Player-inventory panel art, pushed down to PLAYER_INV_Y (not immediately following the
        // slice above) -- the gap between them is where the funding text/status/buttons render,
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
        int statusY = topPos + TEXT_START_Y + resources.length * TEXT_LINE_HEIGHT + 4;
        if (!menu.blueprintPlaced()) {
            String status = "Construction progress: " + menu.fundedPercent() + "%";
            int color = menu.fundedPercent() >= 100 ? 0xFF55FF55 : 0xFFAAAAAA;
            graphics.text(font, status, leftPos + (imageWidth - font.width(status)) / 2, statusY, color);
        }
    }
}
