package com.github.cerealklla.blueprynts.blueprint;

import java.util.Map;

import com.github.cerealklla.blueprynts.BluepryntsMod;
import com.github.cerealklla.blueprynts.construction.SizeClass;

import net.minecraft.resources.Identifier;

/**
 * Computes N, the number of Footprint Slabs a player is handed at Begin Design -- an area budget
 * (block count), not a fixed width x depth shape. The player is free to arrange up to N marked
 * columns into any shape.
 *
 * <p>Placeholder formula, flagged to the user as the piece of this whole feature most likely to
 * change immediately after the first playtest: a per-tier base area, scaled by {@link SizeClass}
 * and by {@link BlueprintType#areaScale()} -- except for any type listed in {@link
 * #BASE_AREA_OVERRIDE_BY_TIER}, which replaces {@link #BASE_AREA_BY_TIER} outright for that type
 * (added 2026-10-05, explicit user request: Private Residence starts at 100 slabs at Tier 1 and
 * escalates to 140 at Tier 5, instead of sharing the flat per-tier table every other type still
 * uses). {@code sizeClass}/{@code areaScale} scaling still applies on top of an override the same
 * way it applies on top of the shared table -- only the per-tier base number changes.
 */
public final class SlabBudget {

    // Roughly doubling per tier -- a placeholder table, one line to retune. Indexed by
    // TierSpec#ordinal() (T1 = index 0), five entries matching the five-tier T1-T5 range
    // (old T5's base area, 400, was dropped alongside the enum constant, 2026-09-29).
    private static final int[] BASE_AREA_BY_TIER = {25, 50, 100, 175, 275};

    // Per-type overrides of the table above, keyed by BlueprintType#id(). Private Residence
    // (2026-10-05, explicit user request): 100 at Tier 1, escalating to 140 at Tier 5 -- a flatter
    // progression than the shared table's rough doubling, since a home's footprint shouldn't need
    // to grow nearly as fast as a production building's does.
    // Recallcinite Stone (2026-10-09, Recallcinite Totem feature): 9 at Tier 1 up to 100 at Tier 5,
    // per the user's own explicit endpoints -- the three middle values are a plain interpolation,
    // flagged as tunable like every other table here.
    private static final Map<Identifier, int[]> BASE_AREA_OVERRIDE_BY_TIER = Map.of(
            Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "private_residence"), new int[] {100, 110, 120, 130, 140},
            Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "recallcinite_stone"), new int[] {9, 20, 40, 65, 100});

    private SlabBudget() {
    }

    public static int compute(SizeClass sizeClass, TierSpec tier, BlueprintType blueprintType) {
        int[] table = BASE_AREA_OVERRIDE_BY_TIER.getOrDefault(blueprintType.id(), BASE_AREA_BY_TIER);
        int baseArea = table[tier.ordinal()];
        double scaled = baseArea * sizeClass.slabBudgetMultiplier() * blueprintType.areaScale();
        return Math.max(1, (int) Math.round(scaled));
    }
}
