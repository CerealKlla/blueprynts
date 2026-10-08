package com.github.cerealklla.blueprynts.construction;

import java.util.Map;

import com.github.cerealklla.blueprynts.blueprint.GenericResource;

/**
 * A Construction Box's own funding requirements, converted from Settlemynts' {@code
 * ConstructionRequirements}/{@code ResourceCost} into a purely Blueprynts-native shape right at the
 * {@code bridge.SettlemyntsConstructionConfigBridge} boundary -- see that class's own doc for why
 * (this is the type {@code ConstructionBoxBlockEntity}, an always-loaded class, is allowed to touch).
 *
 * @param minTimeTicks real construction duration -- 0-100% funding-gated construction progress is
 *                     additionally capped by elapsed time since binding, over this many ticks (added
 *                     2026-09-29: "it shouldn't hit 100% immediately... it should be 100% funded
 *                     immediately, not 100% constructed" -- funding and construction progress are
 *                     deliberately decoupled, not the same number).
 * @param maxTimeTicks NPC auto-funding's own target pace -- not read by anything yet (no NPC
 *                     auto-delivery pipeline exists this pass).
 */
public record FundingRequirements(Map<GenericResource, Integer> costs, int minTimeTicks, int maxTimeTicks) {

    public static final FundingRequirements NONE = new FundingRequirements(Map.of(), 0, 0);
}
