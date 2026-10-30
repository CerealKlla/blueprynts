package com.github.cerealklla.blueprynts.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.blueprynts.BluepryntsMod;
import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxBlockEntity;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxIndex;
import com.github.cerealklla.blueprynts.registration.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The stable public entry point for other mods to integrate with Blueprynts -- the open {@link
 * BlueprintType} registry, plus (added 2026-09-29) Building Supply Box creation/lookup. Same
 * "stable facade, don't reach into internals" pattern as Cartographyr's {@code Cartography}, Lyfe's
 * {@code api.Lyfe}, Yconomics' {@code api.Yconomics}, and Settlemynts' {@code api.Settlemynts}.
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

    /**
     * Places a Building Supply Box at {@code pos} (facing {@code facing}), mints its persistent
     * Construction ID, and indexes it so {@link #getConstructionBoxPos} can resolve it later without
     * needing its chunk loaded. v1: identity only -- no funding/deposit state exists yet, see
     * {@code construction.ConstructionBoxBlockEntity}'s own doc for what's deliberately not built.
     *
     * @param zoneTypeId    the owning plot's Zone Type id (see {@code bridge.SettlemyntsZoneBridge}'s
     *                      own doc for why this is comparable directly against a {@code BlueprintType}
     *                      id), or {@code null} if unknown -- the box's picker is constrained to it.
     * @param buildableArea optional -- {@code null} places an "unbound" box (no reposition placement
     *                       constraint at all), a real {@link BuildableArea} binds it to that
     *                       rectangle. Deliberately a plain rectangle, not a {@code Geometry.Polygon}
     *                       or any other Cartographyr type -- see that record's own doc for why
     *                       (2026-09-29 correction: keeps this mod free of any Cartographyr
     *                       dependency). The caller does whatever real polygon math is needed and
     *                       reduces it to this one rectangle before calling.
     * @param plotArea       optional -- the *whole* plot's real per-cell interior shape (every
     *                       GREEN-or-BLUE cell), not just the buildable BLUE region, and -- unlike
     *                       {@code buildableArea} -- not reduced to a bounding rectangle, since a
     *                       plot is a real polygon and can be non-convex ({@link PlotArea}'s own doc).
     *                       {@code null} leaves the Supply Box unbound for reposition purposes too.
     *                       Added 2026-09-29 per user request/correction: "the supply box can go
     *                       anywhere within the plot, not only the 15x15 or 50x50 valid area for the
     *                       building" -- then "but a plot is a polygon, not a rectangle" -- the box's
     *                       own reposition check is against this field, never {@code buildableArea}.
     * @param settlementCoreId the owning plot's own settlement core id (Settlemynts' {@code
     *                       GhostTownHallCoreEntity#getUUID()}), and
     * @param plotId         ... its own plot id (Settlemynts' {@code PlotRecord#plotId()}) -- added
     *                       2026-10-22 so the box can reach back into Settlemynts by plot identity
     *                       directly (pulling missing materials from the plot's own boxes, pushing a
     *                       shortfall to Planned Inventory) without resolving "which plot is this"
     *                       from its own world position, which doesn't work -- the box sits just
     *                       outside the plot's buildable area (see {@code
     *                       ConstructionBoxMenu#resolvePermissionCheckPos}'s own doc for the same
     *                       gotcha already found once for permission checks). Either may be {@code
     *                       null} (a Settlemynts-less server, or a caller with no plot of its own).
     */
    public static UUID createConstructionBox(ServerLevel level, BlockPos pos, Direction facing, Identifier zoneTypeId,
                                               BuildableArea buildableArea, PlotArea plotArea, UUID settlementCoreId, UUID plotId) {
        level.setBlock(pos, ModBlocks.CONSTRUCTION_BOX.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        UUID id = UUID.randomUUID();
        if (level.getBlockEntity(pos) instanceof ConstructionBoxBlockEntity box) {
            box.setConstructionId(id);
            box.setZoneTypeId(zoneTypeId);
            box.setBuildableArea(buildableArea);
            box.setPlotArea(plotArea);
            box.setPlotIdentity(settlementCoreId, plotId);
        }
        ConstructionBoxIndex.get(level.getServer()).put(id, GlobalPos.of(level.dimension(), pos));
        return id;
    }

    /** Resolves a Building Supply Box's current position by its Construction ID, independent of chunk-load state. */
    public static Optional<BlockPos> getConstructionBoxPos(ServerLevel level, UUID constructionId) {
        return ConstructionBoxIndex.get(level.getServer()).get(constructionId).map(GlobalPos::pos);
    }

    /**
     * A read-only snapshot of a Building Supply Box's status, for an external mod that needs to react
     * to it without ever importing {@code construction.ConstructionBoxBlockEntity} directly -- added
     * 2026-09-30 for Settlemynts' own Plot Config Sign (spawned once a plot's building has actually
     * finished construction, and later used to validate where its sign can be relocated to within the
     * same plot).
     */
    public record ConstructionBoxStatus(boolean everCompleted, PlotArea plotArea) {
    }

    /** {@code Optional.empty()} if {@code boxPos} has no loaded Building Supply Box (chunk not loaded, or never a box at all). */
    public static Optional<ConstructionBoxStatus> getConstructionBoxStatus(ServerLevel level, BlockPos boxPos) {
        if (!(level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box)) {
            return Optional.empty();
        }
        return Optional.of(new ConstructionBoxStatus(box.everCompleted(), box.plotArea()));
    }

    /**
     * Every item {@code boxPos}'s Building Supply Box is still short of, for an external mod that
     * needs to react to a shortfall without ever importing {@code
     * construction.ConstructionBoxBlockEntity} directly -- replaces the old {@code NpcFundingState}/
     * {@code getNpcFundingState} (2026-10-22, see that class's own doc for the funding-model
     * rework this is part of). Empty if the box isn't loaded, isn't fundable, or has nothing missing.
     */
    public static Map<Identifier, Integer> getMissingMaterials(ServerLevel level, BlockPos boxPos) {
        if (!(level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) || !box.isFundable()) {
            return Map.of();
        }
        Map<Identifier, Integer> missing = new java.util.LinkedHashMap<>();
        box.missingItems().forEach((item, amount) ->
                missing.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item), amount));
        return missing;
    }

    /** Deposits {@code count} of the real item {@code itemId} into {@code boxPos}'s Building Supply Box, same clamping/completion-check behavior as a player's own deposit. No-op if the box isn't loaded or {@code itemId} doesn't resolve to a real item. */
    public static void depositItem(ServerLevel level, BlockPos boxPos, Identifier itemId, int count) {
        if (!(level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box)) {
            return;
        }
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId)
                .ifPresent(item -> box.deposit(level, item, count));
    }

    /**
     * Re-triggers {@code boxPos}'s Building Supply Box's own plot-stock pull (see {@code
     * ConstructionBoxBlockEntity#pullFromPlotAndSync}) -- the intended call site for Settlemynts'
     * own {@code bridge.BlueprintsConstructionBridge} to notify a box that the owning plot's boxes
     * just changed (added 2026-10-22, mirrors {@code watchtower.WatchTowerRefuelNotifier}'s own
     * trigger shape for the Watch Tower Beacon). No-op if the box isn't loaded.
     */
    public static void notifyPlotBoxesChanged(ServerLevel level, BlockPos boxPos) {
        if (level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) {
            box.pullFromPlotAndSync(level);
        }
    }

    /**
     * Appends one line to {@code boxPos}'s Building Supply Box transaction ledger (added
     * 2026-10-12) -- the intended call site for any mod recording a Shop purchase/sale against the
     * owning plot, e.g. Settlemynts' own buy/sell handlers and its nightly inter-plot Planned
     * Inventory settlement. No-op if the box isn't loaded; this mod knows nothing about Shops,
     * gold, or plots itself -- {@code line} is the caller's own already-formatted description.
     */
    public static void appendLedgerEntry(ServerLevel level, BlockPos boxPos, String line) {
        if (level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) {
            box.appendLedgerEntry(level.getGameTime(), line);
        }
    }

    /** Every recorded ledger line for {@code boxPos}'s box, oldest first, formatted as {@code "Day N: <line>"}. Empty if the box isn't loaded or has no entries yet. */
    public static java.util.List<String> getLedgerLines(ServerLevel level, BlockPos boxPos) {
        if (!(level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box)) {
            return java.util.List.of();
        }
        return box.ledger().stream()
                .map(entry -> "Day " + (entry.gameTime() / 24000L) + ": " + entry.line())
                .toList();
    }

    /**
     * The bound Blueprint's own Tier (1-5), for an external mod that needs to scale something by it
     * without ever importing {@code construction.ConstructionBoxBlockEntity} directly -- added
     * 2026-09-30 for Settlemynts' Guardhouse Plot Type (garrison size/gear caps scale by Tier).
     * {@code Optional.empty()} if the box isn't loaded or no Blueprint has been bound yet.
     */
    public static Optional<Integer> getConstructionBoxTier(ServerLevel level, BlockPos boxPos) {
        if (!(level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) || box.tier() <= 0) {
            return Optional.empty();
        }
        return Optional.of(box.tier());
    }

    /**
     * Raises this Building Supply Box's "allowed Tier" cap -- the highest Blueprint Tier its own
     * picker will offer -- added 2026-10-09 for Settlemynts' "Upgrade Plot" ({@code
     * construction.PlotTierUpgradeFunding}). Deliberately independent of {@link
     * #getConstructionBoxTier} (which Blueprint is actually bound/built right now): "you can
     * upgrade the tier to tier 2 and still have a tier 1 building." No-op if the box isn't loaded.
     */
    public static void setConstructionBoxAllowedTier(ServerLevel level, BlockPos boxPos, int tier) {
        if (level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) {
            box.setAllowedTier(tier);
        }
    }

    /**
     * Moves {@code oldPos}'s Building Supply Box to {@code newPos}, preserving its full state (bound
     * Blueprint, funding/build progress, ledger, plot identity -- everything the block entity's own
     * save/load already round-trips) and re-pointing {@link ConstructionBoxIndex} at the new
     * position. Added 2026-10-29 for Settlemynts' {@code debug.DebugCommands#resetPlotStructures} --
     * a Nether-founded plot's box ended up right under the world's ceiling (see that command's own
     * doc for the full incident), and this is the general "move this box somewhere else, keep
     * everything about it" primitive that fix needed. This is a direct move with no player/Locator
     * interaction at all -- the existing {@code SupplyBoxLocatorItem} player-facing relocation flow
     * is untouched.
     *
     * @return {@code false} if {@code oldPos} has no loaded Building Supply Box, {@code newPos}
     *         already has something else there, or {@code newPos} equals {@code oldPos} (nothing to do).
     */
    public static boolean relocateConstructionBox(ServerLevel level, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos) || !(level.getBlockEntity(oldPos) instanceof ConstructionBoxBlockEntity box)
                || !level.getBlockState(newPos).isAir()) {
            return false;
        }
        BlockState state = level.getBlockState(oldPos);
        CompoundTag savedData = box.saveCustomOnly(level.registryAccess());
        UUID id = box.constructionId();
        level.removeBlock(oldPos, false);
        level.setBlock(newPos, state, 3);
        if (level.getBlockEntity(newPos) instanceof ConstructionBoxBlockEntity newBox) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(newBox.problemPath(), BluepryntsMod.LOGGER)) {
                newBox.loadCustomOnly(TagValueInput.create(reporter, level.registryAccess(), savedData));
            }
            newBox.setChanged();
        }
        if (id != null) {
            ConstructionBoxIndex.get(level.getServer()).put(id, GlobalPos.of(level.dimension(), newPos));
        }
        return true;
    }
}
