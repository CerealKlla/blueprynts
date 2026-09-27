package com.github.cerealklla.blueprynts.blueprint;

import com.github.cerealklla.blueprynts.construction.SizeClass;

/**
 * Computes N, the number of Footprint Slabs a player is handed at Begin Design -- an area budget
 * (block count), not a fixed width x depth shape. The player is free to arrange up to N marked
 * columns into any shape.
 *
 * <p>Placeholder formula, flagged to the user as the piece of this whole feature most likely to
 * change immediately after the first playtest: a per-tier base area, scaled by {@link SizeClass}
 * and by {@link BlueprintType#areaScale()}.
 */
public final class SlabBudget {

    // Roughly doubling per tier -- a placeholder table, one line to retune.
    private static final int[] BASE_AREA_BY_TIER = {25, 50, 100, 175, 275, 400};

    private SlabBudget() {
    }

    public static int compute(SizeClass sizeClass, TierSpec tier, BlueprintType blueprintType) {
        int baseArea = BASE_AREA_BY_TIER[tier.ordinal()];
        double scaled = baseArea * sizeClass.slabBudgetMultiplier() * blueprintType.areaScale();
        return Math.max(1, (int) Math.round(scaled));
    }
}
