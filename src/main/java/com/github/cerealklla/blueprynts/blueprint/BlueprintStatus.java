package com.github.cerealklla.blueprynts.blueprint;

/**
 * Review status of a saved Blueprint. Data-only for now -- the user was explicit this is not
 * enforced anywhere yet (no visibility gating in Blueprynts or Settlemynts), just the field and the
 * on-disk folder structure it drives, ahead of a later "release" milestone that will start checking it.
 */
public enum BlueprintStatus {
    UNREVIEWED,
    ACCEPTED,
    REJECTED;

    /** Display/folder-name form, e.g. "Unreviewed". */
    public String label() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
