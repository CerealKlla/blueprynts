package com.github.cerealklla.blueprynts.api;

import java.util.Collection;
import java.util.Optional;

import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;

import net.minecraft.resources.Identifier;

/**
 * The stable public entry point for other mods to integrate with Blueprynts -- currently just the
 * open {@link BlueprintType} registry. Same "stable facade, don't reach into internals" pattern as
 * Cartographyr's {@code Cartography}, Lyfe's {@code api.Lyfe}, Yconomics' {@code api.Yconomics},
 * and Settlemynts' {@code api.Settlemynts}.
 */
public final class Blueprynts {

    private Blueprynts() {
    }

    public static void registerBlueprintType(BlueprintType type) {
        BlueprintTypeRegistry.register(type);
    }

    public static Optional<BlueprintType> getBlueprintType(Identifier id) {
        return BlueprintTypeRegistry.get(id);
    }

    public static Collection<BlueprintType> getRegisteredBlueprintTypes() {
        return BlueprintTypeRegistry.all();
    }
}
