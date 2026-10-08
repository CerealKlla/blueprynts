package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Shared by {@link BuildingLocatorItem}/{@link SupplyBoxLocatorItem}'s own {@code cancelIfHeld} --
 * a short window after a Locator is granted during which the walk-away auto-cancel is suspended.
 *
 * <p>Real bug, 2026-09-30: a player right-clicking a Building Supply Box to start a reposition is
 * very often standing just outside the plot's own strict interior at that exact moment (the box sits
 * at the plot's edge, facing in) -- without this grace window, the very first walk-away check (up to
 * 10 ticks later) could already see "outside the plot" and cancel the brand new item before the
 * player had any real chance to move. See {@code ModDataComponents#LOCATOR_GRANTED_AT_GAME_TIME}'s
 * own doc.
 */
final class LocatorGracePeriod {

    private static final long GRACE_TICKS = 60; // 3 seconds.

    private LocatorGracePeriod() {
    }

    static boolean hasElapsed(ServerLevel level, ItemStack stack) {
        Long grantedAt = stack.get(ModDataComponents.LOCATOR_GRANTED_AT_GAME_TIME.get());
        return grantedAt == null || level.getGameTime() - grantedAt >= GRACE_TICKS;
    }
}
