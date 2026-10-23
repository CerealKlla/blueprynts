package com.github.cerealklla.blueprynts.construction.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.blueprynts.construction.SyncConstructionBoxMissingItemsPayload;

import net.minecraft.resources.Identifier;

/**
 * Client-only holder for the currently-open Construction Box's real per-item shortfall, pushed by
 * {@link SyncConstructionBoxMissingItemsPayload} -- see that class's own doc. A plain static field
 * (not a request/response slot like {@code ClientConstructionRequests} uses elsewhere) since this is
 * a live value the open {@code ConstructionBoxScreen} just reads every frame, not a one-shot "open
 * this screen" event -- only one Construction Box screen can ever be open at a time per client, so
 * there's no identity to key by.
 */
public final class ClientConstructionBoxMissingItemsState {

    private static Map<Identifier, Integer> missing = Map.of();

    private ClientConstructionBoxMissingItemsState() {
    }

    public static void set(SyncConstructionBoxMissingItemsPayload payload) {
        Map<Identifier, Integer> updated = new LinkedHashMap<>();
        List<String> ids = payload.itemIds();
        List<Integer> amounts = payload.amounts();
        for (int i = 0; i < ids.size() && i < amounts.size(); i++) {
            updated.put(Identifier.parse(ids.get(i)), amounts.get(i));
        }
        missing = updated;
    }

    public static Map<Identifier, Integer> get() {
        return missing;
    }
}
