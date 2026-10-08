package com.github.cerealklla.blueprynts.construction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Save-wide (not per-dimension) index from a Construction Box's persistent Construction ID to its
 * current {@link GlobalPos} -- the whole point of giving a box its own ID at all (see {@code
 * ConstructionBoxBlockEntity}'s doc): a caller (Settlemynts today, a future NPC-driving mod later)
 * should be able to resolve *where* a specific box is without needing its chunk already loaded.
 *
 * <p>Deliberately {@code MinecraftServer}-scoped, never {@code ServerLevel#getDataStorage()} --
 * same reasoning as Cartographyr's own {@code CartographySavedData} (that accessor is
 * dimension-scoped in this Minecraft version; using it here would silently give each dimension its
 * own disconnected copy instead of one save-wide store).
 *
 * <p>Only a position index -- the box's real state (once it has any beyond its own ID) still lives
 * on {@link ConstructionBoxBlockEntity} itself, so resolving an ID still requires that position's
 * chunk to be loaded for anything beyond "where is it" -- an honest, already-precedented limitation
 * in this suite (same as Settlemynts' own {@code GhostPlotStakeEntity} loaded-entity-only search).
 */
public final class ConstructionBoxIndex extends SavedData {

    public static final SavedDataType<ConstructionBoxIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "construction_box_index"),
            ConstructionBoxIndex::new,
            codec());

    private final Map<UUID, GlobalPos> boxes;

    ConstructionBoxIndex() {
        this(new HashMap<>());
    }

    private ConstructionBoxIndex(Map<UUID, GlobalPos> boxes) {
        this.boxes = boxes;
    }

    private record Entry(UUID id, GlobalPos pos) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
        ).apply(i, Entry::new));
    }

    private static Codec<ConstructionBoxIndex> codec() {
        return Codec.list(Entry.CODEC).xmap(
                entries -> {
                    Map<UUID, GlobalPos> map = new HashMap<>();
                    for (Entry entry : entries) {
                        map.put(entry.id(), entry.pos());
                    }
                    return new ConstructionBoxIndex(map);
                },
                data -> {
                    List<Entry> entries = new ArrayList<>(data.boxes.size());
                    data.boxes.forEach((id, pos) -> entries.add(new Entry(id, pos)));
                    return entries;
                });
    }

    public static ConstructionBoxIndex get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void put(UUID id, GlobalPos pos) {
        boxes.put(id, pos);
        setDirty();
    }

    public Optional<GlobalPos> get(UUID id) {
        return Optional.ofNullable(boxes.get(id));
    }
}
