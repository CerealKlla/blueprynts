package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "View Ledger" button on {@code client.ConstructionBoxScreen} -- see {@code LedgerEntry}'s own doc. */
public record RequestLedgerPayload(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<RequestLedgerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "request_ledger"));

    public static final StreamCodec<ByteBuf, RequestLedgerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), RequestLedgerPayload::boxPos,
            RequestLedgerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
