package com.github.cerealklla.blueprynts.blueprint;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.block.state.BlockState;

/**
 * One captured cell of a saved Blueprint. {@code relX}/{@code relZ} are (forward, lateral)
 * coordinates relative to the site it was saved from and that site's own facing -- see {@code
 * construction.SiteTerrainOps#toRelativeColumn}/{@code #toWorldColumn} -- so loading this Blueprint
 * at a *different* site correctly re-orients it against that site's own facing, rather than always
 * pasting in a fixed absolute orientation. {@code relY} is relative to the ground surface (0),
 * never negative-zero-ambiguous: negative is below ground, positive is above.
 *
 * <p>{@code state} present = a real captured block (including {@code Blocks.AIR} if the player
 * deliberately cleared something, e.g. mined out below-ground wool and left it empty); {@code
 * state} empty = the {@code PRE_EXISTING} sentinel -- "leave whatever terrain is already there,"
 * meaning this cell was untouched brown-wool below-ground filler at save time.
 *
 * <p>{@code sequence} is the cell's build-order position, computed once by {@link SequenceComputer}
 * at Save time and never recalculated (design-document.md's "Planned: Passive Construction &
 * Funding" section, captured 2026-09-29) -- lower sequence numbers paste first. {@code -1} is the
 * backfill sentinel for a file saved before this field existed (same pattern as {@code facing}/
 * {@code sizeClass} below) -- callers that care about ordering should treat a Blueprint with any
 * {@code -1} sequence as unordered (paste in on-disk order, the old behavior) rather than guessing.
 */
public record BlueprintCell(int relX, int relY, int relZ, Optional<BlockState> state, int sequence) {

    public static final Codec<BlueprintCell> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(BlueprintCell::relX),
            Codec.INT.fieldOf("y").forGetter(BlueprintCell::relY),
            Codec.INT.fieldOf("z").forGetter(BlueprintCell::relZ),
            BlockState.CODEC.optionalFieldOf("state").forGetter(BlueprintCell::state),
            Codec.INT.optionalFieldOf("sequence", -1).forGetter(BlueprintCell::sequence)
    ).apply(i, BlueprintCell::new));

    public boolean isPreExisting() {
        return state.isEmpty();
    }
}
