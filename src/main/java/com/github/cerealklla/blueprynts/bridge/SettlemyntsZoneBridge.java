package com.github.cerealklla.blueprynts.bridge;

import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.zone.ZoneType;

import net.minecraft.world.level.block.Blocks;

/**
 * Bridges Blueprynts' own {@link BlueprintType} registry into Settlemynts' {@code ZoneTypeRegistry}
 * -- a Blueprint Type is "equivalent to the Zone in Settlemynts" per the user, so every built-in
 * Blueprint Type is also registered there, one-for-one, if Settlemynts happens to be loaded.
 *
 * <p>This class must only ever be referenced (its {@link #registerBuiltins} called, or this class
 * loaded/classload-triggered at all) from behind a {@code ModList.get().isLoaded("settlemynts")}
 * check at the call site -- same isolation Lyfe uses for its own optional Cartographyr/Yconomics
 * integrations, so a Settlemynts-less server never force-loads Settlemynts classes.
 */
public final class SettlemyntsZoneBridge {

    private SettlemyntsZoneBridge() {
    }

    public static void registerBuiltins() {
        for (BlueprintType type : com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry.all()) {
            Settlemynts.registerZoneType(new ZoneType(type.id(), type.label(), wallBlockFor(type)));
        }
    }

    private static net.minecraft.world.level.block.Block wallBlockFor(BlueprintType type) {
        String path = type.id().getPath();
        return switch (path) {
            case "farm" -> Blocks.LIME_WOOL;
            case "lumberyard" -> Blocks.ORANGE_WOOL;
            case "blacksmith" -> Blocks.GRAY_WOOL;
            default -> Blocks.LIGHT_GRAY_WOOL;
        };
    }
}
