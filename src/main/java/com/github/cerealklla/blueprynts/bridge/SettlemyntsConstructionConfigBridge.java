package com.github.cerealklla.blueprynts.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

import com.github.cerealklla.blueprynts.blueprint.GenericResource;
import com.github.cerealklla.blueprynts.construction.FundingRequirements;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.construction.ConstructionRequirements;
import com.github.cerealklla.settlemynts.construction.ResourceCost;

import net.minecraft.resources.Identifier;

/**
 * Reads a Construction Box's own Zone Type + Tier funding requirements from Settlemynts (Settlemynts
 * owns this config table, design-document.md's "Planned: Passive Construction & Funding" section,
 * captured 2026-09-29) -- this mod stores no cost numbers of its own.
 *
 * <p>Deliberately converts Settlemynts' own {@link ConstructionRequirements}/{@link ResourceCost}
 * types into a plain {@code Map<GenericResource, Integer>} (a purely Blueprynts-native shape) right
 * here inside this bridge, rather than handing the Settlemynts record itself back to a caller --
 * {@code ConstructionBoxBlockEntity} is always-loaded (unlike this bridge class), so none of its own
 * persisted field types may ever reference a Settlemynts class, even behind a nullable/Optional --
 * same isolation reasoning as {@code PlotArea}/{@code BuildableArea} already being Blueprynts-native
 * types despite being computed Settlemynts-side.
 *
 * <p>This class must only ever be referenced (its {@link #getRequirements} called, or this class
 * loaded/classload-triggered at all) from behind a {@code ModList.get().isLoaded("settlemynts")}
 * check at the call site -- same isolation {@code SettlemyntsZoneBridge} already uses, so a
 * Settlemynts-less server never force-loads Settlemynts classes. {@code minTimeTicks} is now
 * surfaced too (added 2026-09-29, real construction-duration gating -- see {@code
 * FundingRequirements}'s own doc) -- still converted to a plain int here, never the Settlemynts
 * record itself.
 */
public final class SettlemyntsConstructionConfigBridge {

    private SettlemyntsConstructionConfigBridge() {
    }

    public static FundingRequirements getRequirements(Identifier zoneTypeId, int tier) {
        ConstructionRequirements requirements = Settlemynts.getConstructionRequirements(zoneTypeId, tier);
        Map<GenericResource, Integer> costs = new LinkedHashMap<>();
        for (ResourceCost cost : requirements.costs()) {
            costs.put(cost.resource(), cost.amount());
        }
        return new FundingRequirements(costs, requirements.minTimeTicks(), requirements.maxTimeTicks());
    }
}
