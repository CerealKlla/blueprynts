package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: "send me this Blueprint's preview image bytes." {@code variant} is {@code "full"}
 * or {@code "small"} -- see {@code BlueprintStorage#readPreviewBytes}. The client only sends this
 * when its own local cache (see {@code ClientBlueprintPreviewCache}) doesn't already have a copy at
 * least as fresh as the mtime it was told about in {@code OpenConstructionSiteScreenPayload}.
 */
public record RequestBlueprintPreviewPayload(BlockPos sitePos, String name, String variant) implements CustomPacketPayload {

    public static final Type<RequestBlueprintPreviewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "request_blueprint_preview"));

    public static final StreamCodec<ByteBuf, RequestBlueprintPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), RequestBlueprintPreviewPayload::sitePos,
            ByteBufCodecs.STRING_UTF8, RequestBlueprintPreviewPayload::name,
            ByteBufCodecs.STRING_UTF8, RequestBlueprintPreviewPayload::variant,
            RequestBlueprintPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
