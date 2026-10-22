package com.github.cerealklla.blueprynts.construction;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A single funnel slot for a {@link ConstructionBoxMenu} -- never visibly holds an inserted stack.
 * The moment a stack lands in it that's still part of the box's own requirements, its full count is
 * tallied into the owning box's running total and the slot is cleared back to empty.
 *
 * <p><b>Generalized 2026-10-22</b> (see {@code ConstructionBoxBlockEntity}'s own doc for the funding-
 * model rework this is part of) -- used to be one slot per fixed {@code GenericResource} (Wood/
 * Stone); now there's exactly one slot, period, accepting *any* item the bound Blueprint currently
 * still needs (resolved dynamically against {@link ConstructionBoxMenu#remaining}, not a fixed
 * identity baked into the slot itself) -- a Blueprint's own real material list has no small fixed
 * category count to give each one its own dedicated slot the way Wood/Stone did.
 */
public class ConstructionBoxFundingSlot extends Slot {

    private final ConstructionBoxMenu menu;

    public ConstructionBoxFundingSlot(Container container, int index, int x, int y, ConstructionBoxMenu menu) {
        super(container, index, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && menu.remaining(stack.getItem()) > 0;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.max(1, menu.remaining(stack.getItem()));
    }

    @Override
    public void set(ItemStack stack) {
        if (!stack.isEmpty() && menu.remaining(stack.getItem()) > 0) {
            menu.onDeposit(stack.getItem(), stack.getCount());
            super.set(ItemStack.EMPTY);
        } else {
            super.set(stack);
        }
    }
}
