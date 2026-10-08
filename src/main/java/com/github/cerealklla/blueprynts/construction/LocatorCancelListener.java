package com.github.cerealklla.blueprynts.construction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Dying or logging out while holding a bound Building/Supply Box Locator auto-cancels the pending
 * relocation, same as walking outside the plot (see {@code BuildingLocatorItem}/{@code
 * SupplyBoxLocatorItem}'s own {@code cancelIfHeld}, called here with {@code force = true} since
 * there's no "still inside the plot" bounds check that makes sense for either trigger). Runs
 * *before* vanilla's own death-drop handling ({@link LivingDeathEvent} fires pre-drop), so a
 * canceled Supply Box Locator's stack is cleared from the inventory slot and never also spawns as a
 * separate dropped item in the world -- design doc's own intent ("dying with the relocation item in
 * inventory, or logging out and back in, also cancels it and removes the item"), never actually
 * built until this pass (real playtest report, 2026-09-30).
 */
public final class LocatorCancelListener {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel level) {
            cancelBoth(level, player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel level) {
            cancelBoth(level, player);
        }
    }

    private void cancelBoth(ServerLevel level, Player player) {
        cancelHand(level, player, InteractionHand.MAIN_HAND);
        cancelHand(level, player, InteractionHand.OFF_HAND);
    }

    private void cancelHand(ServerLevel level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BuildingLocatorItem.cancelIfHeld(level, player, stack, true, s -> player.setItemInHand(hand, ItemStack.EMPTY));
        SupplyBoxLocatorItem.cancelIfHeld(level, player, stack, true, s -> player.setItemInHand(hand, ItemStack.EMPTY));
    }
}
