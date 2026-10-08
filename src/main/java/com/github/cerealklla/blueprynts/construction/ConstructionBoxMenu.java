package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.blueprint.GenericResource;

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
import net.minecraft.world.item.ItemStack;

/**
 * The Construction Box's own screen -- Supplied/Needed funding rows (one {@link
 * ConstructionBoxFundingSlot} per {@link GenericResource}, fixed count regardless of what a given
 * box actually needs -- an unneeded row's slot just always rejects everything, {@code required ==
 * 0}) plus the player's own inventory/hotbar. Required/Supplied numbers sync to the client through
 * {@link ContainerData} (the same built-in mechanism vanilla's furnace uses for burn time, not a
 * custom network payload) rather than the menu-open extraData, so they stay live while the screen
 * is open as deposits happen.
 */
public class ConstructionBoxMenu extends AbstractContainerMenu {

    private static final int RESOURCE_COUNT = GenericResource.values().length;
    private static final int BOX_X_INDEX = RESOURCE_COUNT * 2;
    private static final int BOX_Y_INDEX = RESOURCE_COUNT * 2 + 1;
    private static final int BOX_Z_INDEX = RESOURCE_COUNT * 2 + 2;
    private static final int BLUEPRINT_PLACED_INDEX = RESOURCE_COUNT * 2 + 3;
    private static final int EVER_COMPLETED_INDEX = RESOURCE_COUNT * 2 + 4;
    private static final int DATA_SIZE = RESOURCE_COUNT * 2 + 5;

    private final ConstructionBoxBlockEntity box; // null on the client's own reconstructed instance
    private final Container fundingContainer;
    private final ContainerData data;

    /** Server-side: a real box backs this menu. */
    public ConstructionBoxMenu(MenuType<?> type, int containerId, Inventory inventory, ConstructionBoxBlockEntity box) {
        super(type, containerId);
        this.box = box;
        this.fundingContainer = new SimpleContainer(RESOURCE_COUNT);
        this.data = new SyncedData();
        addDataSlots(data);
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real box to read from. */
    public ConstructionBoxMenu(MenuType<?> type, int containerId, Inventory inventory) {
        this(type, containerId, inventory, null);
    }

    // Must match ConstructionBoxScreen's own layout constants exactly (funding-slot y, and the
    // player-inventory y both mods' text/background positioning is built around).
    private static final int FUNDING_SLOT_Y = 18;
    private static final int PLAYER_INV_Y = 132;

    private void layoutSlots(Inventory inventory) {
        GenericResource[] resources = GenericResource.values();
        for (int i = 0; i < resources.length; i++) {
            addSlot(new ConstructionBoxFundingSlot(fundingContainer, i, 8 + i * 18, FUNDING_SLOT_Y, resources[i], this));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    /** Called by {@link ConstructionBoxFundingSlot#set} the instant a matching stack lands in a funding slot. */
    void onDeposit(GenericResource resource, int count) {
        if (box != null && box.getLevel() instanceof ServerLevel serverLevel) {
            box.deposit(serverLevel, resource, count);
        }
    }

    public int required(GenericResource resource) {
        return data.get(resource.ordinal() * 2);
    }

    public int supplied(GenericResource resource) {
        return data.get(resource.ordinal() * 2 + 1);
    }

    public int remaining(GenericResource resource) {
        return Math.max(0, required(resource) - supplied(resource));
    }

    /** {@code true} once every row's Supplied meets its own Needed -- the client-visible "fully funded" state, mirroring {@code ConstructionBoxBlockEntity#isFullyFunded}. */
    public boolean isFullyFunded() {
        for (GenericResource resource : GenericResource.values()) {
            if (supplied(resource) < required(resource)) {
                return false;
            }
        }
        return true;
    }

    /** Overall funding percentage across every required resource row, mirroring {@code ConstructionBoxBlockEntity#fundedPercent} exactly (client-visible construction progress, since 2026-09-29's percentage-driven pasting). */
    public int fundedPercent() {
        long totalRequired = 0;
        long totalSupplied = 0;
        for (GenericResource resource : GenericResource.values()) {
            totalRequired += required(resource);
            totalSupplied += supplied(resource);
        }
        if (totalRequired <= 0) {
            return 100;
        }
        return (int) Math.min(100, (100L * totalSupplied) / totalRequired);
    }

    // Real, live playtest bug (2026-09-29): everCompleted()/blueprintPlaced()/boxPos() used to read
    // straight off `box`, which is only ever non-null server-side -- the client's own reconstructed
    // menu (registration.ModMenus' factory) always passes box = null, so every button-active check
    // and button-click payload built from these on the client silently saw "false"/(0,0,0) no matter
    // the real state ("Relocate Building" looked permanently disabled; "Reposition Supply Box" sent
    // a payload for block (0,0,0), a no-op server-side). All three now flow through the same
    // ContainerData sync as the resource rows, so the client actually sees the real values.
    public boolean everCompleted() {
        return data.get(EVER_COMPLETED_INDEX) != 0;
    }

    public boolean blueprintPlaced() {
        return data.get(BLUEPRINT_PLACED_INDEX) != 0;
    }

    public BlockPos boxPos() {
        return new BlockPos(data.get(BOX_X_INDEX), data.get(BOX_Y_INDEX), data.get(BOX_Z_INDEX));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking a funding row does nothing special (it's a funnel, not real storage --
        // ConstructionBoxFundingSlot#set clears it back to empty the instant a stack lands, so a
        // funding-row index is never actually holding anything to move out). Shift-clicking from the
        // player's own inventory routes through the same GenericResource matching the direct-drag
        // path uses (real bug, 2026-09-30: this used to unconditionally no-op, so shift-click --
        // the instinctive way to "manually add" items to any vanilla container -- silently did
        // nothing with zero feedback).
        if (index >= RESOURCE_COUNT) {
            Slot sourceSlot = slots.get(index);
            if (sourceSlot.hasItem()) {
                ItemStack sourceStack = sourceSlot.getItem();
                for (GenericResource resource : GenericResource.values()) {
                    int remaining = remaining(resource);
                    if (remaining > 0 && resource.matches(sourceStack.getItem())) {
                        int deposit = Math.min(remaining, sourceStack.getCount());
                        onDeposit(resource, deposit);
                        sourceStack.shrink(deposit);
                        sourceSlot.set(sourceStack.isEmpty() ? ItemStack.EMPTY : sourceStack);
                        sourceSlot.setChanged();
                        break;
                    }
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
            if (index < RESOURCE_COUNT * 2) {
                GenericResource resource = GenericResource.values()[index / 2];
                return index % 2 == 0 ? box.requiredAmount(resource) : box.suppliedAmount(resource);
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
            return box.everCompleted() ? 1 : 0;
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
