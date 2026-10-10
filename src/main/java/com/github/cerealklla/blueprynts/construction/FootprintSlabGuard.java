package com.github.cerealklla.blueprynts.construction;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Keeps a Footprint Slab from ever coming to rest anywhere but its owning player's own inventory --
 * dropped (Q / drag-out-of-screen) or left sitting in some other container's slots, it snaps back.
 * The budget-tracking the whole mechanic depends on (granted at Begin Design, reclaimed on Begin
 * Construction/auto-clear/login) only works if a slab can't be stashed somewhere those sweeps never
 * look.
 *
 * <p>There's no generic "item entered this container" event to hook for the container case, so this
 * instead corrects on {@link PlayerContainerEvent.Close} -- the item may sit in a chest slot while
 * the screen is open, but the moment the player closes it, anything still there gets pulled back out.
 */
public final class FootprintSlabGuard {

    // Real live bug, 2026-10-10: a genuinely full inventory makes vanilla's own
    // Inventory#placeItemBackInInventory fall back to Player#drop as ITS last resort, which fires a
    // brand new ItemTossEvent for the same slab -- caught by this same onToss, canceled, retried,
    // and falling back the same way again, forever. Confirmed live on Production: a server session's
    // entire log was ~580,000 lines of nothing but this one StackOverflowError repeating roughly once
    // a second, which is almost certainly what made RCON stop responding and forced a kill. This flag
    // breaks the loop -- while a fallback placement is already in progress, a re-entrant toss of the
    // same item is let through un-canceled instead of retried, so the rare "truly no room anywhere"
    // case just drops the slab on the ground (recoverable) rather than hanging the server (not).
    private boolean handlingFallback = false;

    @SubscribeEvent
    public void onToss(ItemTossEvent event) {
        if (handlingFallback || !FootprintSlabBlock.isFootprintSlab(event.getEntity().getItem())) {
            return;
        }
        ItemStack stack = event.getEntity().getItem().copy();
        event.setCanceled(true);
        // Canceling only stops it from entering the world -- the stack was already removed from the
        // player's inventory by the time this fires (see ItemTossEvent's own doc), so it has to be
        // added back explicitly, not just left alone.
        Player player = event.getPlayer();
        if (!player.getInventory().add(stack)) {
            handlingFallback = true;
            try {
                player.getInventory().placeItemBackInInventory(stack);
            } finally {
                handlingFallback = false;
            }
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        AbstractContainerMenu menu = event.getContainer();
        for (Slot slot : menu.slots) {
            // Only foreign containers -- the player's own inventory slots are backed by their own
            // Inventory container and are exactly where a slab is supposed to be able to sit.
            if (slot.container == player.getInventory() || !FootprintSlabBlock.isFootprintSlab(slot.getItem())) {
                continue;
            }
            ItemStack stack = slot.remove(slot.getItem().getCount());
            if (!player.getInventory().add(stack)) {
                handlingFallback = true;
                try {
                    player.getInventory().placeItemBackInInventory(stack);
                } finally {
                    handlingFallback = false;
                }
            }
        }
    }
}
