package com.github.cerealklla.blueprynts.construction;

import java.util.Optional;

/**
 * Client-side bridge for "a Blueprynts screen should open" -- deliberately zero client-only
 * imports, same reasoning as Settlemynts' {@code founding.ClientFoundingRequests}: this is written
 * from the common {@code BluepryntsMod#registerPayloads} handler (which must stay harmless to
 * class-load on a dedicated server), and read/cleared from a genuinely client-only tick listener in
 * {@code BluepryntsModClient} that's the only place actually allowed to touch {@code Minecraft}/
 * {@code Screen}.
 */
public final class ClientConstructionRequests {

    private static volatile OpenConstructionSiteScreenPayload pendingConstructionSite;
    private static volatile OpenSlabRemoveScreenPayload pendingSlabRemove;

    private ClientConstructionRequests() {
    }

    public static void requestConstructionSiteScreen(OpenConstructionSiteScreenPayload payload) {
        pendingConstructionSite = payload;
    }

    public static Optional<OpenConstructionSiteScreenPayload> takePendingConstructionSiteScreen() {
        OpenConstructionSiteScreenPayload request = pendingConstructionSite;
        pendingConstructionSite = null;
        return Optional.ofNullable(request);
    }

    public static void requestSlabRemoveScreen(OpenSlabRemoveScreenPayload payload) {
        pendingSlabRemove = payload;
    }

    public static Optional<OpenSlabRemoveScreenPayload> takePendingSlabRemoveScreen() {
        OpenSlabRemoveScreenPayload request = pendingSlabRemove;
        pendingSlabRemove = null;
        return Optional.ofNullable(request);
    }
}
