package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client: opens {@code SlabRemoveScreen} for the specific slab the player right-clicked. */
public record OpenSlabRemoveScreenPayload(BlockPos slabPos) implements CustomPacketPayload {

    public static final Type<OpenSlabRemoveScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "open_slab_remove_screen"));

    public static final StreamCodec<ByteBuf, OpenSlabRemoveScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenSlabRemoveScreenPayload::slabPos,
            OpenSlabRemoveScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
