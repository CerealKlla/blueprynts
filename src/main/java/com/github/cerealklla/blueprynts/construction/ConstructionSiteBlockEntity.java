package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.blueprynts.blueprint.BlueprintCell;
import com.github.cerealklla.blueprynts.blueprint.BlueprintRecord;
import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;
import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.blueprint.SlabBudget;
import com.github.cerealklla.blueprynts.blueprint.TierSpec;
import com.github.cerealklla.blueprynts.registration.ModBlockEntities;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Construction Site's live state: pending Size/Tier/Blueprint Type, the current {@link
 * ConstructionSitePhase}, which player currently has it claimed, every marked column, and the
 * {@link TerrainSnapshot} needed to restore the site exactly (used by both a deliberate clear and
 * the walk-away/anti-farming auto-clear -- see {@code ConstructionSiteBlock#serverTick}).
 */
public class ConstructionSiteBlockEntity extends BlockEntity {

    private SizeClass sizeClass = SizeClass.SMALL;
    private int tier = 0;
    private Identifier blueprintTypeId;
    private ConstructionSitePhase phase = ConstructionSitePhase.IDLE;
    private UUID activePlayer;
    private final Set<Column> markedColumns = new HashSet<>();
    private TerrainSnapshot snapshot = new TerrainSnapshot();

    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_SITE.get(), pos, state);
    }

    public SizeClass sizeClass() {
        return sizeClass;
    }

    public int tier() {
        return tier;
    }

    public Identifier blueprintTypeId() {
        return blueprintTypeId;
    }

    public ConstructionSitePhase phase() {
        return phase;
    }

    public UUID activePlayer() {
        return activePlayer;
    }

    public Set<Column> markedColumns() {
        return Set.copyOf(markedColumns);
    }

    /** The direction a build extends away from the placer -- opposite the block's own FACING (which points toward whoever placed it, furnace convention). */
    private Direction intoSite() {
        return getBlockState().getValue(ConstructionSiteBlock.FACING).getOpposite();
    }

    /** The current outer clearing rectangle, recomputed fresh from the site's own position/facing/Size -- valid throughout DESIGNING and CONSTRUCTING alike, not just once slabs exist. */
    public SiteTerrainOps.OuterArea outerArea() {
        return SiteTerrainOps.computeOuterArea(getBlockPos(), intoSite(), sizeClass.outerDimension());
    }

    /** {@code true} if unclaimed, or already claimed by {@code player}. */
    public boolean claim(Player player) {
        if (activePlayer == null) {
            activePlayer = player.getUUID();
            setChanged();
            return true;
        }
        return activePlayer.equals(player.getUUID());
    }

    /** Only valid while {@link ConstructionSitePhase#IDLE}. */
    public void setPendingOptions(SizeClass sizeClass, int tier, Identifier blueprintTypeId) {
        if (phase != ConstructionSitePhase.IDLE) {
            return;
        }
        this.sizeClass = sizeClass;
        this.tier = tier;
        this.blueprintTypeId = blueprintTypeId;
        setChanged();
    }

    /** Marks a column as part of the footprint; false if already marked or not currently designing. */
    boolean markColumn(Column column) {
        if (phase != ConstructionSitePhase.DESIGNING || markedColumns.contains(column)) {
            return false;
        }
        markedColumns.add(column);
        setChanged();
        return true;
    }

    public void unmarkColumn(Column column) {
        if (markedColumns.remove(column)) {
            setChanged();
        }
    }

    /** @return an error message, or {@code null} on success. */
    public String beginDesign(ServerLevel level, Player player) {
        if (!claim(player)) {
            return "Someone else is already using this Construction Site.";
        }
        if (phase != ConstructionSitePhase.IDLE) {
            return "This Construction Site already has a design in progress.";
        }
        if (blueprintTypeId == null) {
            return "Pick a Blueprint Type first.";
        }
        BlueprintType type = BlueprintTypeRegistry.get(blueprintTypeId).orElse(new BlueprintType(blueprintTypeId, blueprintTypeId.toString(), 1.0));

        SiteTerrainOps.OuterArea area = SiteTerrainOps.computeOuterArea(getBlockPos(), intoSite(), sizeClass.outerDimension());
        snapshot = new TerrainSnapshot();
        SiteTerrainOps.levelClearingArea(level, area, snapshot);

        markedColumns.clear();
        phase = ConstructionSitePhase.DESIGNING;
        ActiveSiteRegistry.register(activePlayer, net.minecraft.core.GlobalPos.of(level.dimension(), getBlockPos()));
        setChanged();

        int budget = SlabBudget.compute(sizeClass, TierSpec.fromOrdinal(tier), type);
        ItemStack stack = new ItemStack(ModBlocks.FOOTPRINT_SLAB.get(), budget);
        stack.set(ModDataComponents.FOOTPRINT_SLAB_ORIGIN, getBlockPos());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return null;
    }

    /** @return an error message, or {@code null} on success. */
    public String beginConstruction(ServerLevel level, Player player) {
        if (phase != ConstructionSitePhase.DESIGNING || activePlayer == null || !activePlayer.equals(player.getUUID())) {
            return "This Construction Site isn't in Design phase for you right now.";
        }
        if (markedColumns.isEmpty()) {
            return "Mark at least one column with a Footprint Slab first.";
        }

        int groundY = getBlockPos().getY();
        for (Column column : markedColumns) {
            // Slabs are placed by right-clicking the leveled floor, so they stand ON it at
            // groundY + 1, not embedded in it at groundY -- looking at groundY was a real playtest
            // bug, Begin Construction silently leaving every placed slab behind.
            BlockPos slabPos = new BlockPos(column.x(), groundY + 1, column.z());
            if (level.getBlockState(slabPos).getBlock() instanceof FootprintSlabBlock) {
                level.removeBlock(slabPos, false);
            }
        }
        clearRemainingSlabItems(player);

        Set<Column> ring = SiteTerrainOps.computeBoundaryRing(markedColumns);
        TierSpec spec = TierSpec.fromOrdinal(tier);
        GhostConstructionWallEntity.raise(level, getBlockPos(), ring, groundY, spec.heightAboveGround(), activePlayer);
        SiteTerrainOps.applyBelowGroundWool(level, markedColumns, groundY, spec.depthBelowGround(), snapshot);

        phase = ConstructionSitePhase.CONSTRUCTING;
        setChanged();
        return null;
    }

    private void clearRemainingSlabItems(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (matchesThisSite(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private boolean matchesThisSite(ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) || !(blockItem.getBlock() instanceof FootprintSlabBlock)) {
            return false;
        }
        BlockPos origin = stack.get(ModDataComponents.FOOTPRINT_SLAB_ORIGIN);
        return getBlockPos().equals(origin);
    }

    /**
     * Restores every captured terrain change and resets to IDLE -- used for a deliberate clear, the
     * walk-away auto-clear, and breaking the site block while active. Also reclaims any Footprint
     * Slabs still sitting in the active player's inventory (if they're online) -- a real playtest
     * gap: walking away used to clear the leveled terrain but left the granted slab budget in the
     * player's inventory for free, undermining the whole point of it being a budget.
     */
    public void restoreAndReset(ServerLevel level) {
        int groundY = getBlockPos().getY();
        for (Column column : markedColumns) {
            // Same groundY-vs-groundY+1 fix as beginConstruction -- see its own comment.
            BlockPos slabPos = new BlockPos(column.x(), groundY + 1, column.z());
            if (level.getBlockState(slabPos).getBlock() instanceof FootprintSlabBlock) {
                level.removeBlock(slabPos, false);
            }
        }
        if (activePlayer != null && level.getServer().getPlayerList().getPlayer(activePlayer) instanceof Player onlinePlayer) {
            clearRemainingSlabItems(onlinePlayer);
        }
        SiteTerrainOps.restore(level, snapshot);
        GhostConstructionWallEntity.discardAll(level, getBlockPos());
        markedColumns.clear();
        snapshot = new TerrainSnapshot();
        phase = ConstructionSitePhase.IDLE;
        if (activePlayer != null) {
            ActiveSiteRegistry.unregister(activePlayer);
        }
        activePlayer = null;
        setChanged();
    }

    /** @return an error message, or {@code null} on success. */
    public String saveBlueprint(ServerLevel level, String name, Player player) {
        if (phase != ConstructionSitePhase.CONSTRUCTING || activePlayer == null || !activePlayer.equals(player.getUUID())) {
            return "Nothing under construction here to save yet.";
        }
        if (name == null || name.isBlank()) {
            return "Give the Blueprint a name first.";
        }

        int groundY = getBlockPos().getY();
        TierSpec spec = TierSpec.fromOrdinal(tier);
        Direction intoSite = intoSite();
        List<Column> relativeColumns = new ArrayList<>();
        List<BlueprintCell> cells = new ArrayList<>();
        BlockState untouchedWool = net.minecraft.world.level.block.Blocks.BROWN_WOOL.defaultBlockState();

        for (Column column : markedColumns) {
            Column relative = SiteTerrainOps.toRelativeColumn(getBlockPos(), intoSite, column.x(), column.z());
            relativeColumns.add(relative);
            for (int relY = -spec.depthBelowGround(); relY <= spec.heightAboveGround(); relY++) {
                BlockPos worldPos = new BlockPos(column.x(), groundY + relY, column.z());
                BlockState state = level.getBlockState(worldPos);
                if (relY < 0 && state.equals(untouchedWool)) {
                    cells.add(new BlueprintCell(relative.x(), relY, relative.z(), Optional.empty()));
                } else {
                    cells.add(new BlueprintCell(relative.x(), relY, relative.z(), Optional.of(state)));
                }
            }
        }

        BlueprintRecord record = new BlueprintRecord(name, blueprintTypeId, relativeColumns, spec.heightAboveGround(), spec.depthBelowGround(), cells);
        BlueprintStorage.get(level).save(record);
        return null;
    }

    /** @return an error message, or {@code null} on success. */
    public String loadBlueprint(ServerLevel level, String name, Player player) {
        Optional<BlueprintRecord> found = BlueprintStorage.get(level).load(name);
        if (found.isEmpty()) {
            return "No Blueprint named '" + name + "'.";
        }
        if (phase != ConstructionSitePhase.IDLE) {
            restoreAndReset(level);
        }
        if (!claim(player)) {
            return "Someone else is already using this Construction Site.";
        }

        BlueprintRecord record = found.get();
        Direction intoSite = intoSite();
        int groundY = getBlockPos().getY();

        snapshot = new TerrainSnapshot();
        SiteTerrainOps.OuterArea area = SiteTerrainOps.computeOuterAreaForFootprint(getBlockPos(), intoSite, record.relativeColumns());
        SiteTerrainOps.levelClearingArea(level, area, snapshot);

        markedColumns.clear();
        for (Column relative : record.relativeColumns()) {
            markedColumns.add(SiteTerrainOps.toWorldColumn(getBlockPos(), intoSite, relative));
        }

        for (BlueprintCell cell : record.cells()) {
            if (cell.isPreExisting()) {
                continue;
            }
            Column world = SiteTerrainOps.toWorldColumn(getBlockPos(), intoSite, new Column(cell.relX(), cell.relZ()));
            BlockPos worldPos = new BlockPos(world.x(), groundY + cell.relY(), world.z());
            snapshot.captureIfAbsent(worldPos, level.getBlockState(worldPos));
            level.setBlock(worldPos, cell.state().orElseThrow(), 3);
        }

        blueprintTypeId = record.blueprintTypeId();
        Set<Column> ring = SiteTerrainOps.computeBoundaryRing(markedColumns);
        GhostConstructionWallEntity.raise(level, getBlockPos(), ring, groundY, record.height(), activePlayer);

        phase = ConstructionSitePhase.CONSTRUCTING;
        ActiveSiteRegistry.register(activePlayer, net.minecraft.core.GlobalPos.of(level.dimension(), getBlockPos()));
        setChanged();
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        sizeClass = SizeClass.valueOf(input.getStringOr("SizeClass", SizeClass.SMALL.name()));
        tier = input.getIntOr("Tier", 0);
        blueprintTypeId = input.read("BlueprintTypeId", Identifier.CODEC).orElse(null);
        phase = ConstructionSitePhase.valueOf(input.getStringOr("Phase", ConstructionSitePhase.IDLE.name()));
        activePlayer = input.read("ActivePlayer", UUIDUtil.CODEC).orElse(null);
        markedColumns.clear();
        markedColumns.addAll(input.read("MarkedColumns", Column.CODEC.listOf()).orElse(List.of()));
        snapshot = input.read("Snapshot", TerrainSnapshot.CODEC).orElse(new TerrainSnapshot());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("SizeClass", sizeClass.name());
        output.putInt("Tier", tier);
        output.storeNullable("BlueprintTypeId", Identifier.CODEC, blueprintTypeId);
        output.putString("Phase", phase.name());
        output.storeNullable("ActivePlayer", UUIDUtil.CODEC, activePlayer);
        output.store("MarkedColumns", Column.CODEC.listOf(), List.copyOf(markedColumns));
        output.store("Snapshot", TerrainSnapshot.CODEC, snapshot);
    }
}
