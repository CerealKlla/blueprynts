package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Change Blueprint" button on {@code client.ConstructionBoxScreen} -- opens the Tier picker (see {@code ConstructionBoxBlock#openTierPicker}), reachable regardless of whether the box is still funding, already built, or mid-Reposition. */
public record RequestConstructionBoxTierPickerPayload(BlockPos boxPos) implements CustomPacketPayload {

    public static final Type<RequestConstructionBoxTierPickerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "request_construction_box_tier_picker"));

    public static final StreamCodec<ByteBuf, RequestConstructionBoxTierPickerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), RequestConstructionBoxTierPickerPayload::boxPos,
            RequestConstructionBoxTierPickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
