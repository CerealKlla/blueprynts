package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Save Blueprint" -- captures the current build volume under {@code name}. */
public record SaveBlueprintPayload(BlockPos sitePos, String name) implements CustomPacketPayload {

    public static final Type<SaveBlueprintPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "save_blueprint"));

    public static final StreamCodec<ByteBuf, SaveBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), SaveBlueprintPayload::sitePos,
            ByteBufCodecs.STRING_UTF8, SaveBlueprintPayload::name,
            SaveBlueprintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
