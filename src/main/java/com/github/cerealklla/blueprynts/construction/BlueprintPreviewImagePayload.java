package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: the raw PNG bytes answering a {@link RequestBlueprintPreviewPayload}. {@code
 * mtime} is echoed back so the client can store it alongside the bytes in its own cache (see {@code
 * ClientBlueprintPreviewCache}) without a second round trip to ask "how fresh is this."
 * {@code MAX_IMAGE_BYTES} is a generous cap (2 MB) -- BluepryntImager's own output is far smaller
 * (a real 1024x768 render was ~135 KB) -- purely a sanity ceiling, not a tuned limit.
 */
public record BlueprintPreviewImagePayload(String name, String variant, long mtime, byte[] pngBytes) implements CustomPacketPayload {

    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    public static final Type<BlueprintPreviewImagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "blueprint_preview_image"));

    public static final StreamCodec<ByteBuf, BlueprintPreviewImagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BlueprintPreviewImagePayload::name,
            ByteBufCodecs.STRING_UTF8, BlueprintPreviewImagePayload::variant,
            ByteBufCodecs.VAR_LONG, BlueprintPreviewImagePayload::mtime,
            ByteBufCodecs.byteArray(MAX_IMAGE_BYTES), BlueprintPreviewImagePayload::pngBytes,
            BlueprintPreviewImagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
