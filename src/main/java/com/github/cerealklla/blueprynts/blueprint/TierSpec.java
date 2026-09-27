package com.github.cerealklla.blueprynts.blueprint;

/**
 * Vertical extent granted to a Construction Site's build volume per Tier (T0-T5) -- height above
 * the leveled ground surface, and depth below it. Deliberately placeholder numbers: the user was
 * explicit these haven't been tuned by feel yet and expect to revise them after the first
 * playtest -- this table is the one place to change to retune.
 *
 * <p>The horizontal footprint is NOT part of this table -- it's whatever set of columns the player
 * marks with Footprint Slabs, up to the budget from {@link SlabBudget}. Tier only ever governs the
 * vertical extent, applied uniformly across every marked column.
 */
public enum TierSpec {
    T0(4, 0),
    T1(6, 0),
    T2(8, 2),
    T3(10, 4),
    T4(12, 6),
    T5(14, 8);

    private final int heightAboveGround;
    private final int depthBelowGround;

    TierSpec(int heightAboveGround, int depthBelowGround) {
        this.heightAboveGround = heightAboveGround;
        this.depthBelowGround = depthBelowGround;
    }

    /** Blocks of buildable space above the leveled ground surface (the surface layer itself is height 0, always included). */
    public int heightAboveGround() {
        return heightAboveGround;
    }

    /** Blocks of below-ground extent, starting one block below the surface -- the surface layer itself is never "below ground." */
    public int depthBelowGround() {
        return depthBelowGround;
    }

    public static TierSpec fromOrdinal(int tier) {
        TierSpec[] values = values();
        if (tier < 0 || tier >= values.length) {
            throw new IllegalArgumentException("Tier out of range: " + tier);
        }
        return values[tier];
    }
}
