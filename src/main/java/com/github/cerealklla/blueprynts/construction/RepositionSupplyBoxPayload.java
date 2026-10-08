package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: the "Reposition Supply Box" button's click -- see {@code BluepryntsMod}'s handler. */
public record RepositionSupplyBoxPayload(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<RepositionSupplyBoxPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "reposition_supply_box"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RepositionSupplyBoxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), RepositionSupplyBoxPayload::boxPos,
            RepositionSupplyBoxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
