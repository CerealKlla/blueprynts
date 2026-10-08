package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.blueprynts.api.BuildableArea;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Whether a proposed structure footprint (a {@code size x size} square, its near edge horizontally
 * centered on {@code anchor}, extending in the fixed {@code intoSite} direction -- user request,
 * 2026-09-29: "the player is at the center point of the width of the building on one edge...
 * rotation doesn't affect which way the building is facing, that's already determined by the single
 * Road Stake") fits fully inside a plain {@link BuildableArea} rectangle. {@code null} area means an
 * unbound box -- always fits, nothing to violate. Deliberately plain-rectangle containment, not
 * polygon math -- see {@link BuildableArea}'s own doc for why (2026-09-29 correction, dropping this
 * mod's Cartographyr dependency); the buffer ring this already enforces was baked into the rectangle
 * itself by whoever computed it (Settlemynts, via {@code PlotValidity.REQUIRED_BUFFER}), not
 * recomputed here.
 */
public record BuildingFootprintCheck(boolean fits, List<Column> footprintCells) {

    public static BuildingFootprintCheck evaluate(BuildableArea area, BlockPos anchor, Direction intoSite, int size) {
        Direction right = intoSite.getClockWise();
        int half = size / 2;

        List<Column> footprint = new ArrayList<>();
        boolean fits = true;
        for (int forward = 1; forward <= size; forward++) {
            for (int lateral = -half; lateral <= size - half - 1; lateral++) {
                int x = anchor.getX() + intoSite.getStepX() * forward + right.getStepX() * lateral;
                int z = anchor.getZ() + intoSite.getStepZ() * forward + right.getStepZ() * lateral;
                footprint.add(new Column(x, z));
                if (area != null && !area.contains(x, z)) {
                    fits = false;
                }
            }
        }
        return new BuildingFootprintCheck(fits, footprint);
    }

    /** Just the rectangle's outline cells (perimeter of the {@code size x size} footprint) -- cheap enough to regenerate as ghost markers every tick interval, unlike a filled area. */
    public List<Column> outlineCells() {
        if (footprintCells.isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Column c : footprintCells) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        List<Column> outline = new ArrayList<>();
        for (Column c : footprintCells) {
            if (c.x() == minX || c.x() == maxX || c.z() == minZ || c.z() == maxZ) {
                outline.add(c);
            }
        }
        return outline;
    }
}
