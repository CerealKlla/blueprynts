package com.github.cerealklla.blueprynts.construction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One recorded line in a Construction Box's persistent transaction ledger (added 2026-10-12,
 * explicit user request: a standing record of every purchase/sale touching the plot's shop,
 * instead of temporary debug logging, so unusual activity -- like a shop's gold balance growing
 * faster than expected -- can be investigated after the fact rather than caught live). {@code
 * gameTime} is the raw tick count at the moment it was recorded; {@code line} is a plain, already-
 * formatted description -- this ledger is read-only/human-readable (see {@code
 * ConstructionBoxBlockEntity#ledger()}'s own doc), not a structured dataset callers query by field.
 */
public record LedgerEntry(long gameTime, String line) {

    public static final Codec<LedgerEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("time").forGetter(LedgerEntry::gameTime),
            Codec.STRING.fieldOf("line").forGetter(LedgerEntry::line)
    ).apply(i, LedgerEntry::new));
}
