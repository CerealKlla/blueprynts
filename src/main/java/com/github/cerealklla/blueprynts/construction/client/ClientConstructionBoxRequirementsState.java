package com.github.cerealklla.blueprynts.construction.client;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.blueprynts.construction.SyncConstructionBoxRequirementsPayload;

import net.minecraft.resources.Identifier;

/**
 * Client-only holder for the currently-open Construction Box's full per-item requirement list and
 * current blocker, pushed by {@link SyncConstructionBoxRequirementsPayload} -- see that class's own
 * doc. A plain static field (not a request/response slot like {@code ClientConstructionRequests}
 * uses elsewhere) since this is a live value the open {@code ConstructionBoxScreen} just reads every
 * frame, not a one-shot "open this screen" event -- only one Construction Box screen can ever be
 * open at a time per client, so there's no identity to key by.
 */
public final class ClientConstructionBoxRequirementsState {

    public record Entry(Identifier itemId, int supplied, int required) {
    }

    private static List<Entry> entries = List.of();
    private static Identifier nextNeeded = null;

    private ClientConstructionBoxRequirementsState() {
    }

    public static void set(SyncConstructionBoxRequirementsPayload payload) {
        List<Entry> updated = new ArrayList<>();
        List<String> ids = payload.itemIds();
        List<Integer> supplied = payload.suppliedAmounts();
        List<Integer> required = payload.requiredAmounts();
        int count = Math.min(ids.size(), Math.min(supplied.size(), required.size()));
        for (int i = 0; i < count; i++) {
            updated.add(new Entry(Identifier.parse(ids.get(i)), supplied.get(i), required.get(i)));
        }
        entries = updated;
        nextNeeded = payload.nextNeededItemId().isEmpty() ? null : Identifier.parse(payload.nextNeededItemId());
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static Identifier nextNeeded() {
        return nextNeeded;
    }
}
