package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Load Blueprint" -- independent of whatever Size/Tier/Type is currently selected. */
public record LoadBlueprintPayload(BlockPos sitePos, String name) implements CustomPacketPayload {

    public static final Type<LoadBlueprintPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "load_blueprint"));

    public static final StreamCodec<ByteBuf, LoadBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), LoadBlueprintPayload::sitePos,
            ByteBufCodecs.STRING_UTF8, LoadBlueprintPayload::name,
            LoadBlueprintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
