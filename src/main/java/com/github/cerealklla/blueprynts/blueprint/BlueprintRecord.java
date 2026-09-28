package com.github.cerealklla.blueprynts.blueprint;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.blueprynts.construction.Column;

import net.minecraft.resources.Identifier;

/**
 * A saved Blueprint: the marked-column footprint (each column a (forward, lateral) pair relative
 * to the saving site's own facing -- see {@code BlueprintCell}), its own height/depth (independent
 * of whatever the current Construction Site's Tier is set to), and every captured cell. Loading a
 * Blueprint re-derives its clearing/wool region entirely from these stored values -- there's no
 * "does this fit the current selection" reconciliation, per the user's explicit correction -- and
 * re-orients against the *loading* site's own facing, so it pastes correctly regardless of which
 * direction that site happens to face.
 */
public record BlueprintRecord(
        String name,
        String author,
        BlueprintStatus status,
        Identifier blueprintTypeId,
        int tier,
        List<Column> relativeColumns,
        int height,
        int depth,
        List<BlueprintCell> cells) {

    public static final Codec<BlueprintRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(BlueprintRecord::name),
            Codec.STRING.fieldOf("author").forGetter(BlueprintRecord::author),
            Codec.STRING.xmap(s -> BlueprintStatus.valueOf(s.toUpperCase()), BlueprintStatus::name)
                    .optionalFieldOf("status", BlueprintStatus.UNREVIEWED).forGetter(BlueprintRecord::status),
            Identifier.CODEC.fieldOf("blueprint_type").forGetter(BlueprintRecord::blueprintTypeId),
            Codec.INT.fieldOf("tier").forGetter(BlueprintRecord::tier),
            Column.CODEC.listOf().fieldOf("columns").forGetter(BlueprintRecord::relativeColumns),
            Codec.INT.fieldOf("height").forGetter(BlueprintRecord::height),
            Codec.INT.fieldOf("depth").forGetter(BlueprintRecord::depth),
            BlueprintCell.CODEC.listOf().fieldOf("cells").forGetter(BlueprintRecord::cells)
    ).apply(i, BlueprintRecord::new));

    /** Same record with a different {@link #status()} -- used when a Blueprint is moved between review folders. */
    public BlueprintRecord withStatus(BlueprintStatus newStatus) {
        return new BlueprintRecord(name, author, newStatus, blueprintTypeId, tier, relativeColumns, height, depth, cells);
    }
}
