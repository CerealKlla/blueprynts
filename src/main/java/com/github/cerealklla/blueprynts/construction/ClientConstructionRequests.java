package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Client-side bridge for "a Blueprynts screen should open" (or, now, "a preview image arrived") --
 * deliberately zero client-only imports, same reasoning as Settlemynts' {@code
 * founding.ClientFoundingRequests}: this is written from the common {@code
 * BluepryntsMod#registerPayloads} handler (which must stay harmless to class-load on a dedicated
 * server), and read/cleared from a genuinely client-only tick listener in {@code
 * BluepryntsModClient} that's the only place actually allowed to touch {@code Minecraft}/{@code
 * Screen}/texture registration.
 */
public final class ClientConstructionRequests {

    private static volatile OpenConstructionSiteScreenPayload pendingConstructionSite;
    private static volatile OpenSlabRemoveScreenPayload pendingSlabRemove;
    private static volatile OpenConstructionBoxPickerPayload pendingConstructionBoxPicker;
    private static volatile OpenConstructionBoxTierPickerPayload pendingConstructionBoxTierPicker;
    private static final Queue<BlueprintPreviewImagePayload> pendingPreviewImages = new ConcurrentLinkedQueue<>();

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

    public static void requestConstructionBoxPickerScreen(OpenConstructionBoxPickerPayload payload) {
        pendingConstructionBoxPicker = payload;
    }

    public static Optional<OpenConstructionBoxPickerPayload> takePendingConstructionBoxPickerScreen() {
        OpenConstructionBoxPickerPayload request = pendingConstructionBoxPicker;
        pendingConstructionBoxPicker = null;
        return Optional.ofNullable(request);
    }

    public static void requestConstructionBoxTierPickerScreen(OpenConstructionBoxTierPickerPayload payload) {
        pendingConstructionBoxTierPicker = payload;
    }

    public static Optional<OpenConstructionBoxTierPickerPayload> takePendingConstructionBoxTierPickerScreen() {
        OpenConstructionBoxTierPickerPayload request = pendingConstructionBoxTierPicker;
        pendingConstructionBoxTierPicker = null;
        return Optional.ofNullable(request);
    }

    /** Multiple preview images can arrive close together (rapid paging) -- a queue, not a single slot, so none are dropped. */
    public static void requestStorePreviewImage(BlueprintPreviewImagePayload payload) {
        pendingPreviewImages.add(payload);
    }

    public static List<BlueprintPreviewImagePayload> takePendingPreviewImages() {
        List<BlueprintPreviewImagePayload> drained = new ArrayList<>();
        BlueprintPreviewImagePayload next;
        while ((next = pendingPreviewImages.poll()) != null) {
            drained.add(next);
        }
        return drained;
    }
}
