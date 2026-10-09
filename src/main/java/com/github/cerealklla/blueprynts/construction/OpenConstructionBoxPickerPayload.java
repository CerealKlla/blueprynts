package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: opens {@code client.ConstructionBoxPickerScreen} -- the Blueprint-selection
 * step of the Building Supply Box (design-document.md Section 11 points 4-6; see {@code
 * ConstructionBoxBlockEntity}'s own doc). {@code names} is every saved Blueprint matching this box's
 * own Zone Type and (currently, lowest-tier-only) constraints -- see {@code
 * ConstructionBoxBlock#useWithoutItem}. {@code fullPreviewMtimes}/{@code smallPreviewMtimes} are
 * parallel to {@code names}, added 2026-09-29 (user request: "add the preview image into the
 * Blueprint Selection on the Construction Box") -- same shape {@code
 * OpenConstructionSiteScreenPayload} already uses for the editor's own richer picker, reused as-is
 * rather than duplicated.
 *
 * <p><b>Widened 2026-10-09</b> -- now always reached via {@code ConstructionBoxTierPickerScreen}'s
 * own prior step (pick a Tier, 1 through the box's {@code allowedTier} cap), so {@code names} is
 * always filtered to one specific, already-chosen Tier -- see {@code
 * BluepryntsMod}'s {@code SelectConstructionBoxTierPayload} handler, which builds this payload.
 */
public record OpenConstructionBoxPickerPayload(BlockPos boxPos, List<String> names,
                                                List<Long> fullPreviewMtimes, List<Long> smallPreviewMtimes)
        implements CustomPacketPayload {

    public static final Type<OpenConstructionBoxPickerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "open_construction_box_picker"));

    public static final StreamCodec<ByteBuf, OpenConstructionBoxPickerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenConstructionBoxPickerPayload::boxPos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenConstructionBoxPickerPayload::names,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG), OpenConstructionBoxPickerPayload::fullPreviewMtimes,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG), OpenConstructionBoxPickerPayload::smallPreviewMtimes,
            OpenConstructionBoxPickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
