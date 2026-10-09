package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.blueprint.TierSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Enforces "the player cannot break/place blocks outside the footprint's build volume" once a
 * Construction Site is CONSTRUCTING (design: the ghost-glass wall is purely visual/walk-through,
 * like every other ghost marker in the suite -- this is the real protection check). No restriction
 * at all while DESIGNING (slabs may be placed freely anywhere in the leveled outer area) or IDLE.
 */
public final class ConstructionProtectionListener {

    @SubscribeEvent
    public void onBreak(BreakBlockEvent event) {
        if (event.getState().getBlock() instanceof ConstructionSiteBlock && event.getLevel() instanceof ServerLevel serverLevel) {
            // Restoring here (rather than the guard check below) is what stands in for the missing
            // Block#onRemove hook this version of the game no longer has -- see
            // ConstructionSiteBlock#restoreIfActive's own doc.
            ConstructionSiteBlock.restoreIfActive(serverLevel, event.getPos());
            return;
        }
        guard(event.getPlayer(), event.getPos(), event);
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        // A Building Supply Box relocation (see SupplyBoxLocatorItem) is a completely different
        // mechanic from Construction Site authoring -- it has no business being blocked by an
        // unrelated site's own build-volume restriction just because the placing player happens to
        // have one CONSTRUCTING elsewhere. Real bug found live, 2026-09-29: this guard doesn't check
        // proximity to the site at all, so ANY block placement anywhere in the world by a player with
        // an active CONSTRUCTING site outside that site's own footprint was silently reverted a
        // moment after being set -- exactly matching "it said repositioned, flashed for a frame, then
        // vanished."
        if (event.getState().getBlock() instanceof ConstructionBoxBlock) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            guard(player, event.getPos(), event);
        }
    }

    private void guard(Player player, BlockPos pos, ICancellableEvent event) {
        ActiveSiteRegistry.activeSiteFor(player.getUUID()).ifPresent(globalPos -> {
            if (!(player.level() instanceof ServerLevel serverLevel) || !globalPos.dimension().equals(serverLevel.dimension())) {
                return;
            }
            if (!(serverLevel.getBlockEntity(globalPos.pos()) instanceof ConstructionSiteBlockEntity site)
                    || site.phase() != ConstructionSitePhase.CONSTRUCTING) {
                return;
            }
            if (!withinBuildVolume(site, globalPos.pos(), pos)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("You can't build outside this Construction Site's footprint."));
            }
        });
    }

    private boolean withinBuildVolume(ConstructionSiteBlockEntity site, BlockPos sitePos, BlockPos target) {
        Column column = new Column(target.getX(), target.getZ());
        if (!site.markedColumns().contains(column)) {
            return false;
        }
        TierSpec spec = TierSpec.fromOrdinal(site.tier());
        Identifier blueprintTypeId = site.blueprintTypeId();
        BlueprintType type = blueprintTypeId != null
                ? BlueprintTypeRegistry.get(blueprintTypeId).orElse(new BlueprintType(blueprintTypeId, blueprintTypeId.toString(), 1.0))
                : new BlueprintType(Identifier.fromNamespaceAndPath(com.github.cerealklla.blueprynts.BluepryntsMod.MODID, "unknown"), "Unknown", 1.0);
        int relY = target.getY() - sitePos.getY();
        return relY >= -TierSpec.depthBelowGround(type, spec) && relY <= TierSpec.heightAboveGround(type, spec);
    }
}
