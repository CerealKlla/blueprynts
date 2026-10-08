package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.blueprynts.api.BuildableArea;
import com.github.cerealklla.blueprynts.api.PlotArea;
import com.github.cerealklla.blueprynts.blueprint.GenericResource;
import com.github.cerealklla.blueprynts.registration.ModBlockEntities;
import com.github.cerealklla.blueprynts.registration.ModMenus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 * <p><b>v1 scope, 2026-09-29</b>: only the identity piece -- {@link #constructionId()}, minted once
 * at creation and never reassigned -- so a box can be referenced from outside without needing its
 * chunk loaded first (see {@link ConstructionBoxIndex}). Required/Supplied material tracking,
 * status, and timing are deliberately NOT implemented yet (real follow-up work, not stubbed with
 * placeholder data) -- see the plan this shipped under.
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

    private UUID constructionId;
    private Identifier zoneTypeId;
    private boolean blueprintPlaced;
    private String placedBlueprintName;
    private BuildableArea buildableArea;
    private PlotArea plotArea;
    private TerrainSnapshot removalSnapshot = new TerrainSnapshot();

    // Funding tally (design-document.md's "Planned: Passive Construction & Funding," captured
    // 2026-09-29) -- both arrays indexed by GenericResource#ordinal(), a fixed-size-per-enum-value
    // shape (not a Map) so a fresh resource category needs no format change here, just a bigger
    // array. `required` is computed once by initializeRequirements() when a Blueprint is first bound
    // and never recalculated (config, not box state -- deliberately NOT re-fetched from Settlemynts
    // on every access, so a mid-game config retune doesn't retroactively change an in-progress box's
    // own already-agreed cost). `everCompleted` distinguishes "never yet built" (still funding) from
    // "was built, currently mid-Reposition" -- both look like blueprintPlaced() == false with a
    // non-null placedBlueprintName(), but need different useWithoutItem() behavior.
    private int[] required = new int[GenericResource.values().length];
    private int[] supplied = new int[GenericResource.values().length];
    private boolean everCompleted;

    // Suspends attemptCompletion/tickConstruction entirely between "Reposition Building" clearing
    // whatever was standing and the player actually placing a new anchor with the Locator -- a real
    // bug, 2026-09-29: without this, the once-a-second ticker's own attemptCompletion call resumed
    // building at the *old* anchor within about a second of the clear (funding/elapsed time hadn't
    // changed, so it just re-pasted right back), and then placing the new anchor left those stray
    // blocks behind, untracked, since a fresh removalSnapshot had already started accumulating for
    // the new location by then ("which ended up leaving parts behind").
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

    // Real construction-duration gating (added 2026-09-29, user correction: "it shouldn't hit 100%
    // immediately... it should be 100% funded immediately, not 100% constructed" -- funding and
    // construction progress are deliberately two separate numbers, not one). `minTimeTicks <= 0`
    // means no time gate at all (a box created before this existed, or with no Settlemynts config).
    //
    // Revised 2026-10-05, explicit user request: construction time only accumulates while the box
    // actually holds resources ("I'd like the auto build time to only elapse if there are resources
    // in the box"), not as flat wall-clock time since binding -- previously `timeBasedPercent` read
    // straight off `level.getGameTime() - boundAtGameTime`, so a box sitting completely empty still
    // ticked toward its minimum build time. `accumulatedTimeTicks` replaces that: it only advances
    // inside {@link #tickConstruction} (the once-a-second ticker), and only on a tick where {@link
    // #hasAnyResources()} is true -- an empty box's timer is effectively paused.
    private int minTimeTicks;
    private long accumulatedTimeTicks;
    // NPC auto-funding's own target pace (design-document.md Section 14a, Settlemynts' own doc) --
    // stored alongside minTimeTicks the same way, 2026-09-30, once a real NPC-delivery pipeline
    // (Settlemynts' construction.NpcAutoFundingTicker) needed to read it back after binding.
    private int maxTimeTicks;
    // The bound Blueprint's own Tier (1-5) -- known at bind time (BluepryntsMod's Blueprint-select
    // handler already reads it off the loaded BlueprintRecord) but never stored on the box itself
    // until Settlemynts' Guardhouse feature needed to read it back cross-mod (garrison size/gear
    // caps scale by Tier) -- same "accepted but discarded until a real consumer needed it" gap
    // maxTimeTicks itself used to have. 0 means unbound/unknown.
    private int tier;
    // Not persisted -- purely an optimization so the ticker's once-a-second re-check doesn't
    // re-run RealBlueprintPlacement when nothing has actually changed; worst case after a
    // reload it just redundantly re-pastes the same already-correct cells once, harmless.
    private int lastBuiltPercent = -1;

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
     * funding progress ({@link #required}/{@link #supplied}) or {@link #accumulatedTimeTicks} --
     * moving the pending build location doesn't refund or re-time it.
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
        this.blueprintPlaced = source.blueprintPlaced;
        this.placedBlueprintName = source.placedBlueprintName;
        this.buildableArea = source.buildableArea;
        this.plotArea = source.plotArea;
        this.removalSnapshot = source.removalSnapshot;
        this.required = source.required.clone();
        this.supplied = source.supplied.clone();
        this.everCompleted = source.everCompleted;
        this.repositionPending = source.repositionPending;
        this.pendingAnchor = source.pendingAnchor;
        this.pendingFacing = source.pendingFacing;
        this.minTimeTicks = source.minTimeTicks;
        this.maxTimeTicks = source.maxTimeTicks;
        this.tier = source.tier;
        this.accumulatedTimeTicks = source.accumulatedTimeTicks;
        this.lastBuiltPercent = source.lastBuiltPercent;
        setChanged();
    }

    public int maxTimeTicks() {
        return maxTimeTicks;
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
        setChanged();
    }

    /** True while this box is bound to a Blueprint but not yet built and not mid-Reposition -- the same state {@link #attemptCompletion} and NPC auto-funding both need to check before touching funding. */
    public boolean isFundable() {
        return placedBlueprintName != null && !blueprintPlaced && !repositionPending;
    }

    public int requiredAmount(GenericResource resource) {
        return required[resource.ordinal()];
    }

    public int suppliedAmount(GenericResource resource) {
        return supplied[resource.ordinal()];
    }

    /**
     * Called once, right when a Blueprint is first bound -- caches this box's own funding
     * requirements and construction duration (from Settlemynts, or all-zero/no-gate if that config
     * isn't available). Never recalculated afterward -- a later config retune doesn't retroactively
     * change an in-progress box's own already-agreed cost/duration.
     */
    public void initializeRequirements(FundingRequirements requirements) {
        Arrays.fill(required, 0);
        Arrays.fill(supplied, 0);
        for (Map.Entry<GenericResource, Integer> entry : requirements.costs().entrySet()) {
            required[entry.getKey().ordinal()] = entry.getValue();
        }
        this.minTimeTicks = requirements.minTimeTicks();
        this.maxTimeTicks = requirements.maxTimeTicks();
        this.accumulatedTimeTicks = 0;
        setChanged();
    }

    public boolean isFullyFunded() {
        for (int i = 0; i < required.length; i++) {
            if (supplied[i] < required[i]) {
                return false;
            }
        }
        return true;
    }

    /** Tallies a matching deposit (capped at that resource's own required amount -- excess is the caller's own responsibility to reject before calling this) and attempts completion. */
    public void deposit(ServerLevel level, GenericResource resource, int count) {
        int index = resource.ordinal();
        if (required[index] <= 0 || count <= 0) {
            return;
        }
        supplied[index] = Math.min(required[index], supplied[index] + count);
        setChanged();
        attemptCompletion(level);
    }

    /**
     * Pastes the bound Blueprint up to whatever percentage its own funding currently supports --
     * overall {@code totalSupplied/totalRequired} across every resource row (a placeholder
     * approximation: a resource sitting at 0% doesn't currently hold the *overall* percentage back
     * the way per-resource gating might, flagged as a known simplification, not silently exact).
     * Trivially 100% if no cost is configured at all (a Settlemynts-less server, or nothing set for
     * this Zone Type + Tier), so a zero-cost box still builds for free. Called after every deposit,
     * and once immediately after binding. No-op if already fully built or nothing is bound.
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
        // Deliberately two separate numbers, not one -- funding can reach 100% instantly (e.g. a
        // zero-cost box, or a player dumping in every resource at once), but real construction
        // progress is additionally capped by elapsed time since binding. User correction, 2026-09-29:
        // "it shouldn't hit 100% immediately... it should be 100% funded immediately, not 100%
        // constructed."
        int pct = Math.min(fundedPercent(), timeBasedPercent());
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

    /** Overall funding percentage across every required resource row, 0-100; trivially 100 if nothing is required at all. */
    private int fundedPercent() {
        long totalRequired = 0;
        long totalSupplied = 0;
        for (int i = 0; i < required.length; i++) {
            totalRequired += required[i];
            totalSupplied += supplied[i];
        }
        if (totalRequired <= 0) {
            return 100;
        }
        return (int) Math.min(100, (100L * totalSupplied) / totalRequired);
    }

    /** How much of {@link #minTimeTicks} has accumulated so far (see {@link #accumulatedTimeTicks}'s own doc), 0-100; trivially 100 if no time gate is configured ({@code minTimeTicks <= 0}). */
    private int timeBasedPercent() {
        if (minTimeTicks <= 0) {
            return 100;
        }
        return (int) Math.max(0, Math.min(100, (100L * accumulatedTimeTicks) / minTimeTicks));
    }

    /** {@code true} if any resource row currently holds a nonzero supplied amount -- the "are there resources in the box" check {@link #tickConstruction} gates time accumulation on. */
    private boolean hasAnyResources() {
        for (int amount : supplied) {
            if (amount > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-checks construction progress once a second (called from {@code ConstructionBoxBlock}'s
     * ticker) -- without this, a fully-funded-but-still-time-gated box would only ever advance on
     * its *next* deposit, which might never come. Also where {@link #accumulatedTimeTicks} actually
     * advances: only while {@link #isFundable()} and {@link #hasAnyResources()} are both true, so an
     * empty box's construction timer is paused, not just its funding. {@link #attemptCompletion}'s
     * own {@code lastBuiltPercent} check keeps a redundant call (nothing to build yet, or already
     * caught up) cheap.
     */
    public void tickConstruction(ServerLevel level) {
        if (!isFundable()) {
            return;
        }
        if (minTimeTicks > 0 && hasAnyResources()) {
            accumulatedTimeTicks += 20;
            setChanged();
        }
        attemptCompletion(level);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        constructionId = input.read("ConstructionId", UUIDUtil.CODEC).orElse(null);
        zoneTypeId = input.read("ZoneTypeId", Identifier.CODEC).orElse(null);
        blueprintPlaced = input.getBooleanOr("BlueprintPlaced", false);
        placedBlueprintName = input.read("PlacedBlueprintName", Codec.STRING).orElse(null);
        buildableArea = input.read("BuildableArea", AREA_CODEC).orElse(null);
        plotArea = input.read("PlotArea", PlotArea.CODEC).orElse(null);
        removalSnapshot = input.read("RemovalSnapshot", TerrainSnapshot.CODEC).orElse(new TerrainSnapshot());
        everCompleted = input.getBooleanOr("EverCompleted", false);
        pendingAnchor = input.read("PendingAnchor", BlockPos.CODEC).orElse(null);
        pendingFacing = input.read("PendingFacing", Direction.CODEC).orElse(null);
        minTimeTicks = input.getIntOr("MinTimeTicks", 0);
        maxTimeTicks = input.getIntOr("MaxTimeTicks", 0);
        tier = input.getIntOr("Tier", 0);
        accumulatedTimeTicks = input.read("AccumulatedTimeTicks", Codec.LONG).orElse(0L);
        repositionPending = input.getBooleanOr("RepositionPending", false);
        required = toArray(input.read("Required", Codec.INT.listOf()).orElse(List.of()), required.length);
        supplied = toArray(input.read("Supplied", Codec.INT.listOf()).orElse(List.of()), supplied.length);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("ConstructionId", UUIDUtil.CODEC, constructionId);
        output.storeNullable("ZoneTypeId", Identifier.CODEC, zoneTypeId);
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
        output.putInt("MinTimeTicks", minTimeTicks);
        output.putInt("MaxTimeTicks", maxTimeTicks);
        output.putInt("Tier", tier);
        output.store("AccumulatedTimeTicks", Codec.LONG, accumulatedTimeTicks);
        output.store("Required", Codec.INT.listOf(), toList(required));
        output.store("Supplied", Codec.INT.listOf(), toList(supplied));
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Building Supply Box");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ConstructionBoxMenu(ModMenus.CONSTRUCTION_BOX.get(), containerId, inventory, this);
    }

    private static int[] toArray(List<Integer> list, int expectedSize) {
        int[] array = new int[expectedSize];
        for (int i = 0; i < Math.min(expectedSize, list.size()); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static List<Integer> toList(int[] array) {
        List<Integer> list = new ArrayList<>(array.length);
        for (int value : array) {
            list.add(value);
        }
        return list;
    }
}
