package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A real, placed, block-reserving marker for one Footprint Slab (design: "an actual, block
 * reserving structure, there is no order to them"). Placing one marks the exact column it sits in
 * as part of the Construction Site's footprint; there is no vertex/placement-order concept at all
 * -- the footprint IS the raw set of marked columns.
 *
 * <p>Indestructible by ordinary mining ({@code strength(-1.0F, ...)} in its registration, the same
 * unbreakable convention as {@code Blocks.BEDROCK}, plus an empty loot table) -- the only way to
 * take one back is right-clicking it for its own "Remove"
 * context menu ({@link #useWithoutItem}), which is unambiguous about which slab is being removed
 * (a correction from an earlier plan draft that wrongly routed removal through the Construction
 * Site's own UI, where "which slab" would have been ambiguous).
 *
 * <p>Placement validity (site exists/still DESIGNING/claimed by this player/column not already
 * marked) is checked in {@link #setPlacedBy}, AFTER the block already exists in the world --
 * simpler than a pre-emptive {@code canSurvive} check, since validating requires reading the
 * origin off the placing stack, which isn't available from a bare position/state check. An invalid
 * placement is rolled back immediately (block removed, item returned) rather than ever left
 * standing in a bad state.
 */
public class FootprintSlabBlock extends Block {

    public FootprintSlabBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof Player player)) {
            return;
        }
        BlockPos origin = stack.get(ModDataComponents.FOOTPRINT_SLAB_ORIGIN);
        String rejection = tryMarkColumn(serverLevel, origin, pos, player);
        if (rejection != null) {
            serverLevel.removeBlock(pos, false);
            ItemStack returned = new ItemStack(this);
            if (origin != null) {
                returned.set(ModDataComponents.FOOTPRINT_SLAB_ORIGIN, origin);
            }
            if (!player.getInventory().add(returned)) {
                player.drop(returned, false);
            }
            player.sendSystemMessage(Component.literal(rejection));
        }
    }

    /** Returns a rejection message, or {@code null} if the placement was accepted and the column marked. */
    private String tryMarkColumn(ServerLevel level, BlockPos origin, BlockPos placedAt, Player player) {
        if (origin == null || !(level.getBlockEntity(origin) instanceof ConstructionSiteBlockEntity site)) {
            return "This slab isn't bound to a Construction Site.";
        }
        if (site.phase() != ConstructionSitePhase.DESIGNING || !player.getUUID().equals(site.activePlayer())) {
            return "This Construction Site isn't in Design phase for you right now.";
        }
        Column column = new Column(placedAt.getX(), placedAt.getZ());
        if (!site.markColumn(column)) {
            return "This column is already marked.";
        }
        return null;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new OpenSlabRemoveScreenPayload(pos));
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Actually removes this slab and returns the item, re-tagged with {@code origin} so it can be
     * placed somewhere else -- called from the server-side {@code RemoveSlabPayload} handler, which
     * is responsible for locating the owning site and calling {@code
     * ConstructionSiteBlockEntity#unmarkColumn} itself (this method only handles the block/item).
     */
    public static void removeAndReturn(ServerLevel level, BlockPos pos, BlockPos origin, Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FootprintSlabBlock slab)) {
            return;
        }
        level.removeBlock(pos, false);
        ItemStack returned = new ItemStack(slab);
        returned.set(ModDataComponents.FOOTPRINT_SLAB_ORIGIN, origin);
        if (!player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
    }
}
