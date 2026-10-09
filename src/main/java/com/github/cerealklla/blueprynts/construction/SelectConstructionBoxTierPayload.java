package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: a Tier was picked on {@code client.ConstructionBoxTierPickerScreen} -- re-opens the Blueprint-name picker filtered to it. */
public record SelectConstructionBoxTierPayload(BlockPos boxPos, int tier) implements CustomPacketPayload {

    public static final Type<SelectConstructionBoxTierPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "select_construction_box_tier"));

    public static final StreamCodec<ByteBuf, SelectConstructionBoxTierPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), SelectConstructionBoxTierPayload::boxPos,
            ByteBufCodecs.VAR_INT, SelectConstructionBoxTierPayload::tier,
            SelectConstructionBoxTierPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
