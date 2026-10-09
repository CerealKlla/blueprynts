package com.github.cerealklla.blueprynts.api;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.blueprint.GenericResource;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxBlockEntity;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxIndex;
import com.github.cerealklla.blueprynts.registration.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

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
     */
    public static UUID createConstructionBox(ServerLevel level, BlockPos pos, Direction facing, Identifier zoneTypeId, BuildableArea buildableArea, PlotArea plotArea) {
        level.setBlock(pos, ModBlocks.CONSTRUCTION_BOX.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        UUID id = UUID.randomUUID();
        if (level.getBlockEntity(pos) instanceof ConstructionBoxBlockEntity box) {
            box.setConstructionId(id);
            box.setZoneTypeId(zoneTypeId);
            box.setBuildableArea(buildableArea);
            box.setPlotArea(plotArea);
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

    /** One resource row of a Building Supply Box's funding state. {@code missing()} is never negative even if {@code supplied} somehow exceeds {@code required}. */
    public record ResourceFunding(int required, int supplied) {
        public int missing() {
            return Math.max(0, required - supplied);
        }
    }

    /**
     * A read-only snapshot of a Building Supply Box's funding state, for NPC auto-funding (Settlemynts'
     * {@code construction.NpcAutoFundingTicker}, added 2026-09-30) to compute its own delivery pace
     * against without ever importing {@code construction.ConstructionBoxBlockEntity} directly -- same
     * "read-only facade" pattern as {@link ConstructionBoxStatus}.
     */
    public record NpcFundingState(Map<GenericResource, ResourceFunding> resources, int maxTimeTicks) {
    }

    /** {@code Optional.empty()} if {@code boxPos} has no loaded box, or the box isn't currently fundable (no Blueprint bound yet, already built, or mid-Reposition -- see {@code ConstructionBoxBlockEntity#isFundable}). */
    public static Optional<NpcFundingState> getNpcFundingState(ServerLevel level, BlockPos boxPos) {
        if (!(level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) || !box.isFundable()) {
            return Optional.empty();
        }
        Map<GenericResource, ResourceFunding> resources = new EnumMap<>(GenericResource.class);
        for (GenericResource resource : GenericResource.values()) {
            resources.put(resource, new ResourceFunding(box.requiredAmount(resource), box.suppliedAmount(resource)));
        }
        return Optional.of(new NpcFundingState(resources, box.maxTimeTicks()));
    }

    /** Deposits {@code count} of {@code resource} into {@code boxPos}'s Building Supply Box, same clamping/completion-check behavior as a player's own deposit. No-op if the box isn't loaded. */
    public static void depositResource(ServerLevel level, BlockPos boxPos, GenericResource resource, int count) {
        if (level.getBlockEntity(boxPos) instanceof ConstructionBoxBlockEntity box) {
            box.deposit(level, resource, count);
        }
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
}
