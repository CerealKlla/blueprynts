package com.github.cerealklla.blueprynts.construction;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteTerrainOpsTest {

    @Test
    void boundaryRingIsEveryUnmarkedNeighborOfAMarkedColumn() {
        // A 2x2 marked block: (0,0),(1,0),(0,1),(1,1) -- ring should be every unmarked column
        // 4-adjacent to one of those, never a marked column itself, and never a diagonal-only neighbor.
        Set<Column> marked = Set.of(new Column(0, 0), new Column(1, 0), new Column(0, 1), new Column(1, 1));
        Set<Column> ring = SiteTerrainOps.computeBoundaryRing(marked);

        assertTrue(ring.contains(new Column(-1, 0)));
        assertTrue(ring.contains(new Column(2, 0)));
        assertTrue(ring.contains(new Column(0, -1)));
        assertTrue(ring.contains(new Column(0, 2)));
        assertFalse(ring.contains(new Column(0, 0))); // marked columns are never in the ring
        assertFalse(ring.contains(new Column(2, 2))); // diagonal-only, not 4-adjacent to any marked column
    }

    @Test
    void relativeColumnRoundTripsThroughAllFourFacings() {
        BlockPos sitePos = new BlockPos(100, 64, 100);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Column world = new Column(sitePos.getX() + 5, sitePos.getZ() - 3);
            Column relative = SiteTerrainOps.toRelativeColumn(sitePos, facing, world.x(), world.z());
            Column roundTripped = SiteTerrainOps.toWorldColumn(sitePos, facing, relative);
            assertEquals(world, roundTripped, "round trip failed for facing " + facing);
        }
    }

    @Test
    void outerAreaStartsImmediatelyBehindTheSiteAndIsCenteredLaterally() {
        BlockPos sitePos = new BlockPos(0, 64, 0);
        SiteTerrainOps.OuterArea area = SiteTerrainOps.computeOuterArea(sitePos, Direction.NORTH, 15);

        // NORTH: forward direction decreases Z: area occupies z in [-15, -1], centered on x.
        assertEquals(-15, area.minZ());
        assertEquals(-1, area.maxZ());
        assertEquals(-7, area.minX());
        assertEquals(7, area.maxX());
    }
}
