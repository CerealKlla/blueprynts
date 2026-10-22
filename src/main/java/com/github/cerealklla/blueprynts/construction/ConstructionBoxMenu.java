package com.github.cerealklla.blueprynts.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Construction Box's own screen -- one funnel slot (any item the bound Blueprint still needs,
 * see {@link ConstructionBoxFundingSlot}'s own doc) plus an overall funding-percentage readout, plus
 * the player's own inventory/hotbar. Required/Supplied are now keyed by real {@link Item}s derived
 * from the Blueprint itself (2026-10-22, see {@code ConstructionBoxBlockEntity}'s own doc) rather
 * than a small fixed {@code GenericResource} enum -- there's no cheap way to sync an arbitrary-length
 * per-item breakdown through {@link ContainerData} (a fixed-size int array) the way the old two-row
 * Wood/Stone display did, so this screen deliberately shows only the aggregate {@link #fundedPercent()}
 * now, not a per-item list -- a known, flagged simplification, not an oversight (see decisions.md).
 * Everything else syncs the same way it always did: through {@link ContainerData}, not a custom
 * network payload, so values stay live while the screen is open.
 */
public class ConstructionBoxMenu extends AbstractContainerMenu {

    private static final int FUNDED_PERCENT_INDEX = 0;
    private static final int BOX_X_INDEX = 1;
    private static final int BOX_Y_INDEX = 2;
    private static final int BOX_Z_INDEX = 3;
    private static final int BLUEPRINT_PLACED_INDEX = 4;
    private static final int EVER_COMPLETED_INDEX = 5;
    private static final int CAN_MANAGE_INDEX = 6;
    private static final int LEGACY_FUNDING_DATA_LOST_INDEX = 7;
    private static final int DATA_SIZE = 8;

    private final ConstructionBoxBlockEntity box; // null on the client's own reconstructed instance
    private final Container fundingContainer;
    private final ContainerData data;
    // Computed once at open time (permission doesn't change mid-session) -- only meaningful
    // server-side; the client's own reconstructed menu always has box == null here and instead reads
    // the real value through SyncedData, same as boxPos()/everCompleted() below. See
    // bridge.SettlemyntsPermissionBridge's own doc for why Settlemynts-not-loaded defaults to true.
    private final boolean canManage;

    /** Server-side: a real box backs this menu. */
    public ConstructionBoxMenu(MenuType<?> type, int containerId, Inventory inventory, ConstructionBoxBlockEntity box) {
        super(type, containerId);
        this.box = box;
        this.fundingContainer = new SimpleContainer(1);
        boolean computedCanManage = false;
        if (box != null && box.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos checkPos = resolvePermissionCheckPos(box);
            computedCanManage = com.github.cerealklla.blueprynts.bridge.SettlemyntsPermissionBridge.canManage(
                    serverLevel, checkPos, inventory.player.getUUID());
        }
        this.canManage = computedCanManage;
        this.data = new SyncedData();
        addDataSlots(data);
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real box to read from. */
    public ConstructionBoxMenu(MenuType<?> type, int containerId, Inventory inventory) {
        this(type, containerId, inventory, null);
    }

    /**
     * The box's own {@code getBlockPos()} is deliberately placed just outside the plot's buildable
     * area (Settlemynts auto-places it "just outside the buildable area" at Finalize), so resolving
     * "which plot manages this box" from that position directly finds no plot at all, denying both
     * this screen's own Manage button AND every one of {@code BluepryntsMod}'s four server-side
     * permission re-checks (Tier Picker, Ledger, Reposition Building, Reposition Supply Box) to
     * everyone, including the plot's rightful Mayor/Owner -- a real live-reported bug (2026-10-12:
     * "I'm not seeing the construction site button" on a fully-funded box). {@link
     * com.github.cerealklla.blueprynts.api.PlotArea#anyInteriorPoint()} gives a position genuinely
     * inside the plot polygon instead, when the box has one recorded; falls back to the box's own
     * position only if it somehow has none (e.g. a box that predates `plotArea` being recorded at
     * all), same as this always worked before this fix. Public so every
     * {@code SettlemyntsPermissionBridge.canManage} call site can share one resolution, not just
     * this menu's own constructor.
     */
    public static BlockPos resolvePermissionCheckPos(ConstructionBoxBlockEntity box) {
        if (box.plotArea() != null) {
            var interior = box.plotArea().anyInteriorPoint();
            if (interior.isPresent()) {
                int[] xz = interior.get();
                return new BlockPos(xz[0], box.getBlockPos().getY(), xz[1]);
            }
        }
        return box.getBlockPos();
    }

    // Must match ConstructionBoxScreen's own layout constants exactly (funding-slot y, and the
    // player-inventory y both mods' text/background positioning is built around).
    private static final int FUNDING_SLOT_Y = 18;
    // Reworked 2026-10-12 -- this screen now shows a single "Manage" button (gated on canManage())
    // instead of four, which used to keep outgrowing this constant every time one was added (132 ->
    // 156 -> 180, each one landing short again) -- see ConstructionBoxScreen's own doc. Must match
    // that class's own copy of this constant exactly.
    private static final int PLAYER_INV_Y = 124;

    private void layoutSlots(Inventory inventory) {
        addSlot(new ConstructionBoxFundingSlot(fundingContainer, 0, 8, FUNDING_SLOT_Y, this));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    /** Called by {@link ConstructionBoxFundingSlot#set} the instant a matching stack lands in the funding slot. */
    void onDeposit(Item item, int count) {
        if (box != null && box.getLevel() instanceof ServerLevel serverLevel) {
            box.deposit(serverLevel, item, count);
        }
    }

    /** {@code max(0, required - supplied)} for {@code item} right now -- a generous default (never blocks a local click prediction) on the client's own reconstructed menu, which has no real box to ask. */
    public int remaining(Item item) {
        return box != null ? box.remaining(item) : Integer.MAX_VALUE;
    }

    public int fundedPercent() {
        return data.get(FUNDED_PERCENT_INDEX);
    }

    /** {@code true} once {@link #fundedPercent()} reaches 100 -- the client-visible "fully funded" state, mirroring {@code ConstructionBoxBlockEntity#isFullyFunded}. */
    public boolean isFullyFunded() {
        return fundedPercent() >= 100;
    }

    // Real, live playtest bug (2026-09-29): everCompleted()/blueprintPlaced()/boxPos() used to read
    // straight off `box`, which is only ever non-null server-side -- the client's own reconstructed
    // menu (registration.ModMenus' factory) always passes box = null, so every button-active check
    // and button-click payload built from these on the client silently saw "false"/(0,0,0) no matter
    // the real state ("Relocate Building" looked permanently disabled; "Reposition Supply Box" sent
    // a payload for block (0,0,0), a no-op server-side). All three now flow through the same
    // ContainerData sync as fundedPercent, so the client actually sees the real values.
    public boolean everCompleted() {
        return data.get(EVER_COMPLETED_INDEX) != 0;
    }

    public boolean blueprintPlaced() {
        return data.get(BLUEPRINT_PLACED_INDEX) != 0;
    }

    public BlockPos boxPos() {
        return new BlockPos(data.get(BOX_X_INDEX), data.get(BOX_Y_INDEX), data.get(BOX_Z_INDEX));
    }

    /** Mayor/Town Planner/Owner -- see {@code bridge.SettlemyntsPermissionBridge}'s own doc. Gates the "Manage" button on {@code client.ConstructionBoxScreen}. */
    public boolean canManage() {
        return data.get(CAN_MANAGE_INDEX) != 0;
    }

    /** {@code true} if this box's saved funding data couldn't be read at all -- see {@code ConstructionBoxBlockEntity#legacyFundingDataLost}'s own doc. Gates a distinct status message on {@code client.ConstructionBoxScreen} instead of a misleading funded percentage. */
    public boolean legacyFundingDataLost() {
        return data.get(LEGACY_FUNDING_DATA_LOST_INDEX) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking the funnel row does nothing special (it's a funnel, not real storage --
        // ConstructionBoxFundingSlot#set clears it back to empty the instant a stack lands, so that
        // index is never actually holding anything to move out). Shift-clicking from the player's
        // own inventory routes through the same item-matching the direct-drag path uses (real bug,
        // 2026-09-30: this used to unconditionally no-op, so shift-click -- the instinctive way to
        // "manually add" items to any vanilla container -- silently did nothing with zero feedback).
        if (index >= 1) {
            Slot sourceSlot = slots.get(index);
            if (sourceSlot.hasItem()) {
                ItemStack sourceStack = sourceSlot.getItem();
                int remaining = remaining(sourceStack.getItem());
                if (remaining > 0) {
                    int deposit = Math.min(remaining, sourceStack.getCount());
                    onDeposit(sourceStack.getItem(), deposit);
                    sourceStack.shrink(deposit);
                    sourceSlot.set(sourceStack.isEmpty() ? ItemStack.EMPTY : sourceStack);
                    sourceSlot.setChanged();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return box == null || !box.isRemoved();
    }

    /** Server: reads live values straight from the box. Client: stores whatever the server last synced. */
    private final class SyncedData implements ContainerData {
        private final int[] clientCache = new int[DATA_SIZE];

        @Override
        public int get(int index) {
            if (box == null) {
                return clientCache[index];
            }
            if (index == FUNDED_PERCENT_INDEX) {
                return box.fundedPercent();
            }
            if (index == BOX_X_INDEX) {
                return box.getBlockPos().getX();
            }
            if (index == BOX_Y_INDEX) {
                return box.getBlockPos().getY();
            }
            if (index == BOX_Z_INDEX) {
                return box.getBlockPos().getZ();
            }
            if (index == BLUEPRINT_PLACED_INDEX) {
                return box.blueprintPlaced() ? 1 : 0;
            }
            if (index == EVER_COMPLETED_INDEX) {
                return box.everCompleted() ? 1 : 0;
            }
            if (index == LEGACY_FUNDING_DATA_LOST_INDEX) {
                return box.legacyFundingDataLost() ? 1 : 0;
            }
            return canManage ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            clientCache[index] = value;
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }
}
