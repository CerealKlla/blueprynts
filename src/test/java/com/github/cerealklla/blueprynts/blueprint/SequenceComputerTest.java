package com.github.cerealklla.blueprynts.blueprint;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the layer-ordering guarantee only -- the same-layer support-dependency sort needs a real
 * {@code BlockState} (ladders, wall torches, etc.) to exercise {@code isWallMounted}, which needs a
 * live registry bootstrap this project's plain JUnit setup doesn't have (same accepted limitation as
 * other {@code BlockState}-dependent logic elsewhere in this suite, e.g. Protectyons'
 * {@code ProtectionExemptBlocks}) -- verified live instead (see decisions.md).
 */
class SequenceComputerTest {

    private static BlueprintCell preExisting(int x, int y, int z) {
        return new BlueprintCell(x, y, z, Optional.empty(), -1);
    }

    @Test
    void groundFloorComesFirst() {
        List<BlueprintCell> cells = List.of(
                preExisting(0, 1, 0),
                preExisting(0, 0, 0),
                preExisting(0, -1, 0));
        List<BlueprintCell> result = SequenceComputer.assign(cells);
        int seqAtY0 = sequenceAt(result, 0);
        int seqAtYMinus1 = sequenceAt(result, -1);
        int seqAtY1 = sequenceAt(result, 1);
        assertTrue(seqAtY0 < seqAtYMinus1, "ground floor (relY=0) must come before relY=-1");
        assertTrue(seqAtY0 < seqAtY1, "ground floor (relY=0) must come before relY=1");
    }

    @Test
    void layersAlternateBelowThenAboveByIncreasingDistance() {
        List<BlueprintCell> cells = List.of(
                preExisting(0, 0, 0),
                preExisting(0, -1, 0),
                preExisting(0, 1, 0),
                preExisting(0, -2, 0),
                preExisting(0, 2, 0));
        List<BlueprintCell> result = SequenceComputer.assign(cells);
        assertTrue(sequenceAt(result, 0) < sequenceAt(result, -1));
        assertTrue(sequenceAt(result, -1) < sequenceAt(result, 1));
        assertTrue(sequenceAt(result, 1) < sequenceAt(result, -2));
        assertTrue(sequenceAt(result, -2) < sequenceAt(result, 2));
    }

    @Test
    void everyCellGetsAUniqueSequentialNumber() {
        List<BlueprintCell> cells = List.of(
                preExisting(0, 0, 0), preExisting(1, 0, 0), preExisting(0, 0, 1),
                preExisting(0, 1, 0), preExisting(0, -1, 0));
        List<BlueprintCell> result = SequenceComputer.assign(cells);
        List<Integer> sequences = result.stream().map(BlueprintCell::sequence).sorted().toList();
        assertEquals(List.of(0, 1, 2, 3, 4), sequences);
    }

    private static int sequenceAt(List<BlueprintCell> cells, int relY) {
        return cells.stream().filter(c -> c.relY() == relY).findFirst().orElseThrow().sequence();
    }
}
