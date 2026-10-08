package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: a Blueprint was picked on {@code client.ConstructionBoxPickerScreen}. */
public record SelectConstructionBoxBlueprintPayload(BlockPos boxPos, String name) implements CustomPacketPayload {

    public static final Type<SelectConstructionBoxBlueprintPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "select_construction_box_blueprint"));

    public static final StreamCodec<ByteBuf, SelectConstructionBoxBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), SelectConstructionBoxBlueprintPayload::boxPos,
            ByteBufCodecs.STRING_UTF8, SelectConstructionBoxBlueprintPayload::name,
            SelectConstructionBoxBlueprintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
