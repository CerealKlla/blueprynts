package com.github.cerealklla.blueprynts.construction;

/** A Construction Site's current state -- see {@code ConstructionSiteBlockEntity}. */
public enum ConstructionSitePhase {
    /** No active session; ground behind the site is untouched (or already restored). */
    IDLE,
    /** Outer area leveled, slabs granted/being placed, footprint not yet committed. */
    DESIGNING,
    /** Footprint committed: slabs removed, wall/wool applied, build volume protected and buildable. */
    CONSTRUCTING
}
