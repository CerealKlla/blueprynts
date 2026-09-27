package com.github.cerealklla.blueprynts.construction;

/**
 * A Construction Site's outer clearing area -- reuses Settlemynts' own Small/Large plot-size
 * numbers as hardcoded constants (design doc: "the plot's Small (15x15) or Large (50x50) square").
 * Deliberately not a live dependency on Settlemynts -- these are copy-pasted values, per the user.
 */
public enum SizeClass {
    SMALL(15, 1.0),
    LARGE(50, 2.5);

    private final int outerDimension;
    private final double slabBudgetMultiplier;

    SizeClass(int outerDimension, double slabBudgetMultiplier) {
        this.outerDimension = outerDimension;
        this.slabBudgetMultiplier = slabBudgetMultiplier;
    }

    /** The outer clearing's side length in blocks (a square: {@code outerDimension x outerDimension}). */
    public int outerDimension() {
        return outerDimension;
    }

    /** Multiplier applied on top of a Tier's base slab-budget area -- see {@link SlabBudget}. */
    public double slabBudgetMultiplier() {
        return slabBudgetMultiplier;
    }
}
