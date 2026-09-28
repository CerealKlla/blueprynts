package com.github.cerealklla.blueprynts.blueprint;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.blueprynts.construction.Column;
import com.github.cerealklla.blueprynts.construction.SizeClass;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * A saved Blueprint: the marked-column footprint (each column a (forward, lateral) pair relative
 * to the saving site's own facing -- see {@code BlueprintCell}), its own height/depth (independent
 * of whatever the current Construction Site's Tier is set to), and every captured cell. Loading a
 * Blueprint re-derives its clearing/wool region entirely from these stored values -- there's no
 * "does this fit the current selection" reconciliation, per the user's explicit correction -- and
 * re-orients against the *loading* site's own facing, so it pastes correctly regardless of which
 * direction that site happens to face.
 *
 * <p>{@link #facing()} is the saving site's own {@code intoSite()} direction -- needed so Load can
 * rotate each cell's own {@code BlockState} (a stair's {@code facing}, a sign's {@code rotation},
 * etc., all absolute compass directions as captured) to match the *loading* site's facing, the same
 * way {@link #relativeColumns()} already re-orients positions. Without this, only the footprint's
 * shape re-orients correctly -- individual blocks' own facing stays fixed to whatever direction the
 * saving site originally faced, visibly wrong (e.g. stairs 180 degrees off) whenever the loading
 * site faces a different direction than the one the Blueprint was saved at. {@code null} for files
 * saved before this field existed -- {@code loadBlueprint} treats that as "no correction possible"
 * and skips rotation entirely, rather than guessing a default that could rotate an old file wrong a
 * second way.
 *
 * <p>{@link #sizeClass()} is the saving site's own plot Size (Small/Large) -- Load re-clears the
 * *entire* original plot outer area (via {@code SiteTerrainOps.computeOuterArea}, the same one a
 * fresh Begin Design uses), not just a tight bounding box around the built footprint. Without this,
 * Load only knew about the marked columns themselves, so any deliberate "white space" a player left
 * around their structure within the plot (e.g. building against the back edge instead of centered)
 * was lost -- a real playtest report ("clicking Load for two different blueprints clears a different
 * area... the blueprint needs to track all the white space around the structure too"). `null` for
 * files saved before this field existed -- Load falls back to {@code
 * SiteTerrainOps.computeOuterAreaForFootprint} (the old, footprint-bounding-box behavior) in that
 * case, since there's no way to recover an old file's original plot size, and a bounding box is
 * guaranteed to at least cover the real content even if it doesn't reproduce the original margin.
 */
public record BlueprintRecord(
        int templateVersion,
        String name,
        String author,
        BlueprintStatus status,
        List<BlueprintReview> reviews,
        Identifier blueprintTypeId,
        int tier,
        List<Column> relativeColumns,
        int height,
        int depth,
        List<BlueprintCell> cells,
        Direction facing,
        SizeClass sizeClass) {

    /**
     * Bump this whenever a change to this record's own JSON shape would otherwise break reading
     * older saved files (a field renamed/removed/re-typed, not just a new optional field with a
     * safe default). Every file written today stamps this value; a future reader can branch on
     * whatever value it finds to decide how to interpret the rest of the document.
     */
    public static final int CURRENT_TEMPLATE_VERSION = 1;

    public static final Codec<BlueprintRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("template_version", 1).forGetter(BlueprintRecord::templateVersion),
            Codec.STRING.fieldOf("name").forGetter(BlueprintRecord::name),
            Codec.STRING.fieldOf("author").forGetter(BlueprintRecord::author),
            Codec.STRING.xmap(s -> BlueprintStatus.valueOf(s.toUpperCase()), BlueprintStatus::name)
                    .optionalFieldOf("status", BlueprintStatus.UNREVIEWED).forGetter(BlueprintRecord::status),
            BlueprintReview.CODEC.listOf().optionalFieldOf("reviews", List.of()).forGetter(BlueprintRecord::reviews),
            Identifier.CODEC.fieldOf("blueprint_type").forGetter(BlueprintRecord::blueprintTypeId),
            Codec.INT.fieldOf("tier").forGetter(BlueprintRecord::tier),
            Column.CODEC.listOf().fieldOf("columns").forGetter(BlueprintRecord::relativeColumns),
            Codec.INT.fieldOf("height").forGetter(BlueprintRecord::height),
            Codec.INT.fieldOf("depth").forGetter(BlueprintRecord::depth),
            BlueprintCell.CODEC.listOf().fieldOf("cells").forGetter(BlueprintRecord::cells),
            Direction.CODEC.optionalFieldOf("facing").forGetter(r -> Optional.ofNullable(r.facing())),
            Codec.STRING.xmap(SizeClass::valueOf, SizeClass::name).optionalFieldOf("size_class")
                    .forGetter(r -> Optional.ofNullable(r.sizeClass()))
    ).apply(i, (templateVersion, name, author, status, reviews, blueprintTypeId, tier, relativeColumns, height, depth, cells, facing, sizeClass) ->
            new BlueprintRecord(templateVersion, name, author, status, reviews, blueprintTypeId, tier, relativeColumns, height, depth, cells,
                    facing.orElse(null), sizeClass.orElse(null))));

    /** Same record with a different {@link #status()} -- used when a Blueprint is moved between review folders. */
    public BlueprintRecord withStatus(BlueprintStatus newStatus) {
        return new BlueprintRecord(templateVersion, name, author, newStatus, reviews, blueprintTypeId, tier, relativeColumns, height, depth, cells, facing, sizeClass);
    }

    /** Same record with one more vote appended to the permanent review history -- never replaces or removes a prior vote. */
    public BlueprintRecord withAddedReview(BlueprintReview review) {
        List<BlueprintReview> updated = new java.util.ArrayList<>(reviews);
        updated.add(review);
        return new BlueprintRecord(templateVersion, name, author, status, List.copyOf(updated), blueprintTypeId, tier, relativeColumns, height, depth, cells, facing, sizeClass);
    }
}
