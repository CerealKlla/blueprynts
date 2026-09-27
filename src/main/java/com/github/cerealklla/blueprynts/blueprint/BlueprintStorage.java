package com.github.cerealklla.blueprynts.blueprint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Level-scoped persistent store of every saved {@link BlueprintRecord}, keyed by name. */
public final class BlueprintStorage extends SavedData {

    public static final SavedDataType<BlueprintStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(BluepryntsMod.MODID, "blueprints"),
            BlueprintStorage::new,
            codec());

    private final Map<String, BlueprintRecord> byName;

    private BlueprintStorage() {
        this(new HashMap<>());
    }

    private BlueprintStorage(Map<String, BlueprintRecord> byName) {
        this.byName = byName;
    }

    private static Codec<BlueprintStorage> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, BlueprintRecord.CODEC).fieldOf("blueprints").forGetter(d -> d.byName)
        ).apply(i, data -> new BlueprintStorage(new HashMap<>(data))));
    }

    public static BlueprintStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void save(BlueprintRecord record) {
        byName.put(record.name(), record);
        setDirty();
    }

    public Optional<BlueprintRecord> load(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<String> listNames() {
        return List.copyOf(byName.keySet());
    }
}
