package com.github.cerealklla.blueprynts.api;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A plot's real per-cell interior shape -- unlike {@link BuildableArea} (a plain bounding rectangle,
 * a deliberate over-approximation accepted for the building footprint check), this exists because a
 * plot is a {@code Geometry.Polygon}, not a rectangle: a rope-fence-staked plot is very often
 * non-convex or otherwise irregular, so a bounding box over it can easily include real-world area the
 * player never actually staked (user correction, 2026-09-29: "but a plot is a polygon, not a
 * rectangle" -- caught right after {@code plotArea} first shipped as a {@code BuildableArea}
 * bounding-box reduction, the same mistake {@code buildableArea} already makes but explicitly
 * flagged as wrong for this specific field).
 *
 * <p>Still deliberately Cartographyr-free -- a flat per-cell boolean grid over a small window
 * (whatever the caller's own bounding box was) rather than a real {@code Geometry.Polygon}, so this
 * mod still needs no compile dependency on Cartographyr. The caller (Settlemynts, which already has
 * the real polygon and its own {@code PlotValidity.Grid} classification) reduces it down to this
 * grid once, at Construction Box creation time.
 */
public final class PlotArea {

    public static final Codec<PlotArea> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("min_x").forGetter(PlotArea::minX),
            Codec.INT.fieldOf("min_z").forGetter(PlotArea::minZ),
            Codec.INT.fieldOf("width").forGetter(PlotArea::width),
            Codec.INT.fieldOf("height").forGetter(PlotArea::height),
            Codec.BOOL.listOf().fieldOf("interior").forGetter(PlotArea::interiorAsList)
    ).apply(i, PlotArea::fromList));

    private final int minX;
    private final int minZ;
    private final int width;
    private final int height;
    private final boolean[] interior;

    /** {@code interior} is row-major, index {@code z * width + x} (local coordinates, offset by minX/minZ), {@code true} meaning "inside the plot." */
    public PlotArea(int minX, int minZ, int width, int height, boolean[] interior) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.interior = interior;
    }

    private static PlotArea fromList(int minX, int minZ, int width, int height, List<Boolean> list) {
        boolean[] interior = new boolean[list.size()];
        for (int i = 0; i < interior.length; i++) {
            interior[i] = list.get(i);
        }
        return new PlotArea(minX, minZ, width, height, interior);
    }

    private List<Boolean> interiorAsList() {
        List<Boolean> list = new ArrayList<>(interior.length);
        for (boolean b : interior) {
            list.add(b);
        }
        return list;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean contains(int x, int z) {
        int lx = x - minX;
        int lz = z - minZ;
        if (lx < 0 || lz < 0 || lx >= width || lz >= height) {
            return false;
        }
        return interior[lz * width + lx];
    }
}
