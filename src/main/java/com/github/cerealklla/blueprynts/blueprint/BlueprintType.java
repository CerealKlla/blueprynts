package com.github.cerealklla.blueprynts.blueprint;

import net.minecraft.resources.Identifier;

/**
 * An open, trust-based Blueprint Type -- the equivalent concept to Settlemynts' {@code ZoneType}
 * (same governance shape: any mod may register its own via {@link BlueprintTypeRegistry#register},
 * no claiming step). Built-ins (Farm, Lumberyard, Blacksmith) are registered by this mod itself at
 * common setup, and bridged into Settlemynts' own zone-type registry if Settlemynts is loaded (see
 * {@code bridge.SettlemyntsZoneBridge}) -- but this registry is Blueprynts' own, not a copy.
 *
 * @param id the type's identity
 * @param label a short display label a consumer can show verbatim (e.g. "Farm")
 * @param areaScale a per-type multiplier on the {@link SlabBudget} area formula -- e.g. a Farm may
 *                  eventually warrant a larger footprint budget than a Blacksmith at the same
 *                  Size/Tier. All built-ins start at {@code 1.0}; the user was explicit they
 *                  haven't decided real per-type scaling yet and expect to tune it once testable --
 *                  this field exists so that tuning never requires a code change, just a new value.
 */
public record BlueprintType(Identifier id, String label, double areaScale) {
}
