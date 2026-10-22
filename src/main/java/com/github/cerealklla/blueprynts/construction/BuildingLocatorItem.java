package com.github.cerealklla.blueprynts.construction;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.blueprynts.api.Blueprynts;
import com.github.cerealklla.blueprynts.blueprint.BlueprintRecord;
import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;
import com.github.cerealklla.blueprynts.registration.ModBlocks;
import com.github.cerealklla.blueprynts.registration.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * "Reposition Building" hand-off item (user request, 2026-09-29) -- granted by a Building Supply
 * Box's "Reposition Building" button once its structure is removed, bound to that box's own {@link
 * ModDataComponents#BUILDING_LOCATOR_BOX_ID}. Right-clicking the ground re-places the SAME Blueprint
 * (the box's own {@code placedBlueprintName} never changes -- this moves a building, it doesn't let
 * you pick a different one) at the clicked position, in the box's own fixed facing (the Road Stake
 * already determined this; the player's own rotation is irrelevant), if and only if the full
 * footprint fits inside the box's own {@link com.github.cerealklla.blueprynts.api.BuildableArea}
 * (a plain rectangle -- see that record's own doc for why, and {@code ConstructionBoxBlockEntity}'s
 * doc for the required-buffer note). See {@code BuildingLocatorTicker} for the live minimap preview
 * this item drives while held.
 */
public class BuildingLocatorItem extends Item {

    public BuildingLocatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        UUID boxId = stack.get(ModDataComponents.BUILDING_LOCATOR_BOX_ID.get());
        if (boxId == null) {
            player.sendSystemMessage(Component.literal("This Building Locator isn't bound to a box."));
            return InteractionResult.FAIL;
        }
        Optional<BlockPos> boxPos = Blueprynts.getConstructionBoxPos(serverLevel, boxId);
        if (boxPos.isEmpty() || !(serverLevel.getBlockEntity(boxPos.get()) instanceof ConstructionBoxBlockEntity box)) {
            player.sendSystemMessage(Component.literal("This Building Locator's Box is gone or not loaded."));
            return InteractionResult.FAIL;
        }
        if (box.blueprintPlaced() || box.placedBlueprintName() == null) {
            player.sendSystemMessage(Component.literal("There's nothing left to reposition here."));
            return InteractionResult.FAIL;
        }

        Direction facing = serverLevel.getBlockState(boxPos.get()).getValue(HorizontalDirectionalBlock.FACING);
        Direction intoSite = facing.getOpposite();
        BlockPos anchor = context.getClickedPos().above();

        int size = resolveSize(box.placedBlueprintName());
        BuildingFootprintCheck check = BuildingFootprintCheck.evaluate(box.buildableArea(), anchor, intoSite, size);
        if (!check.fits()) {
            player.sendSystemMessage(Component.literal("That location doesn't fit -- it extends outside the plot's buildable area."));
            return InteractionResult.FAIL;
        }

        GhostBuildingPreviewEntity.clear(serverLevel, player.getUUID(), "zone");
        GhostBuildingPreviewEntity.clear(serverLevel, player.getUUID(), "footprint");

        // Move the pending anchor to the clicked spot, then let attemptCompletion re-paste up to
        // whatever percentage is currently funded -- covers "already fully funded, paste the whole
        // thing right back" and "still partially funded, paste only that much" with the exact same
        // call, including its own overlap-guard/self-move handling (identical to what used to be
        // hand-duplicated here).
        //
        // Real bug fixed 2026-10-22: this used to branch on box.everCompleted() and, in the true
        // branch, paste unconditionally at 100% via RealBlueprintPlacement's non-percentage overload
        // -- but everCompleted() is a one-way historical latch, never reset when a box gets rebound
        // to a different (or higher-Tier) Blueprint via "Change Blueprint." A box that had completed
        // once, then got rebound and was only partially funded again, still read everCompleted() as
        // true and instantly pasted the entire new Blueprint regardless of actual funding --
        // "partially built, reposition instantly builds it." attemptCompletion already computes the
        // real current percentage itself, so there's no need to special-case "fully funded" at all.
        box.setPendingAnchor(anchor, facing);
        box.setRepositionPending(false);
        boolean completed = box.attemptCompletion(serverLevel);
        player.sendSystemMessage(Component.literal(completed
                ? "Building placed."
                : "Pending build location set -- builds automatically as funding allows."));
        stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** A fresh, bound Building Locator stack for {@code box} -- used both by "Reposition Building" and the empty-box re-grant path. */
    public static ItemStack grantFor(ConstructionBoxBlockEntity box, long gameTime) {
        ItemStack stack = new ItemStack(ModBlocks.BUILDING_LOCATOR_ITEM.get());
        stack.set(ModDataComponents.BUILDING_LOCATOR_BOX_ID.get(), box.constructionId());
        stack.set(ModDataComponents.LOCATOR_GRANTED_AT_GAME_TIME.get(), gameTime);
        return stack;
    }

    /**
     * Auto-cancels a held, bound Building Locator once the player leaves the plot it belongs to (or
     * dies/logs out holding it) -- design doc's own intent, never actually built until now.
     *
     * <p><b>Real bug fixed 2026-09-30</b>: the doc here used to claim no world state needs restoring,
     * reasoning that "Reposition Building" never removes the box itself -- true, but it overlooked
     * that {@code clearPartialConstruction} (called the instant "Reposition Building" is clicked,
     * before this Locator is even granted) already tears down whatever was actually standing --
     * fully built or only partially -- restoring the real pre-construction terrain right away, not
     * lazily. Walking away used to just discard the stray item and leave {@code repositionPending}
     * stuck {@code true} forever, with nothing ever re-pasting the building -- permanently stuck,
     * silently. {@link ConstructionBoxBlockEntity#anchorPos()}/{@code anchorFacing()} are never
     * touched by "Reposition Building" itself (only a successfully-placed new anchor moves them), so
     * the fix is simply to un-suspend the ticker and paste again right at that same still-current
     * anchor -- {@code attemptCompletion} naturally recomputes the same funded/time-based percentage
     * as before and re-pastes up to it, which **is** "restored to its previous location," not a
     * separate restore path. No-op if {@code stack} isn't a bound Locator, or the box it's bound to no
     * longer exists.
     *
     * @param force skips the bounds check -- used by death/logout cancellation, which must cancel
     *              unconditionally rather than only once the player happens to be outside the plot.
     */
    public static void cancelIfHeld(ServerLevel level, Player player, ItemStack stack, boolean force, java.util.function.Consumer<ItemStack> clearStack) {
        UUID boxId = stack.get(ModDataComponents.BUILDING_LOCATOR_BOX_ID.get());
        if (boxId == null) {
            return;
        }
        if (!force && !LocatorGracePeriod.hasElapsed(level, stack)) {
            return;
        }
        Optional<BlockPos> boxPos = Blueprynts.getConstructionBoxPos(level, boxId);
        if (boxPos.isEmpty() || !(level.getBlockEntity(boxPos.get()) instanceof ConstructionBoxBlockEntity box)) {
            return;
        }
        if (!force) {
            com.github.cerealklla.blueprynts.api.PlotArea plotArea = box.plotArea();
            if (plotArea == null || plotArea.contains(player.blockPosition().getX(), player.blockPosition().getZ())) {
                return; // Unbound (no plot to enforce) or still inside it -- nothing to cancel yet.
            }
        }
        clearStack.accept(stack);
        if (box.repositionPending()) {
            box.setRepositionPending(false);
            box.attemptCompletion(level);
        }
        player.sendSystemMessage(Component.literal(
                "Reposition canceled -- you left the plot. The building has been restored to its previous location."));
    }

    /** {@code record.sizeClass()} is {@code null} for a file saved before that field existed -- falls back to Small, the smallest/safest guess. */
    public static int resolveSize(String blueprintName) {
        Optional<BlueprintRecord> record = BlueprintStorage.get().load(blueprintName);
        if (record.isPresent() && record.get().sizeClass() != null) {
            return record.get().sizeClass().outerDimension();
        }
        return SizeClass.SMALL.outerDimension();
    }
}
