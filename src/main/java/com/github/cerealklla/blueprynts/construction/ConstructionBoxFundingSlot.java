package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.blueprint.GenericResource;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A funnel slot for one {@link GenericResource} row of a {@link ConstructionBoxMenu} -- never
 * visibly holds an inserted stack. The moment a matching stack lands in it, its full count is
 * tallied into the owning box's running total and the slot is cleared back to empty (design-
 * document.md's own "each row a single representative stack... not 400 real stacked slots"). {@link
 * #getMaxStackSize()} is capped at this resource's own remaining need, so vanilla's own slot-
 * insertion logic never moves more than that many items in on a single click -- no separate
 * "reject the excess" bookkeeping is needed here.
 */
public class ConstructionBoxFundingSlot extends Slot {

    private final GenericResource resource;
    private final ConstructionBoxMenu menu;

    public ConstructionBoxFundingSlot(Container container, int index, int x, int y, GenericResource resource, ConstructionBoxMenu menu) {
        super(container, index, x, y);
        this.resource = resource;
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return menu.remaining(resource) > 0 && resource.matches(stack.getItem());
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(1, menu.remaining(resource));
    }

    @Override
    public void set(ItemStack stack) {
        if (!stack.isEmpty() && resource.matches(stack.getItem())) {
            menu.onDeposit(resource, stack.getCount());
            super.set(ItemStack.EMPTY);
        } else {
            super.set(stack);
        }
    }
}
