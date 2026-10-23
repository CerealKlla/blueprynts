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
 * Server-to-client: the currently-open Construction Box menu's full per-item requirement list
 * (supplied/required, not just the shortfall) plus which item is actually blocking further build
 * progress right now -- 2026-10-22, replacing the previous missing-items-only version of this
 * payload (same day, same box, see decisions.md) after direct user design: "'Cobblestone 394/394,
 * Stone Stair 15/54' that'll help, and we could even show 'Next Needed: Stone Stair,' then it's
 * really obvious why it's stalled." Sent via {@code ConstructionBoxMenu#broadcastChanges}, same
 * trigger/diffing shape as before. {@code nextNeededItemId} is an empty string, never {@code null},
 * when nothing is currently blocking (see {@code ConstructionBoxBlockEntity#nextNeededItemId}'s own
 * doc) -- a plain empty-string sentinel rather than a nullable codec, matching this mod's existing
 * list-carrying payloads' own "keep the wire shape simple" convention.
 */
public record SyncConstructionBoxRequirementsPayload(List<String> itemIds, List<Integer> suppliedAmounts,
                                                       List<Integer> requiredAmounts, String nextNeededItemId)
        implements CustomPacketPayload {

    public static final Type<SyncConstructionBoxRequirementsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "sync_construction_box_requirements"));

    public static final StreamCodec<ByteBuf, SyncConstructionBoxRequirementsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SyncConstructionBoxRequirementsPayload::itemIds,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), SyncConstructionBoxRequirementsPayload::suppliedAmounts,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), SyncConstructionBoxRequirementsPayload::requiredAmounts,
            ByteBufCodecs.STRING_UTF8, SyncConstructionBoxRequirementsPayload::nextNeededItemId,
            SyncConstructionBoxRequirementsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
