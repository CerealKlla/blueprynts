package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: sets a Construction Site's pending Size/Tier/Blueprint Type -- only valid while IDLE. */
public record SetConstructionSiteOptionsPayload(BlockPos sitePos, String sizeClass, int tier, String blueprintTypeId)
        implements CustomPacketPayload {

    public static final Type<SetConstructionSiteOptionsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "set_construction_site_options"));

    public static final StreamCodec<ByteBuf, SetConstructionSiteOptionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), SetConstructionSiteOptionsPayload::sitePos,
            ByteBufCodecs.STRING_UTF8, SetConstructionSiteOptionsPayload::sizeClass,
            ByteBufCodecs.VAR_INT, SetConstructionSiteOptionsPayload::tier,
            ByteBufCodecs.STRING_UTF8, SetConstructionSiteOptionsPayload::blueprintTypeId,
            SetConstructionSiteOptionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
