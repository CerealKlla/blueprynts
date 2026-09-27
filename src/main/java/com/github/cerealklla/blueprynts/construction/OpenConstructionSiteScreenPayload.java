package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: opens/refreshes {@code ConstructionSiteScreen} with a full snapshot of a
 * Construction Site's current state, so the client makes no further server queries to render it --
 * same shape as Settlemynts' {@code OpenFoundingScreenPayload}. Resent after every server-side
 * mutation (options changed, Begin Design/Construction, Save/Load) so the open screen always
 * reflects the authoritative state, rather than the client predicting it.
 */
public record OpenConstructionSiteScreenPayload(
        BlockPos sitePos,
        String phase,
        String sizeClass,
        int tier,
        String blueprintTypeId,
        List<String> availableBlueprintTypeIds,
        List<String> availableBlueprintTypeLabels,
        int markedColumnCount,
        List<String> savedBlueprintNames)
        implements CustomPacketPayload {

    public static final Type<OpenConstructionSiteScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "open_construction_site_screen"));

    public static final StreamCodec<ByteBuf, OpenConstructionSiteScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenConstructionSiteScreenPayload::sitePos,
            ByteBufCodecs.STRING_UTF8, OpenConstructionSiteScreenPayload::phase,
            ByteBufCodecs.STRING_UTF8, OpenConstructionSiteScreenPayload::sizeClass,
            ByteBufCodecs.VAR_INT, OpenConstructionSiteScreenPayload::tier,
            ByteBufCodecs.STRING_UTF8, OpenConstructionSiteScreenPayload::blueprintTypeId,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenConstructionSiteScreenPayload::availableBlueprintTypeIds,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenConstructionSiteScreenPayload::availableBlueprintTypeLabels,
            ByteBufCodecs.VAR_INT, OpenConstructionSiteScreenPayload::markedColumnCount,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenConstructionSiteScreenPayload::savedBlueprintNames,
            OpenConstructionSiteScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
