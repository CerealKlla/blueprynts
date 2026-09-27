package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every original block state touched by leveling/wool/building, captured before the first change
 * so a Construction Site session can be restored exactly -- both for a deliberate "clear" and for
 * the auto-clear/anti-farming safeguard (walking away always reverts to true original terrain,
 * ores/trees included, not just "whatever was leveled").
 *
 * <p>Keyed by absolute world position, captured in insertion order (a {@code LinkedHashMap}) purely
 * so a captured position is never accidentally overwritten by a later capture of the same position
 * within one operation -- {@link #captureIfAbsent} is the only mutator.
 */
public final class TerrainSnapshot {

    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

    /** Captures {@code pos}'s current state the first time it's touched; a later call for the same pos is a no-op, so restore always replays the true original. */
    public void captureIfAbsent(BlockPos pos, BlockState currentState) {
        original.putIfAbsent(pos.immutable(), currentState);
    }

    public Map<BlockPos, BlockState> capturedStates() {
        return original;
    }

    public boolean isEmpty() {
        return original.isEmpty();
    }

    private record Entry(BlockPos pos, BlockState state) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Entry::state)
        ).apply(i, Entry::new));
    }

    /** Persisted so a crash/restart mid-session doesn't strand a leveled site with no way to restore it. */
    public static final Codec<TerrainSnapshot> CODEC = Entry.CODEC.listOf().xmap(
            entries -> {
                TerrainSnapshot snapshot = new TerrainSnapshot();
                for (Entry entry : entries) {
                    snapshot.captureIfAbsent(entry.pos(), entry.state());
                }
                return snapshot;
            },
            snapshot -> {
                List<Entry> entries = new ArrayList<>();
                snapshot.capturedStates().forEach((pos, state) -> entries.add(new Entry(pos, state)));
                return entries;
            });
}
