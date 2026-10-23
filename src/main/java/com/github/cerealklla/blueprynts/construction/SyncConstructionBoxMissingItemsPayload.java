package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: the currently-open Construction Box menu's real per-item shortfall, 2026-10-22
 * -- the known, flagged gap from the same-day funding-model rework ({@code ConstructionBoxScreen}'s
 * own class doc: "deliberately shows only the aggregate fundedPercent() now, not a per-item list,"
 * since there's no cheap way to sync an arbitrary-length list through {@code ContainerData}). Real
 * live report, same day, confirmed not a cosmetic nitpick: "it doesn't show me the required
 * materials anywhere" -- a player has no way to know what to manually deposit without this. Sent via
 * {@code ConstructionBoxMenu#broadcastChanges} (vanilla's own per-tick "is anything open-menu-visible
 * stale" hook, the same mechanism {@code ContainerData} itself already rides on -- not a new poll),
 * only when the shortfall has actually changed since the last send. Two parallel lists (item id as a
 * plain string, amount) rather than a single list of a composite record -- same shape every other
 * list-carrying payload in this mod already uses (see {@code OpenConstructionBoxPickerPayload}).
 */
public record SyncConstructionBoxMissingItemsPayload(List<String> itemIds, List<Integer> amounts) implements CustomPacketPayload {

    public static final Type<SyncConstructionBoxMissingItemsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "sync_construction_box_missing_items"));

    public static final StreamCodec<ByteBuf, SyncConstructionBoxMissingItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SyncConstructionBoxMissingItemsPayload::itemIds,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), SyncConstructionBoxMissingItemsPayload::amounts,
            SyncConstructionBoxMissingItemsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
