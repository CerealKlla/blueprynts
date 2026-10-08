package com.github.cerealklla.blueprynts.construction;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: the "Reposition Building" button's click -- see {@code BluepryntsMod}'s handler for the actual clear+grant logic. */
public record RepositionBuildingPayload(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<RepositionBuildingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "reposition_building"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RepositionBuildingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), RepositionBuildingPayload::boxPos,
            RepositionBuildingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
