package com.github.cerealklla.blueprynts.construction;

import java.util.UUID;

import com.github.cerealklla.blueprynts.api.PlotArea;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Reposition Supply Box" hand-off item (user request, 2026-09-29) -- granted by a Building Supply
 * Box's "Reposition Supply Box" button, which removes the old block and stashes its full state in
 * {@link PendingSupplyBoxRelocation} keyed by its own {@code constructionId} (the only thing this
 * item itself carries). Right-clicking the ground places a fresh box there.
 *
 * <p><b>Rewritten as a real {@link BlockItem}, same day</b> -- an earlier version hand-rolled its own
 * {@code Item#useOn} (manual {@code setBlock} + manual stack consumption), which two live tests found
 * genuinely broken (no block ever appeared despite reporting success) and which needed workarounds to
 * force item removal in Creative. User's own correction: "why are you even trying to implement this
 * in a similar way to Creative mode? Just give me a stack of 1 and let normal minecraft remove it
 * when it is placed?" Vanilla's own well-tested {@code BlockItem#place} pipeline now does the actual
 * world mutation and consumption (the standard {@code ItemStack#consume}, including the ordinary
 * "doesn't consume in Creative" convention every other placeable block follows) -- only the parts
 * that genuinely need to be custom are overridden: {@link #getPlacementState} supplies the *original*
 * facing (never derived from the player, and rejects the placement outright -- returning {@code null}
 * fails it cleanly -- if the Locator is already used or the target is outside the plot's real {@link
 * PlotArea}), and {@link #updateCustomBlockEntityTag} restores the rest of the box's state once
 * vanilla has actually placed it.
 */
public class SupplyBoxLocatorItem extends BlockItem {

    public SupplyBoxLocatorItem(Properties properties) {
        super(ModBlocks.CONSTRUCTION_BOX.get(), properties);
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        UUID constructionId = context.getItemInHand().get(ModDataComponents.SUPPLY_BOX_LOCATOR_BOX_ID.get());
        if (constructionId == null) {
            warnServerSide(context, "This Supply Box Locator isn't bound to a box.");
            return null;
        }
        Direction facing = PendingSupplyBoxRelocation.peekFacing(constructionId);
        if (facing == null) {
            warnServerSide(context, "This Supply Box Locator has already been used.");
            return null;
        }
        // "The supply box can go anywhere within the plot, not only the 15x15 or 50x50 valid area
        // for the building" -- user request, 2026-09-29, then "but a plot is a polygon, not a
        // rectangle" -- checked against the real per-cell PlotArea, never a bounding box.
        PlotArea plotArea = PendingSupplyBoxRelocation.peekPlotArea(constructionId);
        BlockPos pos = context.getClickedPos();
        if (plotArea != null && !plotArea.contains(pos.getX(), pos.getZ())) {
            warnServerSide(context, "That's outside the plot -- the Building Supply Box must stay within it.");
            return null;
        }
        return getBlock().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        UUID constructionId = stack.get(ModDataComponents.SUPPLY_BOX_LOCATOR_BOX_ID.get());
        if (constructionId != null && level instanceof ServerLevel serverLevel) {
            restoreAt(serverLevel, constructionId, pos, state.getValue(HorizontalDirectionalBlock.FACING));
            if (player != null) {
                player.sendSystemMessage(Component.literal("Building Supply Box repositioned."));
            }
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    /** Shared by both a real player placement ({@link #updateCustomBlockEntityTag}) and an auto-cancel ({@link #cancelIfHeld}) -- places the block and restores the box's full stashed state onto it. */
    private static void restoreAt(ServerLevel level, UUID constructionId, BlockPos pos, Direction facing) {
        level.setBlock(pos, ModBlocks.CONSTRUCTION_BOX.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof ConstructionBoxBlockEntity box) {
            PendingSupplyBoxRelocation.applyTo(box, constructionId);
            ConstructionBoxIndex.get(level.getServer()).put(constructionId, GlobalPos.of(level.dimension(), pos));
        }
    }

    /**
     * Auto-cancels a held, bound, not-yet-placed Supply Box Locator once the player leaves the plot
     * it belongs to -- walking away, dying, or logging out while holding one used to just silently
     * strand the box (removed from the world with nothing left to restore it) -- design doc's own
     * intent ("dying... or logging out and back in, also cancels it and removes the item"), never
     * actually built until now. Restores the box at the exact position it was removed from, using the
     * same {@link #restoreAt} a real placement uses, then clears the stack from {@code clearStack}
     * (the caller's own inventory/hand reference) so it never also survives as a dropped item.
     *
     * @param force skips the bounds check -- used by death/logout cancellation, which must cancel
     *              unconditionally rather than only once the player happens to be outside the plot.
     */
    public static void cancelIfHeld(ServerLevel level, Player player, ItemStack stack, boolean force, java.util.function.Consumer<ItemStack> clearStack) {
        UUID constructionId = stack.get(ModDataComponents.SUPPLY_BOX_LOCATOR_BOX_ID.get());
        if (constructionId == null) {
            return;
        }
        if (!force && !LocatorGracePeriod.hasElapsed(level, stack)) {
            return;
        }
        Direction facing = PendingSupplyBoxRelocation.peekFacing(constructionId);
        BlockPos originalPos = PendingSupplyBoxRelocation.peekOriginalPos(constructionId);
        if (facing == null || originalPos == null) {
            return; // Already used, or nothing pending -- not this item's job to touch.
        }
        if (!force) {
            PlotArea plotArea = PendingSupplyBoxRelocation.peekPlotArea(constructionId);
            if (plotArea == null || plotArea.contains(player.blockPosition().getX(), player.blockPosition().getZ())) {
                return; // Unbound (no plot to enforce) or still inside it -- nothing to cancel yet.
            }
        }
        restoreAt(level, constructionId, originalPos, facing);
        clearStack.accept(stack);
        player.sendSystemMessage(Component.literal("Reposition canceled -- the Building Supply Box has been returned to its original spot."));
    }

    private static void warnServerSide(BlockPlaceContext context, String message) {
        if (context.getLevel() instanceof ServerLevel && context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.literal(message));
        }
    }

    /** A fresh, bound Supply Box Locator stack for {@code constructionId} -- the box itself must already have been removed and stashed in {@link PendingSupplyBoxRelocation} before this is granted. */
    public static ItemStack grantFor(UUID constructionId, long gameTime) {
        ItemStack stack = new ItemStack(ModBlocks.SUPPLY_BOX_LOCATOR_ITEM.get());
        stack.set(ModDataComponents.SUPPLY_BOX_LOCATOR_BOX_ID.get(), constructionId);
        stack.set(ModDataComponents.LOCATOR_GRANTED_AT_GAME_TIME.get(), gameTime);
        return stack;
    }
}
