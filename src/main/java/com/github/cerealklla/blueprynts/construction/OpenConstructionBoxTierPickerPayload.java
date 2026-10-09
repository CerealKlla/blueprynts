package com.github.cerealklla.blueprynts.construction;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: opens {@code client.ConstructionBoxTierPickerScreen} -- the new first step
 * (added 2026-10-09) of picking/changing a Building Supply Box's Blueprint, ahead of the existing
 * Blueprint-name picker ({@code OpenConstructionBoxPickerPayload}). Lists Tiers 1 through {@code
 * allowedTier} (the box's own unlocked cap, raised via Settlemynts' "Upgrade Plot" -- see {@code
 * ConstructionBoxBlockEntity#allowedTier}'s own doc); selecting one re-requests the Blueprint
 * picker filtered to that Tier via {@link SelectConstructionBoxTierPayload}.
 */
public record OpenConstructionBoxTierPickerPayload(BlockPos boxPos, int allowedTier) implements CustomPacketPayload {

    public static final Type<OpenConstructionBoxTierPickerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "open_construction_box_tier_picker"));

    public static final StreamCodec<ByteBuf, OpenConstructionBoxTierPickerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BlockPos.CODEC), OpenConstructionBoxTierPickerPayload::boxPos,
            ByteBufCodecs.VAR_INT, OpenConstructionBoxTierPickerPayload::allowedTier,
            OpenConstructionBoxTierPickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
