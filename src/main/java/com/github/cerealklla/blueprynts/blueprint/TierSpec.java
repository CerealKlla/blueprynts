package com.github.cerealklla.blueprynts.blueprint;

import java.util.Map;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.resources.Identifier;

/**
 * Vertical extent granted to a Construction Site's build volume per Tier (T1-T5) -- height above
 * the leveled ground surface, and depth below it. Deliberately placeholder numbers: the user was
 * explicit these haven't been tuned by feel yet and expect to revise them after the first
 * playtest -- this table is the one place to change to retune.
 *
 * <p>Renumbered 2026-09-29 from a six-value T0-T5 range to a five-value T1-T5 range ("less
 * confusing than 0-4") -- the old T0 was dropped and every other constant kept its own name and
 * numbers unchanged (old T1-T4 -> new T1-T4; old T5, the top tier, was dropped entirely per the
 * user's own "nothing actually needs 6 right now"). Every Tier number used elsewhere in this mod
 * (a Construction Site's pending tier, a saved {@code BlueprintRecord}'s own tier field, the
 * on-disk "Tier N" folder name) is now this same 1-based number -- there is no separate internal
 * 0-based index anymore.
 *
 * <p>The horizontal footprint is NOT part of this table -- it's whatever set of columns the player
 * marks with Footprint Slabs, up to the budget from {@link SlabBudget}. Tier only ever governs the
 * vertical extent, applied uniformly across every marked column.
 */
public enum TierSpec {
    T1(4, 0),
    T2(6, 0),
    T3(8, 2),
    T4(10, 4),
    T5(12, 6);

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

    // Per-type overrides of (depthBelowGround, heightAboveGround), keyed by BlueprintType#id() --
    // mirrors SlabBudget's own BASE_AREA_OVERRIDE_BY_TIER pattern. Recallcinite Stone (Recallcinite
    // Totem feature): a narrow 9-tile footprint at Tier 1 but a generous vertical range, since it's
    // meant to read as a tall/deep standing shrine rather than a building -- {0,3} at Tier 1 up to
    // {5,9} (5 below, 9 above) at Tier 5, per the user's own explicit endpoints; the three middle
    // tiers are a plain interpolation, flagged as tunable like every other table in this class.
    private static final Map<Identifier, int[][]> HEIGHT_OVERRIDE_BY_TIER = Map.of(
            Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "recallcinite_stone"),
            new int[][] {{0, 3}, {1, 5}, {2, 6}, {4, 8}, {5, 9}});

    /** Type-aware height above ground -- checks {@link #HEIGHT_OVERRIDE_BY_TIER} first, falling back to this Tier's own shared {@link #heightAboveGround()}. */
    public static int heightAboveGround(BlueprintType type, TierSpec tier) {
        int[][] override = HEIGHT_OVERRIDE_BY_TIER.get(type.id());
        return override != null ? override[tier.ordinal()][1] : tier.heightAboveGround();
    }

    /** Type-aware depth below ground -- checks {@link #HEIGHT_OVERRIDE_BY_TIER} first, falling back to this Tier's own shared {@link #depthBelowGround()}. */
    public static int depthBelowGround(BlueprintType type, TierSpec tier) {
        int[][] override = HEIGHT_OVERRIDE_BY_TIER.get(type.id());
        return override != null ? override[tier.ordinal()][0] : tier.depthBelowGround();
    }

    /** {@code tier} is the 1-based Tier number (1-5, matching this enum's own names), not a 0-based index. */
    public static TierSpec fromOrdinal(int tier) {
        TierSpec[] values = values();
        if (tier < 1 || tier > values.length) {
            throw new IllegalArgumentException("Tier out of range: " + tier);
        }
        return values[tier - 1];
    }

    /** The deepest any Tier's below-ground allowance reaches -- how far down {@code SiteTerrainOps#levelClearingArea}'s bedrock foundation must extend to always cover it, regardless of which Tier ends up selected later. */
    public static int maxDepthBelowGround() {
        int max = 0;
        for (TierSpec spec : values()) {
            max = Math.max(max, spec.depthBelowGround());
        }
        return max;
    }
}
