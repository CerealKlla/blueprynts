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
        if (box.placedBlueprintName() != null && (box.blueprintPlaced() || !box.everCompleted())) {
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
        // Constrained to this box's own plot Zone Type, and -- since no plot-upgrade mechanism exists
        // yet, every plot is "brand new" -- the lowest Tier only (T1, since 2026-09-29's T0-T4 -> T1-T5
        // renumbering; this check itself was missed during that renumbering and left comparing against
        // the now-nonexistent tier 0 until caught here). Both per user request, 2026-09-29; the Tier
        // check should relax once a real plot-tier-progression feature exists.
        // settlemynts:private_residence -- the old native id this box's zoneTypeId can still be
        // carrying if it was bound before Settlemynts removed that built-in in favor of this mod's
        // own bridged blueprynts:private_residence (2026-10-01) -- never matches any real Blueprint's
        // own blueprint_type by raw Identifier equality, which otherwise leaves the picker silently
        // empty for exactly that one type. Settlemynts' own ZoneTypeRegistry carries the equivalent
        // alias for its side (wall rendering, Finalize); this is this mod's side of the same fix.
        net.minecraft.resources.Identifier boxZoneTypeId = box.zoneTypeId();
        if (boxZoneTypeId != null && boxZoneTypeId.equals(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("settlemynts", "private_residence"))) {
            boxZoneTypeId = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    com.github.cerealklla.blueprynts.BluepryntsMod.MODID, "private_residence");
        }
        net.minecraft.resources.Identifier matchZoneTypeId = boxZoneTypeId;
        BlueprintStorage storage = BlueprintStorage.get();
        java.util.List<String> names = storage.listNames(record ->
                record.blueprintTypeId().equals(matchZoneTypeId) && record.tier() == 1);
        java.util.List<Long> fullMtimes = names.stream().map(name -> storage.previewAvailability(name).fullMtime()).toList();
        java.util.List<Long> smallMtimes = names.stream().map(name -> storage.previewAvailability(name).smallMtime()).toList();
        PacketDistributor.sendToPlayer(serverPlayer, new OpenConstructionBoxPickerPayload(pos, names, fullMtimes, smallMtimes));
        return InteractionResult.SUCCESS_SERVER;
    }
}
