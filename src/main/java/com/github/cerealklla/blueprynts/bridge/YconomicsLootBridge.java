package com.github.cerealklla.blueprynts.bridge;

import com.github.cerealklla.yconomics.bag.LootBagListener;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Bridges a Construction Site's refund pile (see {@code construction.SiteTerrainOps#spawnRefundPile})
 * into Yconomics' own Loot Bag mechanic, instead of scattering plain {@code ItemEntity}s -- deposits
 * exactly the way a voluntary or death drop already does ({@link LootBagListener#depositOrScatter}),
 * clustering into a nearby bag if one exists, scattering normally only once a bag is full.
 *
 * <p>This class must only ever be referenced (this method called, or this class loaded/classload-
 * triggered at all) from behind a {@code ModList.get().isLoaded("yconomics")} check at the call site
 * -- same isolation {@link SettlemyntsZoneBridge} uses for its own optional integration, so a
 * Yconomics-less server never force-loads Yconomics classes.
 */
public final class YconomicsLootBridge {

    private YconomicsLootBridge() {
    }

    public static void depositOrScatter(ServerLevel level, Vec3 pos, ItemStack stack) {
        LootBagListener.depositOrScatter(level, pos, stack);
    }
}
