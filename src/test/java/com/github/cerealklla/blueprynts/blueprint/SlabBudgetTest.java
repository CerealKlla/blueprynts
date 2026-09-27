package com.github.cerealklla.blueprynts.blueprint;

import org.junit.jupiter.api.Test;

import com.github.cerealklla.blueprynts.construction.SizeClass;

import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlabBudgetTest {

    private static final BlueprintType NEUTRAL = new BlueprintType(Identifier.fromNamespaceAndPath("test", "test"), "Test", 1.0);

    @Test
    void largeGrantsMoreThanSmallAtTheSameTier() {
        int small = SlabBudget.compute(SizeClass.SMALL, TierSpec.T2, NEUTRAL);
        int large = SlabBudget.compute(SizeClass.LARGE, TierSpec.T2, NEUTRAL);
        assertTrue(large > small);
    }

    @Test
    void higherTierGrantsMoreThanLowerTierAtTheSameSize() {
        int t0 = SlabBudget.compute(SizeClass.SMALL, TierSpec.T0, NEUTRAL);
        int t5 = SlabBudget.compute(SizeClass.SMALL, TierSpec.T5, NEUTRAL);
        assertTrue(t5 > t0);
    }

    @Test
    void blueprintTypeAreaScaleIsALiveMultiplier() {
        BlueprintType doubled = new BlueprintType(Identifier.fromNamespaceAndPath("test", "doubled"), "Doubled", 2.0);
        int neutral = SlabBudget.compute(SizeClass.SMALL, TierSpec.T3, NEUTRAL);
        int scaled = SlabBudget.compute(SizeClass.SMALL, TierSpec.T3, doubled);
        assertEquals(neutral * 2, scaled);
    }

    @Test
    void neverReturnsZeroOrNegative() {
        BlueprintType tiny = new BlueprintType(Identifier.fromNamespaceAndPath("test", "tiny"), "Tiny", 0.0001);
        assertTrue(SlabBudget.compute(SizeClass.SMALL, TierSpec.T0, tiny) >= 1);
    }
}
