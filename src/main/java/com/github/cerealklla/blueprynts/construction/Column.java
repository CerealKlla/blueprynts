package com.github.cerealklla.blueprynts.construction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A single (x, z) column, relative to a Construction Site's own origin -- the unit the marked
 * footprint is tracked in. Deliberately not a {@code BlockPos} (which also carries a Y): the
 * footprint has no notion of "which Y a slab was placed at" beyond the site's own ground level,
 * only which column is marked.
 */
public record Column(int x, int z) {

    public static final Codec<Column> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Column::x),
            Codec.INT.fieldOf("z").forGetter(Column::z)
    ).apply(i, Column::new));
}
