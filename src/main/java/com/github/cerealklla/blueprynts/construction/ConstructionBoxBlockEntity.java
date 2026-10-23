package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.blueprynts.api.BuildableArea;
import com.github.cerealklla.blueprynts.api.PlotArea;
import com.github.cerealklla.blueprynts.bridge.SettlemyntsPlotBoxBridge;
import com.github.cerealklla.blueprynts.registration.ModBlockEntities;
import com.github.cerealklla.blueprynts.registration.ModMenus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Building Supply Box's live state (design-document.md's root "CONSTRUCTION MATERIAL
 * RESERVOIR"/"CONSTRUCTION PROJECT" concept, Sections 16-17; cross-referenced from Settlemynts'
 * own context/design-document.md Section 11 point 4 as the "Building Supply Box"). Deliberately a
 * distinct block from {@link ConstructionSiteBlock} -- that block is the free-form footprint
 * *authoring* tool; this one is the materials-funding reservoir for a structure a plot already
 * knows the footprint of.
 *
 * <p><b>Funding model rebuilt 2026-10-22</b> (explicit user spec, replacing the 2026-09-29 "Planned:
 * Passive Construction & Funding" design) -- {@link #required}/{@link #supplied} are now keyed by
 * the real exact {@link Item} the bound Blueprint actually uses (computed once at bind time by
 * {@link com.github.cerealklla.blueprynts.blueprint.BlueprintMaterialCost}), not an abstract
 * {@code GenericResource} (Wood/Stone) category sourced from a hand-tuned Settlemynts Zone+Tier cost
 * table -- "attempt to buy the components which make up the blueprint." There is also no more
 * elapsed-time construction gate ({@code minTimeTicks}/{@code maxTimeTicks}/{@code
 * accumulatedTimeTicks} all removed) -- "no longer need the 'NPC owned buildings take longer to
 * build' concept"; construction progress is now purely {@link #fundedPercent()}, so a box that gets
 * fully funded (by any combination of the plot's own stock, the settlement economy, or a player's
 * own deposit) paste immediately. This also means the box never needs a {@code BlockEntityTicker} at
 * all any more -- see {@code ConstructionBoxBlock}'s own doc (standing rule, 2026-10-22: avoid
 * non-event-driven chron jobs wherever a real event can trigger the same work instead).
 *
 * <p>Funding itself is now layered, event-driven, same precedent as {@code
 * watchtower.WatchTowerBeaconBlockEntity}'s fuel: (1) a manual player deposit (drag or shift-click)
 * is used immediately, same as always; (2) {@link #pullFromPlotAndSync} -- called once right after
 * binding, and again any time Settlemynts reports the owning plot's own boxes changed -- withdraws
 * whatever it can of the still-missing items directly from the plot's own storage, for free; (3)
 * whatever's still missing after that is pushed to the plot's Planned Inventory (flagged {@code
 * derivedFromConstructionBox} on the Settlemynts side) so the existing nightly inter-plot-trading
 * pass buys it from the settlement economy automatically, at zero new purchasing code written here
 * or in Settlemynts -- "leverage the existing pipeline entirely for the purchasing." {@link
 * #settlementCoreId}/{@link #plotId} (new fields, set once at creation by Settlemynts' own {@code
 * bridge.BlueprintsConstructionBridge#placeConstructionBox}, which already knows both at that
 * moment) are what let this box reach into Settlemynts for both of those steps directly by plot
 * identity -- deliberately NOT resolved from this box's own world position via {@code
 * api.Settlemynts#findPlotAt}, since the box is placed "just outside the plot's buildable area"
 * (see {@code ConstructionBoxMenu#resolvePermissionCheckPos}'s own doc for the exact same gotcha
 * already found once for permission checks) and would resolve to "no plot" there. {@code null} for
 * a box created before this pair of fields existed -- the pull/sync steps are silent no-ops for
 * those (a real, accepted migration gap, not silently guessed around; flagged in decisions.md).
 *
 * <p><b>v1 scope, 2026-09-29</b>: only the identity piece -- {@link #constructionId()}, minted once
 * at creation and never reassigned -- so a box can be referenced from outside without needing its
 * chunk loaded first (see {@link ConstructionBoxIndex}).
 *
 * <p>Gained {@link #zoneTypeId()} and {@link #blueprintPlaced()} the same day, per user request: the
 * picker this box opens is constrained to Blueprint Types matching the owning plot's own Zone Type
 * (a Blueprint Type "is equivalent to the Zone" -- see {@code bridge.SettlemyntsZoneBridge}'s own
 * doc, so comparing the two {@code Identifier}s directly needs no compiled Settlemynts dependency),
 * and once a Blueprint has actually been placed here the picker option is withdrawn entirely.
 *
 * <p><b>"Reposition Building," same day</b>: a box is no longer strictly one-shot -- once a
 * Blueprint is placed, right-clicking the box instead offers "Reposition Building" (removes the
 * structure and hands back a Building Locator item bound to this box's {@link #constructionId()}).
 * {@link #placedBlueprintName()} is never cleared by this (the box stays bound to the same
 * Blueprint -- reposition moves it, it doesn't let you pick a different one), only {@link
 * #blueprintPlaced()} toggles back to {@code false} while the Locator is out. {@link
 * #buildableArea()} is a plain rectangle (nullable -- see {@link BuildableArea}'s own doc)
 * describing where a reposition is allowed to land; using a plain rectangle instead of a real
 * {@code Geometry.Polygon} is deliberate -- it's what lets this whole mod stay free of any
 * Cartographyr dependency again (2026-09-29 correction: an earlier version of this feature gave
 * Blueprynts a real compile dependency on Cartographyr for this, which the user flagged as
 * unnecessary coupling -- "we can break that dependency by having Settlemynts give the Construction
 * Box the full bounding box of where it's allowed to be placed when it first creates the Construction
 * Box").
 */
public class ConstructionBoxBlockEntity extends BlockEntity implements MenuProvider {

    private static final Codec<BuildableArea> AREA_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("min_x").forGetter(BuildableArea::minX),
            Codec.INT.fieldOf("min_z").forGetter(BuildableArea::minZ),
            Codec.INT.fieldOf("max_x").forGetter(BuildableArea::maxX),
            Codec.INT.fieldOf("max_z").forGetter(BuildableArea::maxZ)
    ).apply(i, BuildableArea::new));

    private static final Codec<Map<Identifier, Integer>> ITEM_AMOUNTS_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);

    private UUID constructionId;
    private Identifier zoneTypeId;
    private boolean blueprintPlaced;
    private String placedBlueprintName;
    private BuildableArea buildableArea;
    private PlotArea plotArea;
    private TerrainSnapshot removalSnapshot = new TerrainSnapshot();

    // Settlemynts' own identity for the plot this box was created on -- see this class's own doc.
    // Set once at creation (api.Blueprynts#createConstructionBox), never reassigned.
    private UUID settlementCoreId;
    private UUID plotId;

    // Funding tally, rebuilt 2026-10-22 (see this class's own doc) -- keyed by the real Item the
    // bound Blueprint actually uses, computed once by BlueprintMaterialCost at bind time. `required`
    // is never recalculated after binding (an in-progress box's own already-agreed cost doesn't
    // retroactively change). `everCompleted` distinguishes "never yet built" (still funding) from
    // "was built, currently mid-Reposition" -- both look like blueprintPlaced() == false with a
    // non-null placedBlueprintName(), but need different useWithoutItem() behavior.
    private Map<Item, Integer> required = new LinkedHashMap<>();
    private Map<Item, Integer> supplied = new LinkedHashMap<>();
    private boolean everCompleted;

    // Not persisted -- re-derived fresh every load (see loadAdditional's own doc) and always false
    // right after a real initializeRequirements call, so there's nothing to save. True only for a
    // box loaded with a bound Blueprint whose Required/Supplied NBT failed to decode (stale/
    // incompatible data from an older funding-model version) -- freezes isFundable() until an
    // explicit "Change Blueprint" rebind recomputes everything cleanly, rather than risking
    // fundedPercent() trivially reading "100% of nothing required" and auto-completing for free.
    private boolean legacyFundingDataLost;

    // Suspends attemptCompletion entirely between "Reposition Building" clearing whatever was
    // standing and the player actually placing a new anchor with the Locator -- a real bug,
    // 2026-09-29: without this, a re-check resumed building at the *old* anchor within about a
    // second of the clear (funding hadn't changed, so it just re-pasted right back), and then
    // placing the new anchor left those stray blocks behind, untracked, since a fresh
    // removalSnapshot had already started accumulating for the new location by then.
    private boolean repositionPending;

    // Where the (still-unbuilt) bound Blueprint will actually paste -- {@code null} means "this
    // box's own position" (the original, still-default anchor). Lets "Reposition Building" work
    // *before* funding completes, not just after (real playtest correction, 2026-09-29: "Are you
    // making it only movable when it's completely built? If so that won't work") -- a player can
    // grab a Building Locator and pick a different spot for the pending structure without needing
    // to finish paying for it first; {@link #attemptCompletion} reads through {@link #anchorPos()}/
    // {@link #anchorFacing()} instead of {@code getBlockPos()}
    // directly. Once the box has ever completed a build, the *existing* post-build reposition flow
    // (remove + immediately re-place via {@code BuildingLocatorItem}) takes over instead, and these
    // two fields stop being read at all.
    private BlockPos pendingAnchor;
    private Direction pendingFacing;

    // The bound Blueprint's own Tier (1-5) -- known at bind time (BluepryntsMod's Blueprint-select
    // handler already reads it off the loaded BlueprintRecord) but never stored on the box itself
    // until Settlemynts' Guardhouse feature needed to read it back cross-mod (garrison size/gear
    // caps scale by Tier) -- same "accepted but discarded until a real consumer needed it" gap
    // maxTimeTicks itself used to have. 0 means unbound/unknown.
    private int tier;
    // The highest Blueprint Tier this box is currently ALLOWED to build at -- added 2026-10-09,
    // genuinely independent of `tier` above (which Blueprint is actually bound/built right now).
    // Pushed in from Settlemynts' `construction.PlotTierUpgradeFunding` ("Upgrade Plot") via {@code
    // api.Blueprynts#setConstructionBoxAllowedTier}, never raised by this mod on its own. Defaults
    // to 1 (every plot starts at Tier 1) -- see `zone.PlotRecord#tier`'s own class doc on the
    // Settlemynts side for the full split between "unlocked Tier cap" and "built Tier."
    private int allowedTier = 1;
    // Not persisted -- purely an optimization so a redundant re-check doesn't re-run
    // RealBlueprintPlacement when nothing has actually changed; worst case after a reload it just
    // redundantly re-pastes the same already-correct cells once, harmless.
    private int lastBuiltPercent = -1;

    // Transaction ledger (added 2026-10-12, explicit user request) -- a standing, human-readable
    // record of every purchase/sale touching the owning plot's Shop, appended via a thin
    // api.Blueprynts facade call rather than built here (this mod knows nothing about Shops or
    // gold). Capped so it can't grow unbounded over a long-lived world -- oldest entries drop off
    // first, same "it's a diagnostic trail, not an audit requirement" framing as everything else
    // this suite logs.
    private static final int LEDGER_MAX_ENTRIES = 200;
    private final List<LedgerEntry> ledger = new ArrayList<>();

    public ConstructionBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_BOX.get(), pos, state);
    }

    public UUID constructionId() {
        return constructionId;
    }

    /** Only ever called once, immediately after placement -- see {@code api.Blueprynts#createConstructionBox}. */
    public void setConstructionId(UUID constructionId) {
        this.constructionId = constructionId;
        setChanged();
    }

    /** The owning plot's Zone Type id, if known -- {@code null} for a box created before this field existed, or with no known plot. */
    public Identifier zoneTypeId() {
        return zoneTypeId;
    }

    /** Only ever called once, immediately after placement -- see {@code api.Blueprynts#createConstructionBox}. */
    public void setZoneTypeId(Identifier zoneTypeId) {
        this.zoneTypeId = zoneTypeId;
        setChanged();
    }

    /** {@code null} for a box created before Settlemynts started passing this at creation -- see this class's own doc. */
    public UUID settlementCoreId() {
        return settlementCoreId;
    }

    public UUID plotId() {
        return plotId;
    }

    /** Only ever called once, immediately after placement -- see {@code api.Blueprynts#createConstructionBox}. */
    public void setPlotIdentity(UUID settlementCoreId, UUID plotId) {
        this.settlementCoreId = settlementCoreId;
        this.plotId = plotId;
        setChanged();
    }

    /** {@code true} once a Blueprint has been placed here -- the picker option is withdrawn for good after that. */
    public boolean blueprintPlaced() {
        return blueprintPlaced;
    }

    public void setBlueprintPlaced(boolean blueprintPlaced) {
        this.blueprintPlaced = blueprintPlaced;
        setChanged();
    }

    /** The Blueprint name this box is bound to, once one has ever been placed here -- survives a Reposition (only {@link #blueprintPlaced()} toggles then). */
    public String placedBlueprintName() {
        return placedBlueprintName;
    }

    public void setPlacedBlueprintName(String placedBlueprintName) {
        this.placedBlueprintName = placedBlueprintName;
        setChanged();
    }

    /** {@code null} for an "unbound" box (no placement constraint) or one created before this field existed. */
    public BuildableArea buildableArea() {
        return buildableArea;
    }

    /** Only ever called once, immediately after placement -- see {@code api.Blueprynts#createConstructionBox}. */
    public void setBuildableArea(BuildableArea buildableArea) {
        this.buildableArea = buildableArea;
        setChanged();
    }

    /** {@code null} for an "unbound" box or one created before this field existed. Unlike {@link #buildableArea()} (a bounding-rectangle approximation of the 15x15/50x50 building footprint only), this is the plot's real per-cell shape -- what {@link SupplyBoxLocatorItem} checks a reposition against (user request/correction, 2026-09-29: "the supply box can go anywhere within the plot, not only the 15x15 or 50x50 valid area for the building" -- then "but a plot is a polygon, not a rectangle"). */
    public PlotArea plotArea() {
        return plotArea;
    }

    /** Only ever called once, immediately after placement -- see {@code api.Blueprynts#createConstructionBox}. */
    public void setPlotArea(PlotArea plotArea) {
        this.plotArea = plotArea;
        setChanged();
    }

    /** Every real position the currently-placed structure overwrote, captured pre-paste -- see {@code RealBlueprintPlacement}'s own doc. Empty when nothing is placed. */
    public TerrainSnapshot removalSnapshot() {
        return removalSnapshot;
    }

    public void setRemovalSnapshot(TerrainSnapshot removalSnapshot) {
        this.removalSnapshot = removalSnapshot == null ? new TerrainSnapshot() : removalSnapshot;
        setChanged();
    }

    /**
     * Restores every real position {@link #removalSnapshot} captured -- whatever's actually been
     * pasted so far, whether that's a fully-completed structure or only partial construction
     * progress (incremental, percentage-driven building means "Relocate Building" can now be
     * clicked mid-build, not just once finished -- real playtest request, 2026-09-29: "as soon as
     * someone hits 'Relocate Building' it needs to clear the partially built ones"). Also resets
     * {@link #lastBuiltPercent} so the next {@link #attemptCompletion} at a new anchor
     * doesn't think progress already exceeds what's actually standing there. Does **not** touch
     * funding progress ({@link #required}/{@link #supplied}) -- moving the pending build location
     * doesn't refund it.
     *
     * @return the instance callers must continue operating on -- almost always {@code this}, but if
     *         this very box turned out to be sitting inside its own removalSnapshot (see below) and
     *         had to be relocated out of the way, {@code this} is now a stale, orphaned object and
     *         the fresh instance at the new position is returned instead. Callers (see {@code
     *         BluepryntsMod}'s {@code RepositionBuildingPayload} handler) MUST use the returned
     *         reference for anything after this call, not their own original variable.
     */
    public ConstructionBoxBlockEntity clearPartialConstruction(ServerLevel level) {
        if (!removalSnapshot.isEmpty()) {
            // Guard this restore the same way attemptCompletion's own paste already is -- a real bug,
            // 2026-09-30: a Construction Box (or Plot Config Sign) manually placed inside an
            // already-built structure's footprint sat on a position this snapshot captured, so
            // restoring straight to that position's pre-construction state silently destroyed it --
            // a completely different code path from the paste-time overlap fix, which only guards
            // RealBlueprintPlacement's own setBlock loop, never this restore. If this box itself is
            // the one relocated, its own Java reference is now stale -- finish against, and return,
            // the fresh instance instead (same pattern attemptCompletion already uses).
            Set<BlockPos> restoredPositions = removalSnapshot.capturedStates().keySet();
            int floorY = SiteTerrainOps.siteFloorY(getBlockPos());
            Optional<BlockPos> selfMoved = ConstructionBoxOverlapGuard.relocateObstructions(
                    level, restoredPositions, floorY, buildableArea, constructionId);
            if (selfMoved.isPresent()) {
                return level.getBlockEntity(selfMoved.get()) instanceof ConstructionBoxBlockEntity moved
                        ? moved.clearPartialConstruction(level) : this;
            }
            SiteTerrainOps.restore(level, removalSnapshot);
        }
        removalSnapshot = new TerrainSnapshot();
        lastBuiltPercent = -1;
        setChanged();
        return this;
    }

    /** {@code true} once this box has ever successfully completed a build -- survives a later Reposition (only {@link #blueprintPlaced()} toggles then), distinguishing "still funding, never built" from "was built, mid-Reposition" (both look like {@code !blueprintPlaced() && placedBlueprintName() != null}). */
    public boolean everCompleted() {
        return everCompleted;
    }

    /** {@code true} between "Reposition Building" clearing the old build and the player actually placing a new anchor with the Locator -- see the field's own doc. */
    public boolean repositionPending() {
        return repositionPending;
    }

    public void setRepositionPending(boolean repositionPending) {
        this.repositionPending = repositionPending;
        setChanged();
    }

    /** The position the bound Blueprint will paste at -- this box's own position unless {@link #setPendingAnchor} has moved it. */
    public BlockPos anchorPos() {
        return pendingAnchor != null ? pendingAnchor : getBlockPos();
    }

    /** The facing the bound Blueprint will paste with -- this box's own current {@code FACING} unless {@link #setPendingAnchor} has overridden it. */
    public Direction anchorFacing() {
        return pendingFacing != null ? pendingFacing : getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    /** Only meaningful pre-build (see the fields' own doc) -- moves where the pending structure will paste once funded, without touching funding progress. */
    public void setPendingAnchor(BlockPos anchor, Direction facing) {
        this.pendingAnchor = anchor;
        this.pendingFacing = facing;
        setChanged();
    }

    /**
     * Copies every bit of {@code source}'s own state into this (freshly-placed, otherwise-default)
     * instance -- used by {@link ConstructionBoxOverlapGuard} when a Building Supply Box has to be
     * automatically moved out of a building's incoming footprint, so the move is invisible to
     * everything that was tracking {@code source}'s own {@link #constructionId()} (funding progress,
     * {@link #buildableArea()}, an outstanding {@code repositionPending} state, etc.) -- only the
     * block's own position changes, nothing about what it represents.
     */
    void copyStateFrom(ConstructionBoxBlockEntity source) {
        this.constructionId = source.constructionId;
        this.zoneTypeId = source.zoneTypeId;
        this.settlementCoreId = source.settlementCoreId;
        this.plotId = source.plotId;
        this.blueprintPlaced = source.blueprintPlaced;
        this.placedBlueprintName = source.placedBlueprintName;
        this.buildableArea = source.buildableArea;
        this.plotArea = source.plotArea;
        this.removalSnapshot = source.removalSnapshot;
        this.required = new LinkedHashMap<>(source.required);
        this.supplied = new LinkedHashMap<>(source.supplied);
        this.everCompleted = source.everCompleted;
        this.legacyFundingDataLost = source.legacyFundingDataLost;
        this.repositionPending = source.repositionPending;
        this.pendingAnchor = source.pendingAnchor;
        this.pendingFacing = source.pendingFacing;
        this.tier = source.tier;
        this.allowedTier = source.allowedTier;
        this.lastBuiltPercent = source.lastBuiltPercent;
        this.ledger.clear();
        this.ledger.addAll(source.ledger);
        setChanged();
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
        setChanged();
    }

    public int allowedTier() {
        return allowedTier;
    }

    /** Every recorded ledger line, oldest first -- see the field's own doc. Never {@code null}. */
    public List<LedgerEntry> ledger() {
        return ledger;
    }

    /** Appends one line, trimming the oldest entry if already at {@link #LEDGER_MAX_ENTRIES}. */
    public void appendLedgerEntry(long gameTime, String line) {
        ledger.add(new LedgerEntry(gameTime, line));
        while (ledger.size() > LEDGER_MAX_ENTRIES) {
            ledger.remove(0);
        }
        setChanged();
    }

    public void setAllowedTier(int allowedTier) {
        this.allowedTier = allowedTier;
        setChanged();
    }

    /** True while this box is bound to a Blueprint but not yet built and not mid-Reposition -- the same state {@link #attemptCompletion} checks before touching funding. */
    public boolean isFundable() {
        return placedBlueprintName != null && !blueprintPlaced && !repositionPending && !legacyFundingDataLost;
    }

    /** {@code true} if this box's saved funding data couldn't be read at all (stale/incompatible format) -- see {@link #legacyFundingDataLost}'s own doc. Fixed by an explicit "Change Blueprint" rebind. */
    public boolean legacyFundingDataLost() {
        return legacyFundingDataLost;
    }

    /** 0 if {@code item} isn't part of this box's requirements at all. */
    public int requiredAmount(Item item) {
        return required.getOrDefault(item, 0);
    }

    public int suppliedAmount(Item item) {
        return supplied.getOrDefault(item, 0);
    }

    /** {@code max(0, required - supplied)} for one item. */
    public int remaining(Item item) {
        return Math.max(0, requiredAmount(item) - suppliedAmount(item));
    }

    /** Every item still short, in the same order {@link #required} itself iterates (the Blueprint's own cell order) -- empty once fully funded. */
    public Map<Item, Integer> missingItems() {
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int remaining = remaining(entry.getKey());
            if (remaining > 0) {
                missing.put(entry.getKey(), remaining);
            }
        }
        return missing;
    }

    /**
     * Called once, right when a Blueprint is (first bound, or rebound to a different one via
     * "Change Blueprint") -- {@code cost} is the new Blueprint's own real material list, freshly
     * recomputed by the caller via {@code blueprint.BlueprintResources#computeFrom(record.cells())}
     * directly rather than trusted from {@code BlueprintRecord#resources()}'s own stored field (see
     * that method's own doc for why -- a real incident, 2026-10-22: several older/generated
     * Blueprint files on Production have an empty stored {@code resources} field, since they
     * predate it or were written by a one-off generator script that never computed it, which made
     * "Change Blueprint" show zero required resources for a structure that very obviously needs
     * real materials). Never recalculated afterward -- a later re-derivation (e.g. the Blueprint
     * file itself changing) doesn't retroactively change an in-progress box's own already-agreed
     * cost.
     *
     * <p><b>Refunds whatever was already supplied before resetting it</b> (added 2026-10-22,
     * explicit user spec: "the resources for that blueprint should be dumped into storage boxes
     * within the plot... any excess should be placed in a pile on top of the construction site
     * box... only the resources actually used so far" -- i.e. {@link #supplied}, not the old {@link
     * #required}) -- see {@link #refundSuppliedMaterials}. {@code oldBlueprintFullCost} is the
     * previously-bound Blueprint's own cost, freshly recomputed from ITS cells the same way --
     * **only used as a fallback** when {@link #supplied} is empty but this box genuinely has a real
     * completed structure standing ({@link #everCompleted}), i.e. a box whose own persisted funding
     * bookkeeping was lost (the same legacy-data-format incident {@link #legacyFundingDataLost}
     * documents) despite a real structure, with a real material cost, actually existing. Real
     * report, 2026-10-22: "None of the resources were refunded by changing the blueprint" on exactly
     * such a box -- its {@link #supplied} was empty (lost), so there was nothing to refund from at
     * all until this fallback existed. A no-op the first time a box is ever bound (nothing supplied,
     * no prior Blueprint to recompute a fallback from either).
     *
     * <p>Also resets {@link #everCompleted} -- real bug fixed 2026-10-22: that flag is a historical
     * "was the currently-bound Blueprint ever fully built" latch, but used to only ever get set
     * {@code true}, never reset on a rebind. A box that completed once, then got rebound to a
     * different/higher-Tier Blueprint (freshly unfunded), kept reporting {@code everCompleted() ==
     * true} for the new Blueprint it had never actually built -- misleading every reader of it
     * ({@code ConstructionBoxBlock}'s right-click flow, {@code ConstructionBoxMenu}'s client sync,
     * {@code api.Blueprynts#getConstructionBoxStatus}, and {@code BuildingLocatorItem}'s own
     * now-fixed "instantly completes on reposition" bug).
     */
    public void initializeRequirements(ServerLevel level, Map<Item, Integer> cost, Map<Item, Integer> oldBlueprintFullCost) {
        refundSuppliedMaterials(level, oldBlueprintFullCost);
        required = new LinkedHashMap<>(cost);
        supplied = new LinkedHashMap<>();
        everCompleted = false;
        legacyFundingDataLost = false;
        setChanged();
    }

    /**
     * Converts whichever of {@link #supplied} (real, tracked progress -- the normal case) or {@code
     * oldBlueprintFullCost} (the legacy-data fallback, see {@link #initializeRequirements}'s own
     * doc) applies into real stacks (chunked to each item's own max stack size), deposits as much
     * as possible into the owning plot's own boxes for free, and piles whatever doesn't fit directly
     * on top of this box. A no-op if neither source has anything to refund. If this box's plot
     * identity isn't known (see this class's own doc), everything piles on top instead of being
     * deposited -- same degrade-gracefully shape as {@link #pullFromPlotAndSync}.
     */
    private void refundSuppliedMaterials(ServerLevel level, Map<Item, Integer> oldBlueprintFullCost) {
        Map<Item, Integer> toRefund = supplied;
        if (toRefund.isEmpty() && everCompleted && oldBlueprintFullCost != null && !oldBlueprintFullCost.isEmpty()) {
            toRefund = oldBlueprintFullCost;
        }
        if (toRefund.isEmpty()) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : toRefund.entrySet()) {
            int remaining = entry.getValue();
            int maxStack = entry.getKey().getDefaultMaxStackSize();
            while (remaining > 0) {
                int take = Math.min(remaining, maxStack);
                stacks.add(new ItemStack(entry.getKey(), take));
                remaining -= take;
            }
        }
        List<ItemStack> leftover = (settlementCoreId != null && plotId != null)
                ? SettlemyntsPlotBoxBridge.depositIntoPlot(level, plotId, stacks)
                : stacks;
        if (!leftover.isEmpty()) {
            pileOnTop(level, leftover);
        }
    }

    /** Drops {@code stacks} directly above this box -- a Yconomics Loot Bag if that's loaded (same precedent as {@code construction.SiteTerrainOps#spawnRefundPile}'s own refund pile), a plain scattered {@code ItemEntity} otherwise. */
    private void pileOnTop(ServerLevel level, List<ItemStack> stacks) {
        BlockPos pos = getBlockPos();
        net.minecraft.world.phys.Vec3 pileCenter = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        boolean yconomics = net.neoforged.fml.ModList.get().isLoaded("yconomics");
        for (ItemStack stack : stacks) {
            if (yconomics) {
                com.github.cerealklla.blueprynts.bridge.YconomicsLootBridge.depositOrScatter(level, pileCenter, stack);
            } else {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, pileCenter.x, pileCenter.y, pileCenter.z, stack));
            }
        }
    }

    public boolean isFullyFunded() {
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (suppliedAmount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Tallies a matching deposit (capped at that item's own required amount -- excess is the caller's own responsibility to reject before calling this), attempts completion, and re-syncs the plot's Planned Inventory wishlist. */
    public void deposit(ServerLevel level, Item item, int count) {
        int reqAmount = required.getOrDefault(item, 0);
        if (reqAmount <= 0 || count <= 0) {
            return;
        }
        supplied.merge(item, count, Integer::sum);
        supplied.put(item, Math.min(reqAmount, supplied.get(item)));
        setChanged();
        onFundingChanged(level);
    }

    /**
     * Pastes the bound Blueprint up to whatever percentage its own funding currently supports.
     * Trivially 100% if no cost is configured at all (e.g. a Blueprint with no placeable-item cells
     * at all), so a zero-cost box still builds for free. Called after every funding change. No-op if
     * already fully built or nothing is bound.
     *
     * <p>Re-pastes into the *same* accumulated {@link #removalSnapshot} across repeated calls
     * (rather than a fresh one each time) -- {@code TerrainSnapshot#captureIfAbsent} only ever
     * records a position's *first* pre-construction state, so calling this again as funding grows
     * correctly extends the same snapshot instead of losing track of cells captured by an earlier,
     * lower-percentage call.
     *
     * @return {@code true} if this call actually completed the structure (reached 100%).
     */
    public boolean attemptCompletion(ServerLevel level) {
        if (!isFundable()) {
            return false;
        }
        int pct = fundedPercent();
        if (pct <= 0 || pct <= lastBuiltPercent) {
            return false;
        }

        // Guard against pasting over a Building Supply Box (this one or another) sitting somewhere
        // inside the incoming footprint -- real bug, 2026-09-30: moving the box into the build area
        // is intentional and supported, but a later building move/reposition landing on top of it
        // used to just silently destroy it. If *this* box itself is the one that had to move, its own
        // Java instance is now stale (a fresh one exists at the new position) -- finish this call
        // against that fresh instance instead of continuing with `this`.
        Set<BlockPos> footprint = RealBlueprintPlacement.computeFootprintPositions(anchorPos(), anchorFacing(), placedBlueprintName);
        Optional<BlockPos> selfMoved = ConstructionBoxOverlapGuard.relocateObstructions(
                level, footprint, SiteTerrainOps.siteFloorY(anchorPos()), buildableArea, constructionId);
        if (selfMoved.isPresent()) {
            return level.getBlockEntity(selfMoved.get()) instanceof ConstructionBoxBlockEntity moved && moved.attemptCompletion(level);
        }

        RealBlueprintPlacement.Result result = RealBlueprintPlacement.place(level, anchorPos(), anchorFacing(), placedBlueprintName, pct, removalSnapshot);
        if (result.error() != null) {
            return false;
        }
        removalSnapshot = result.snapshot();
        lastBuiltPercent = pct;
        if (pct >= 100) {
            blueprintPlaced = true;
            everCompleted = true;
        }
        setChanged();
        return pct >= 100;
    }

    /** Overall funding percentage across every required item, 0-100; trivially 100 if nothing is required at all. */
    public int fundedPercent() {
        long totalRequired = 0;
        long totalSupplied = 0;
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            totalRequired += entry.getValue();
            totalSupplied += suppliedAmount(entry.getKey());
        }
        if (totalRequired <= 0) {
            return 100;
        }
        return (int) Math.min(100, (100L * totalSupplied) / totalRequired);
    }

    /**
     * Re-checks completion, then pushes whatever's still missing to the owning plot's Planned
     * Inventory wishlist (a no-op if {@link #settlementCoreId}/{@link #plotId} aren't known -- see
     * this class's own doc) -- called after every funding change (a deposit, or a plot-stock pull)
     * so the wishlist always reflects the box's real current shortfall, not a stale one.
     */
    private void onFundingChanged(ServerLevel level) {
        attemptCompletion(level);
        if (settlementCoreId != null && plotId != null) {
            Map<Identifier, Integer> missing = new LinkedHashMap<>();
            missingItems().forEach((item, amount) -> missing.put(BuiltInRegistries.ITEM.getKey(item), amount));
            SettlemyntsPlotBoxBridge.syncMissingMaterials(level, settlementCoreId, plotId, missing);
        }
    }

    /**
     * Withdraws whatever it can of every still-missing item directly from the owning plot's own
     * boxes (free, no economy transaction), then re-syncs the remaining shortfall to Planned
     * Inventory -- called once right after binding (covers "using materials in the plot itself"
     * immediately at construction start) and again any time Settlemynts reports the plot's boxes
     * changed (mirrors {@code watchtower.WatchTowerBeaconBlockEntity}'s own fuel-pull trigger). A
     * silent no-op if this box's plot identity isn't known (see this class's own doc) or nothing is
     * currently missing.
     */
    public void pullFromPlotAndSync(ServerLevel level) {
        if (!isFundable() || settlementCoreId == null || plotId == null) {
            return;
        }
        for (Map.Entry<Item, Integer> entry : missingItems().entrySet()) {
            Item item = entry.getKey();
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            int got = SettlemyntsPlotBoxBridge.withdrawFromPlot(level, plotId, itemId, entry.getValue());
            if (got > 0) {
                supplied.merge(item, got, Integer::sum);
                setChanged();
            }
        }
        onFundingChanged(level);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        constructionId = input.read("ConstructionId", UUIDUtil.CODEC).orElse(null);
        zoneTypeId = input.read("ZoneTypeId", Identifier.CODEC).orElse(null);
        settlementCoreId = input.read("SettlementCoreId", UUIDUtil.CODEC).orElse(null);
        plotId = input.read("PlotId", UUIDUtil.CODEC).orElse(null);
        blueprintPlaced = input.getBooleanOr("BlueprintPlaced", false);
        placedBlueprintName = input.read("PlacedBlueprintName", Codec.STRING).orElse(null);
        buildableArea = input.read("BuildableArea", AREA_CODEC).orElse(null);
        plotArea = input.read("PlotArea", PlotArea.CODEC).orElse(null);
        removalSnapshot = input.read("RemovalSnapshot", TerrainSnapshot.CODEC).orElse(new TerrainSnapshot());
        everCompleted = input.getBooleanOr("EverCompleted", false);
        pendingAnchor = input.read("PendingAnchor", BlockPos.CODEC).orElse(null);
        pendingFacing = input.read("PendingFacing", Direction.CODEC).orElse(null);
        tier = input.getIntOr("Tier", 0);
        allowedTier = input.getIntOr("AllowedTier", 1);
        repositionPending = input.getBooleanOr("RepositionPending", false);
        Optional<Map<Identifier, Integer>> requiredRead = input.read("Required", ITEM_AMOUNTS_CODEC);
        Optional<Map<Identifier, Integer>> suppliedRead = input.read("Supplied", ITEM_AMOUNTS_CODEC);
        required = fromIdMap(requiredRead.orElse(Map.of()));
        supplied = fromIdMap(suppliedRead.orElse(Map.of()));
        // Real incident, 2026-10-22, caught on Production immediately after the funding-model
        // rework shipped: a box saved under the old int[]-indexed GenericResource format (e.g.
        // `Required: [100,60]`) fails to decode against the new Map<Identifier,Integer> codec --
        // logged as a "Serialization errors" warning, not a crash, but `read` falls back to the
        // same empty Optional a genuinely-never-bound box would also produce, which would otherwise
        // make `fundedPercent()` trivially report 100% (nothing required) the instant anything next
        // touched this box -- `attemptCompletion` would then paste the *entire* remaining structure
        // for free, silently discarding whatever real funding progress existed. A box can only ever
        // reach this `loadAdditional` with `placedBlueprintName != null` if `initializeRequirements`
        // already ran for it at least once, which always writes a real `Required`/`Supplied` tag --
        // so for such a box, either read coming back empty unambiguously means stale/incompatible
        // data, never "nothing was ever required." Freezes the box (via `isFundable()`) until an
        // explicit "Change Blueprint" rebind recomputes everything cleanly -- never silently guessed.
        legacyFundingDataLost = placedBlueprintName != null && (requiredRead.isEmpty() || suppliedRead.isEmpty());
        ledger.clear();
        ledger.addAll(input.read("Ledger", LedgerEntry.CODEC.listOf()).orElse(List.of()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("ConstructionId", UUIDUtil.CODEC, constructionId);
        output.storeNullable("ZoneTypeId", Identifier.CODEC, zoneTypeId);
        output.storeNullable("SettlementCoreId", UUIDUtil.CODEC, settlementCoreId);
        output.storeNullable("PlotId", UUIDUtil.CODEC, plotId);
        output.putBoolean("BlueprintPlaced", blueprintPlaced);
        if (placedBlueprintName != null) {
            output.putString("PlacedBlueprintName", placedBlueprintName);
        }
        output.storeNullable("BuildableArea", AREA_CODEC, buildableArea);
        output.storeNullable("PlotArea", PlotArea.CODEC, plotArea);
        output.store("RemovalSnapshot", TerrainSnapshot.CODEC, removalSnapshot);
        output.putBoolean("EverCompleted", everCompleted);
        output.storeNullable("PendingAnchor", BlockPos.CODEC, pendingAnchor);
        output.storeNullable("PendingFacing", Direction.CODEC, pendingFacing);
        output.putBoolean("RepositionPending", repositionPending);
        output.putInt("Tier", tier);
        output.putInt("AllowedTier", allowedTier);
        output.store("Required", ITEM_AMOUNTS_CODEC, toIdMap(required));
        output.store("Supplied", ITEM_AMOUNTS_CODEC, toIdMap(supplied));
        output.store("Ledger", LedgerEntry.CODEC.listOf(), ledger);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Building Supply Box");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ConstructionBoxMenu(ModMenus.CONSTRUCTION_BOX.get(), containerId, inventory, this);
    }

    private static Map<Item, Integer> fromIdMap(Map<Identifier, Integer> idMap) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        idMap.forEach((id, amount) -> {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) {
                result.put(item, amount);
            }
        });
        return result;
    }

    private static Map<Identifier, Integer> toIdMap(Map<Item, Integer> itemMap) {
        Map<Identifier, Integer> result = new LinkedHashMap<>();
        itemMap.forEach((item, amount) -> result.put(BuiltInRegistries.ITEM.getKey(item), amount));
        return result;
    }
}
