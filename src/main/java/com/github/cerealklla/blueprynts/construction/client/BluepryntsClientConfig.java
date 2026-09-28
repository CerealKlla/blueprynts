package com.github.cerealklla.blueprynts.construction.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only config -- explicitly a per-player preference (not server/world config), per the
 * user's own instruction, so it follows a player across every server/world rather than being
 * dictated by whichever server they're on. Registered against {@code ModConfig.Type.CLIENT} in
 * {@code BluepryntsModClient}'s constructor (it already receives the {@code ModContainer} needed
 * to do that, previously unused).
 */
public final class BluepryntsClientConfig {

    public enum PreviewQuality {
        FULL,
        MINIMAL,
        NONE
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<PreviewQuality> PREVIEW_QUALITY = BUILDER
            .comment("Blueprint preview image quality shown when browsing saved Blueprints in the Load screen.",
                    "FULL = full-resolution image, MINIMAL = a smaller/cheaper image, NONE = no image at all.")
            .translation("blueprynts.config.preview_quality")
            .defineEnum("previewQuality", PreviewQuality.FULL);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BluepryntsClientConfig() {
    }

    public static PreviewQuality previewQuality() {
        return PREVIEW_QUALITY.get();
    }

    /** Cycles to the next value and persists it immediately -- used by the picker screen's toggle button. */
    public static PreviewQuality cyclePreviewQuality() {
        PreviewQuality[] values = PreviewQuality.values();
        PreviewQuality next = values[(previewQuality().ordinal() + 1) % values.length];
        PREVIEW_QUALITY.set(next);
        PREVIEW_QUALITY.save();
        return next;
    }
}
