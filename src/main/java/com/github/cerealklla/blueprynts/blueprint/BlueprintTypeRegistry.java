package com.github.cerealklla.blueprynts.blueprint;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

/**
 * In-memory registry of every known {@link BlueprintType}, populated by mods calling {@link
 * com.github.cerealklla.blueprynts.api.Blueprynts#registerBlueprintType} at their own startup --
 * not persisted, not tied to a specific world, rebuilt fresh every boot. Same trust-based
 * governance as Settlemynts' {@code ZoneTypeRegistry}: no claiming step, registering under an id
 * already present just replaces it.
 */
public final class BlueprintTypeRegistry {

    private static final Map<Identifier, BlueprintType> TYPES = new ConcurrentHashMap<>();

    private BlueprintTypeRegistry() {
    }

    public static void register(BlueprintType type) {
        TYPES.put(type.id(), type);
    }

    public static Optional<BlueprintType> get(Identifier id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    public static Collection<BlueprintType> all() {
        return List.copyOf(TYPES.values());
    }
}
