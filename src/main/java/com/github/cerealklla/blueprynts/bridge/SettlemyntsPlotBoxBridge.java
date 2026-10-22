package com.github.cerealklla.blueprynts.bridge;

import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * The two Settlemynts-facing operations {@code construction.ConstructionBoxBlockEntity}'s own
 * event-driven funding needs (added 2026-10-22, see that class's own doc for the funding-model
 * rework this is part of) -- withdrawing already-available materials straight out of the owning
 * plot's own boxes for free, and pushing whatever's still missing to that plot's Planned Inventory
 * wishlist so the existing nightly inter-plot-trading pass buys it from the settlement economy. Same
 * self-guarding-per-method isolation convention as {@code SettlemyntsPermissionBridge} -- every
 * method here checks {@link #isLoaded()} itself, so a Settlemynts-less server simply never gets any
 * automatic funding (a box still funds fully from manual player deposits, same as always).
 */
public final class SettlemyntsPlotBoxBridge {

    private SettlemyntsPlotBoxBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("settlemynts");
    }

    /** Withdraws up to {@code maxAmount} of {@code itemId} from {@code plotId}'s own boxes -- returns the amount actually removed, 0 if Settlemynts isn't loaded or none was available. */
    public static int withdrawFromPlot(ServerLevel level, UUID plotId, Identifier itemId, int maxAmount) {
        return isLoaded() ? Settlemynts.withdrawItemFromPlot(level, plotId, itemId, maxAmount) : 0;
    }

    /** Wholesale-replaces {@code plotId}'s derived-from-Construction-Box Planned Inventory targets with {@code missing} -- a no-op if Settlemynts isn't loaded. */
    public static void syncMissingMaterials(ServerLevel level, UUID settlementCoreId, UUID plotId, Map<Identifier, Integer> missing) {
        if (isLoaded()) {
            Settlemynts.setConstructionMaterialTargets(level, settlementCoreId, plotId, missing);
        }
    }
}
