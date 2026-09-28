package com.github.cerealklla.blueprynts.blueprint;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * One reviewer's vote on a Blueprint, kept as permanent history (never overwritten/removed) rather
 * than a single "reviewed by" field -- see the "Blueprint Reviewer" design in decisions.md: a
 * Blueprint accumulates votes from every player holding that permission, and once it clears some
 * threshold of {@code APPROVE}s and stays under some threshold of {@code REJECT}s, it's meant to
 * move to {@code Accepted} automatically. Data-only for now: nothing casts a vote yet, no permission
 * exists yet, and no threshold logic reads this list yet -- the field only exists so the storage
 * format doesn't need another breaking change once that's built.
 *
 * @param reviewerId the reviewing player's UUID (source of truth -- names can change)
 * @param reviewerName the reviewing player's display name at the time of the vote, for human-readable history
 * @param decision the vote cast
 * @param timestampMillis wall-clock time the vote was cast ({@link System#currentTimeMillis()}), for ordering/display
 */
public record BlueprintReview(UUID reviewerId, String reviewerName, Decision decision, long timestampMillis) {

    public static final Codec<BlueprintReview> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("reviewer_id").forGetter(BlueprintReview::reviewerId),
            Codec.STRING.fieldOf("reviewer_name").forGetter(BlueprintReview::reviewerName),
            Codec.STRING.xmap(s -> Decision.valueOf(s.toUpperCase()), Decision::name)
                    .fieldOf("decision").forGetter(BlueprintReview::decision),
            Codec.LONG.fieldOf("timestamp").forGetter(BlueprintReview::timestampMillis)
    ).apply(i, BlueprintReview::new));

    public enum Decision {
        APPROVE,
        REJECT
    }
}
