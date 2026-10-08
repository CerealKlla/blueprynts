package com.github.cerealklla.blueprynts.api;

/**
 * A simple axis-aligned bounding box (absolute world x/z) a Building Supply Box's structure must
 * stay fully within -- e.g. the plot's own buildable (post-buffer) area, computed by whoever creates
 * the box. Deliberately plain ints, not a {@code Geometry.Polygon} or any other Cartographyr type --
 * this is the whole point of this record, added 2026-09-29 to break Blueprynts' Cartographyr
 * dependency: "we can break that dependency by having Settlemynts give the Construction Box the full
 * bounding box of where it's allowed to be placed when it first creates the Construction Box." The
 * caller (Settlemynts, which already depends on Cartographyr) does whatever real polygon/{@code
 * PlotValidity} math is needed and reduces the result down to this one plain rectangle.
 *
 * <p>Optional at {@link Blueprynts#createConstructionBox} -- {@code null} there means an "unbound"
 * box (no plot to violate, so no placement constraint at all), matching the user's explicit design:
 * "it'll be an optional parameter, so you can be allowed to place an unbound Construction Box or a
 * bound one."
 */
public record BuildableArea(int minX, int minZ, int maxX, int maxZ) {

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
