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
 * Server-to-client: opens a real vanilla {@code BookViewScreen} filled with {@code pages} --
 * reusing that screen directly (see decompiled {@code BookViewScreen.BookAccess(List<Component>)})
 * rather than any real {@code ItemStack}, so the ledger is genuinely readable in-game without ever
 * existing as a removable item anywhere (the explicit "can't be removed from the box" request this
 * feature was built for). {@code pages} is already paginated server-side -- see {@code
 * ConstructionBoxBlock#openLedger}'s own doc for the page-size budget.
 */
public record OpenLedgerPayload(List<String> pages) implements CustomPacketPayload {

    public static final Type<OpenLedgerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "open_ledger"));

    public static final StreamCodec<ByteBuf, OpenLedgerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenLedgerPayload::pages,
            OpenLedgerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
