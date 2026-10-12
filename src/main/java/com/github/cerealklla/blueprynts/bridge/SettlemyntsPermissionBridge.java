package com.github.cerealklla.blueprynts.bridge;

import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * "Is this player allowed to manage this plot" (Mayor/Town Planner/Owner), for the Construction
 * Box's own Manage button -- added 2026-10-12, closing a gap flagged since this screen was first
 * built (2026-09-29): "No permission gate on the Relocate buttons yet... no cross-mod API to check
 * that exists in Settlemynts today." Settlemynts gained exactly that API later ({@code api.Settlemynts
 * #canManagePlotAt}, 2026-10-09, for Lyfe's crafting-structure funding) -- this is a thin pass-through
 * to it. Same isolation convention as every other optional dependency in this suite: only ever
 * referenced from behind {@link #isLoaded()}.
 *
 * <p>With Settlemynts not loaded, there's no Mayor/Town Planner/Owner concept at all -- every call
 * site treats that as "nothing to gate against," i.e. visible/permitted to everyone, same as every
 * other optional-dependency feature in this suite degrading open rather than closed when its
 * dependency is absent.
 */
public final class SettlemyntsPermissionBridge {

    private SettlemyntsPermissionBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("settlemynts");
    }

    public static boolean canManage(ServerLevel level, BlockPos pos, UUID playerId) {
        return !isLoaded() || Settlemynts.canManagePlotAt(level, pos, playerId);
    }
}
