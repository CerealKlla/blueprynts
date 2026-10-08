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
            Settlemynts.registerZoneType(new ZoneType(type.id(), type.label(), wallBlockFor(type), type.npcOwnedOnly()));
        }
    }

    // Colored glass, not wool -- matches Settlemynts' own built-in Zone Types (SettlemyntsMod, switched
    // from wool to glass 2026-09-27), corrected here to match 2026-09-29 (this bridge was the one place
    // never updated, and it's the one every Blueprynts-driven plot actually renders with).
    private static net.minecraft.world.level.block.Block wallBlockFor(BlueprintType type) {
        String path = type.id().getPath();
        return switch (path) {
            case "farm" -> Blocks.LIME_STAINED_GLASS;
            case "lumberyard" -> Blocks.ORANGE_STAINED_GLASS;
            case "blacksmith" -> Blocks.GRAY_STAINED_GLASS;
            case "guardhouse" -> Blocks.RED_STAINED_GLASS;
            // Matches Settlemynts' own pre-existing native "private_residence" ZoneType's color --
            // a separate registration under a different Identifier (settlemynts:private_residence
            // vs. this one's blueprynts:private_residence), see this bridge's own class doc for why
            // that's not a collision; kept visually consistent anyway so the two don't look unrelated.
            case "private_residence" -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            // Four new vendor-style types, 2026-10-05.
            case "grocer" -> Blocks.YELLOW_STAINED_GLASS;
            case "armorer" -> Blocks.CYAN_STAINED_GLASS;
            case "restaurant" -> Blocks.PINK_STAINED_GLASS;
            case "building_supplier" -> Blocks.BROWN_STAINED_GLASS;
            // Stonemason, 2026-10-05, same Shop-auto-seeding feature as the vendor types above.
            case "stonemason" -> Blocks.WHITE_STAINED_GLASS;
            default -> Blocks.LIGHT_GRAY_STAINED_GLASS;
        };
    }
}
