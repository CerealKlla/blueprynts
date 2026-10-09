package com.github.cerealklla.blueprynts.construction;

import com.mojang.serialization.MapCodec;

import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Building Supply Box (design-document.md's root "Construction Material Reservoir" concept) --
 * see {@link ConstructionBoxBlockEntity}'s own doc for the full picture and how this differs from
 * {@link ConstructionSiteBlock}. Indestructible and unmovable by normal play (per the design doc),
 * and never player-placed -- only ever created programmatically via {@code
 * api.Blueprynts#createConstructionBox}, so it deliberately has no registered {@code BlockItem}.
 *
 * <p>Right-clicking opens {@code ConstructionBoxPickerScreen} to choose a saved Blueprint --
 * selecting one places a new {@link ConstructionSiteBlock} adjacent to the box (same position/facing
 * relationship the box's own creation already established -- see {@code
 * api.Blueprynts#createConstructionBox}) and immediately loads that Blueprint into it. v1
 * simplification, not yet done: the picker lists every saved Blueprint, not filtered to ones the
 * plot can actually fit (that needs the plot's own geometry, which this block doesn't have -- see
 * decisions.md 2026-09-29). No funding/deposit interaction yet either -- that's separate follow-up
 * work.
 */
public class ConstructionBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ConstructionBoxBlock> CODEC = simpleCodec(ConstructionBoxBlock::new);

    public ConstructionBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConstructionBoxBlockEntity(pos, state);
    }

    // Real construction-duration gating (added 2026-09-29) needs a re-check purely on elapsed time,
    // not just on deposits -- a fully-funded-but-still-time-gated box would otherwise only ever
    // advance on its *next* deposit, which might never come once funding is already 100%.
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != com.github.cerealklla.blueprynts.registration.ModBlockEntities.CONSTRUCTION_BOX.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<ConstructionBoxBlockEntity>) (lvl, pos, st, box) -> {
            if (lvl instanceof ServerLevel serverLevel && lvl.getGameTime() % 20 == 0) {
                box.tickConstruction(serverLevel);
            }
        };
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof ConstructionBoxBlockEntity box)) {
            return InteractionResult.SUCCESS;
        }
        // A box that's bound to a Blueprint -- whether still funding it or already fully built --
        // opens the real Supplied/Needed Container/MenuProvider screen instead of the old plain-
        // payload Manage screen (2026-09-29's "Passive Construction & Funding" pass replaced
        // OpenConstructionBoxManagePayload/ConstructionBoxManageScreen with this).
        // Widened 2026-10-09 to also cover "mid-upgrade-funding" (torn down for a Tier upgrade,
        // funding a new Tier, no Locator involved at all -- see SelectConstructionBoxBlueprintPayload's
        // upgrade branch) via isFundable(), which already correctly excludes the real
        // mid-Reposition-Locator-pending case (repositionPending) that the old two-case check here
        // used to lean on `everCompleted` alone to rule out.
        if (box.placedBlueprintName() != null && (box.blueprintPlaced() || !box.everCompleted() || box.isFundable())) {
            serverPlayer.openMenu(box);
            return InteractionResult.SUCCESS_SERVER;
        }
        // Was built before, currently mid-Reposition (the player lost/used up their Building
        // Locator) -- re-grant one rather than reopening the funding screen, since the box stays
        // bound to the same Blueprint (already fully funded) until it's actually rebuilt.
        if (box.placedBlueprintName() != null) {
            player.getInventory().add(BuildingLocatorItem.grantFor(box, level.getGameTime()));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Building Locator re-granted."));
            return InteractionResult.SUCCESS_SERVER;
        }
        // Never bound yet -- open the Tier picker first (not the Blueprint picker directly), per
        // 2026-10-09's "allowedTier" rework: a plot's unlocked Tier cap (see
        // `zone.PlotRecord#tier` on the Settlemynts side) may already be above 1 even before a
        // first Blueprint is ever picked, so this must respect {@link ConstructionBoxBlockEntity#allowedTier()}
        // rather than always assuming Tier 1 the way the old "no plot-upgrade mechanism exists yet"
        // version of this method used to.
        openTierPicker(serverPlayer, pos, box.allowedTier());
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Also called by {@code BluepryntsMod}'s "Change Blueprint" handler (the button on {@code client.ConstructionBoxScreen}). */
    public static void openTierPicker(ServerPlayer player, BlockPos boxPos, int allowedTier) {
        PacketDistributor.sendToPlayer(player, new OpenConstructionBoxTierPickerPayload(boxPos, allowedTier));
    }

    /**
     * Builds the Blueprint-name picker payload for one specific, already-chosen Tier -- called by
     * {@code BluepryntsMod}'s {@code SelectConstructionBoxTierPayload} handler (the second step of
     * the Tier-picker -> Blueprint-picker flow). Same {@code settlemynts:private_residence} ->
     * {@code blueprynts:private_residence} alias fix this method's predecessor always needed --
     * never matches any real Blueprint's own {@code blueprint_type} by raw Identifier equality
     * otherwise, which would leave the picker silently empty for exactly that one type.
     */
    public static OpenConstructionBoxPickerPayload buildBlueprintPicker(BlockPos boxPos, net.minecraft.resources.Identifier zoneTypeId, int tier) {
        net.minecraft.resources.Identifier matchZoneTypeId = zoneTypeId;
        if (matchZoneTypeId != null && matchZoneTypeId.equals(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("settlemynts", "private_residence"))) {
            matchZoneTypeId = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    com.github.cerealklla.blueprynts.BluepryntsMod.MODID, "private_residence");
        }
        net.minecraft.resources.Identifier finalZoneTypeId = matchZoneTypeId;
        BlueprintStorage storage = BlueprintStorage.get();
        java.util.List<String> names = storage.listNames(record ->
                record.blueprintTypeId().equals(finalZoneTypeId) && record.tier() == tier);
        java.util.List<Long> fullMtimes = names.stream().map(name -> storage.previewAvailability(name).fullMtime()).toList();
        java.util.List<Long> smallMtimes = names.stream().map(name -> storage.previewAvailability(name).smallMtime()).toList();
        return new OpenConstructionBoxPickerPayload(boxPos, names, fullMtimes, smallMtimes);
    }
}
