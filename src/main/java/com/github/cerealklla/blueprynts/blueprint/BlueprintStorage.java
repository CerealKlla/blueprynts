package com.github.cerealklla.blueprynts.blueprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Global, file-based store of every saved {@link BlueprintRecord} -- one JSON file per Blueprint,
 * living outside any world save (under the game instance root, not a {@code saves/<world>} folder)
 * so wiping/recreating the world/server never loses them, and available from every
 * dimension/world alike (replacing the old per-dimension {@code SavedData} approach).
 *
 * <p>Directory layout mirrors the Blueprint's own Status/Type/Tier, per the user's explicit request:
 * <pre>
 * blueprynts/blueprints/&lt;Status&gt;/&lt;Type label&gt;/Tier &lt;N&gt;/&lt;Blueprint Name&gt; - &lt;Author&gt;.json
 * </pre>
 * Not a cache -- every read/write goes straight to disk. Blueprint counts are expected to be small
 * (authored content, not per-player data), so a full directory walk on load/list is acceptable.
 */
public final class BlueprintStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";

    private static final BlueprintStorage INSTANCE = new BlueprintStorage();

    private final Path root;

    private BlueprintStorage() {
        this.root = FMLPaths.GAMEDIR.get().resolve("blueprynts").resolve("blueprints");
    }

    public static BlueprintStorage get() {
        return INSTANCE;
    }

    public void save(BlueprintRecord record) {
        // Remove any previous copy first (e.g. a re-save after the Type/Tier changed, or a status
        // move) so it never lingers as a stale duplicate under the old path.
        deleteExisting(record.name());

        Path dir = directoryFor(record);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileNameFor(record));
            JsonElement json = BlueprintRecord.CODEC.encodeStart(JsonOps.INSTANCE, record)
                    .getOrThrow(msg -> new IOException("Failed to encode Blueprint '" + record.name() + "': " + msg));
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Blueprint '" + record.name() + "'", e);
        }
    }

    public Optional<BlueprintRecord> load(String name) {
        return findFile(name).map(this::readRecord);
    }

    public List<String> listNames() {
        return listNames(record -> true);
    }

    /** Same as {@link #listNames()}, restricted to records matching {@code filter} -- e.g. a Building Supply Box's own zone/tier constraints. */
    public List<String> listNames(java.util.function.Predicate<BlueprintRecord> filter) {
        List<String> names = new ArrayList<>();
        forEachRecord((path, record) -> {
            if (filter.test(record)) {
                names.add(record.name());
            }
        });
        return names;
    }

    /**
     * Whether BluepryntImager (a separate standalone program, no shared dependency -- see its own
     * context repo) has generated preview images for this Blueprint yet, and how fresh they are.
     * {@code -1} means missing. The two variants are always generated together by that program, but
     * this doesn't assume that -- it checks each independently.
     */
    public PreviewAvailability previewAvailability(String name) {
        Optional<Path> jsonPath = findFile(name);
        if (jsonPath.isEmpty()) {
            return new PreviewAvailability(PreviewAvailability.MISSING, PreviewAvailability.MISSING);
        }
        return new PreviewAvailability(
                mtimeOrMissing(previewPath(jsonPath.get(), "full")),
                mtimeOrMissing(previewPath(jsonPath.get(), "small")));
    }

    /** @return the raw PNG bytes for the given Blueprint's preview image, or empty if it doesn't exist. */
    public Optional<byte[]> readPreviewBytes(String name, String variant) {
        return findFile(name)
                .map(jsonPath -> previewPath(jsonPath, variant))
                .filter(Files::exists)
                .map(path -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read preview image for '" + name + "' (" + variant + ")", e);
                    }
                });
    }

    /** {@code variant} is {@code "full"} (the 1024x768 detail image) or {@code "small"} (the smaller "Minimal" variant). */
    private static Path previewPath(Path jsonPath, String variant) {
        String suffix = "small".equals(variant) ? ".small.png" : ".png";
        return jsonPath.resolveSibling(withoutExtension(jsonPath) + suffix);
    }

    private static String withoutExtension(Path jsonPath) {
        String name = jsonPath.getFileName().toString();
        return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }

    private static long mtimeOrMissing(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : PreviewAvailability.MISSING;
        } catch (IOException e) {
            return PreviewAvailability.MISSING;
        }
    }

    /** {@code -1} for either field means that variant doesn't exist yet. */
    public record PreviewAvailability(long fullMtime, long smallMtime) {
        public static final long MISSING = -1L;
    }

    private Path directoryFor(BlueprintRecord record) {
        String typeLabel = BlueprintTypeRegistry.get(record.blueprintTypeId())
                .map(BlueprintType::label)
                .orElse(record.blueprintTypeId().getPath());
        return root.resolve(sanitize(record.status().label()))
                .resolve(sanitize(typeLabel))
                .resolve("Tier " + record.tier());
    }

    private String fileNameFor(BlueprintRecord record) {
        return sanitize(record.name()) + " - " + sanitize(record.author()) + EXTENSION;
    }

    /** Blueprint names/authors are player-supplied -- keep them filesystem-safe without silently colliding. */
    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Optional<Path> findFile(String name) {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(EXTENSION))
                    .filter(p -> {
                        BlueprintRecord record = readRecord(p);
                        return record != null && record.name().equals(name);
                    })
                    .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to search Blueprint store", e);
        }
    }

    private void deleteExisting(String name) {
        findFile(name).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to remove previous copy of Blueprint '" + name + "'", e);
            }
        });
    }

    private void forEachRecord(BiConsumer<Path, BlueprintRecord> consumer) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(EXTENSION)).forEach(path -> {
                BlueprintRecord record = readRecord(path);
                if (record != null) {
                    consumer.accept(path, record);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to list Blueprint store", e);
        }
    }

    private BlueprintRecord readRecord(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            return BlueprintRecord.CODEC.parse(JsonOps.INSTANCE, element)
                    .getOrThrow(msg -> new IOException("Failed to decode " + file + ": " + msg));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Blueprint file " + file, e);
        }
    }
}
