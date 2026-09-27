package com.github.cerealklla.blueprynts.construction;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.GlobalPos;

/**
 * In-memory (not persisted -- rebuilt implicitly as sessions are re-claimed, same as every other
 * "active session" tracker in this suite, e.g. Settlemynts' {@code activePerimeterPlanner}) lookup
 * of which Construction Site each player currently has an active session on, keyed by player UUID.
 * Exists purely so {@code ConstructionProtectionListener} can find "does this player have an active
 * build volume, and where" in O(1) without scanning every loaded block entity.
 */
public final class ActiveSiteRegistry {

    private static final Map<UUID, GlobalPos> BY_PLAYER = new ConcurrentHashMap<>();

    private ActiveSiteRegistry() {
    }

    public static void register(UUID player, GlobalPos sitePos) {
        BY_PLAYER.put(player, sitePos);
    }

    public static void unregister(UUID player) {
        BY_PLAYER.remove(player);
    }

    public static Optional<GlobalPos> activeSiteFor(UUID player) {
        return Optional.ofNullable(BY_PLAYER.get(player));
    }
}
