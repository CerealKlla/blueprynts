package com.github.cerealklla.blueprynts.construction;

import java.util.List;
import java.util.Optional;

import com.github.cerealklla.nuclyus.request.ClientRequestQueue;
import com.github.cerealklla.nuclyus.request.ClientRequestSlot;

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

    private static final ClientRequestSlot<OpenConstructionSiteScreenPayload> CONSTRUCTION_SITE = new ClientRequestSlot<>();
    private static final ClientRequestSlot<OpenSlabRemoveScreenPayload> SLAB_REMOVE = new ClientRequestSlot<>();
    private static final ClientRequestSlot<OpenConstructionBoxPickerPayload> CONSTRUCTION_BOX_PICKER = new ClientRequestSlot<>();
    private static final ClientRequestSlot<OpenConstructionBoxTierPickerPayload> CONSTRUCTION_BOX_TIER_PICKER = new ClientRequestSlot<>();
    private static final ClientRequestSlot<OpenLedgerPayload> OPEN_LEDGER = new ClientRequestSlot<>();
    /** Multiple preview images can arrive close together (rapid paging) -- a queue, not a single slot, so none are dropped. */
    private static final ClientRequestQueue<BlueprintPreviewImagePayload> PREVIEW_IMAGES = new ClientRequestQueue<>();

    private ClientConstructionRequests() {
    }

    public static void requestConstructionSiteScreen(OpenConstructionSiteScreenPayload payload) {
        CONSTRUCTION_SITE.request(payload);
    }

    public static Optional<OpenConstructionSiteScreenPayload> takePendingConstructionSiteScreen() {
        return CONSTRUCTION_SITE.takePending();
    }

    public static void requestSlabRemoveScreen(OpenSlabRemoveScreenPayload payload) {
        SLAB_REMOVE.request(payload);
    }

    public static Optional<OpenSlabRemoveScreenPayload> takePendingSlabRemoveScreen() {
        return SLAB_REMOVE.takePending();
    }

    public static void requestConstructionBoxPickerScreen(OpenConstructionBoxPickerPayload payload) {
        CONSTRUCTION_BOX_PICKER.request(payload);
    }

    public static Optional<OpenConstructionBoxPickerPayload> takePendingConstructionBoxPickerScreen() {
        return CONSTRUCTION_BOX_PICKER.takePending();
    }

    public static void requestConstructionBoxTierPickerScreen(OpenConstructionBoxTierPickerPayload payload) {
        CONSTRUCTION_BOX_TIER_PICKER.request(payload);
    }

    public static Optional<OpenConstructionBoxTierPickerPayload> takePendingConstructionBoxTierPickerScreen() {
        return CONSTRUCTION_BOX_TIER_PICKER.takePending();
    }

    public static void requestOpenLedger(OpenLedgerPayload payload) {
        OPEN_LEDGER.request(payload);
    }

    public static Optional<OpenLedgerPayload> takePendingOpenLedger() {
        return OPEN_LEDGER.takePending();
    }

    public static void requestStorePreviewImage(BlueprintPreviewImagePayload payload) {
        PREVIEW_IMAGES.request(payload);
    }

    public static List<BlueprintPreviewImagePayload> takePendingPreviewImages() {
        return PREVIEW_IMAGES.takePending();
    }
}
