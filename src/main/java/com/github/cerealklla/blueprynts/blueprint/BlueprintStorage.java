package com.github.cerealklla.blueprynts.blueprint;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
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
 * Every individual record read/write goes straight to disk (no in-memory content cache -- a
 * Blueprint's own JSON can be large and this process doesn't need to hold every one in memory at
 * once). {@code findFile} is the one real exception: it's backed by a small persisted {@code name ->
 * relative path} index ({@link #INDEX_FILE_NAME}, see that field's own doc for the real crash this
 * fixes, 2026-10-28), not a fresh directory walk every call.
 */
public final class BlueprintStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";

    private static final BlueprintStorage INSTANCE = new BlueprintStorage();

    private final Path root;

    // Real server crash, 2026-10-28 ("A single server tick took 60.00 seconds... forcibly
    // shutdown"): findFile used to be a full Files.walk over every saved Blueprint, decoding EVERY
    // file's JSON just to compare its name, on every call -- and ConstructionBoxBlockEntity's own
    // per-tick nextNeededItemId/fundedPercent reads called it indirectly every tick for any player
    // with a Construction Box menu open (that call site now has its own cache too, see that class's
    // own doc, but this was the deeper, shared root cause every findFile caller paid for). Name-to-
    // path resolution needs no content at all once the mapping is known, so it's now a tiny on-disk
    // index (user's own suggested fix, 2026-10-28: "create a 'cache' file on disk that has the
    // summary needed... update that cache file any time a blueprint is created/modified") --
    // name/path pairs are cheap to store directly (no decoded "summary" fields needed, since a full
    // readRecord of the ONE matched file is already fast; only scanning every file to find the match
    // was ever the expensive part). Loaded once (lazily, on first real use) and kept in memory for
    // this process's lifetime, updated incrementally by every save()/deleteExisting() so it never
    // goes stale during normal operation, and self-healed by #rebuildIndexFromDisk if it's ever
    // genuinely missing/corrupt or a lookup misses something a direct file-add (outside save()) put
    // on disk.
    // Deliberately NOT a .json extension -- every file ending in EXTENSION under this tree is
    // otherwise assumed to be a real Blueprint record (forEachRecord/the fallback scan in findFile
    // both filter purely on that suffix), and this index file is a different JSON shape entirely
    // that readRecord would fail trying to decode as one.
    private static final String INDEX_FILE_NAME = "_index.idx";
    private static final Type INDEX_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private Map<String, String> nameToRelativePath;

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
            Map<String, String> index = ensureIndexLoaded();
            index.put(record.name(), root.relativize(file).toString());
            persistIndex(index);
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
     * Same selection as {@link #listNames(java.util.function.Predicate)}, but also resolves each
     * match's preview mtimes in the same single directory walk, instead of a caller looping over the
     * returned names and calling {@link #previewAvailability(String)} per name -- that used to mean
     * one extra full-store walk+decode *per Blueprint*, making every open of a picker screen O(N^2)
     * in the number of saved Blueprints (real bug, 2026-10-11: a Construction Site right-click taking
     * several seconds with the suite's accumulated test content).
     */
    public List<NameAndAvailability> listNamesWithAvailability(java.util.function.Predicate<BlueprintRecord> filter) {
        List<NameAndAvailability> results = new ArrayList<>();
        forEachRecord((path, record) -> {
            if (filter.test(record)) {
                results.add(new NameAndAvailability(
                        record.name(),
                        mtimeOrMissing(previewPath(path, "full")),
                        mtimeOrMissing(previewPath(path, "small"))));
            }
        });
        return results;
    }

    public record NameAndAvailability(String name, long fullMtime, long smallMtime) {
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
        Map<String, String> index = ensureIndexLoaded();
        String relative = index.get(name);
        if (relative != null) {
            Path indexed = root.resolve(relative);
            if (Files.exists(indexed)) {
                return Optional.of(indexed);
            }
            // Indexed path no longer exists (e.g. the file was deleted/moved outside save()) --
            // falls through to the real scan below, which also self-heals the stale entry.
            index.remove(name);
        }
        // Index miss -- either a genuinely new name, or a file added/renamed on disk outside this
        // class's own save()/deleteExisting() (e.g. hand-authored content dropped in directly, same
        // as this mod's own "Ancestors" reference Blueprints). Falls back to the original full scan,
        // but ALSO backfills whatever it finds into the index so this only ever costs a real walk
        // once per such file, not every call from here on.
        try (Stream<Path> walk = Files.walk(root)) {
            Optional<Path> found = walk.filter(p -> p.toString().endsWith(EXTENSION))
                    .filter(p -> {
                        BlueprintRecord record = readRecord(p);
                        return record != null && record.name().equals(name);
                    })
                    .findFirst();
            found.ifPresent(path -> index.put(name, root.relativize(path).toString()));
            persistIndex(index);
            return found;
        } catch (IOException e) {
            throw new RuntimeException("Failed to search Blueprint store", e);
        }
    }

    private void deleteExisting(String name) {
        findFile(name).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
                Map<String, String> index = ensureIndexLoaded();
                if (index.remove(name) != null) {
                    persistIndex(index);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to remove previous copy of Blueprint '" + name + "'", e);
            }
        });
    }

    private Path indexFile() {
        return root.resolve(INDEX_FILE_NAME);
    }

    private Map<String, String> ensureIndexLoaded() {
        if (nameToRelativePath != null) {
            return nameToRelativePath;
        }
        Path file = indexFile();
        if (Files.exists(file)) {
            try {
                Map<String, String> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), INDEX_TYPE);
                nameToRelativePath = loaded != null ? new LinkedHashMap<>(loaded) : new LinkedHashMap<>();
                return nameToRelativePath;
            } catch (IOException e) {
                // Falls through to a real rebuild below -- a corrupt/unreadable index file is
                // recoverable (everything it would contain can be re-derived from the real files),
                // never a reason to fail every Blueprint lookup.
            }
        }
        nameToRelativePath = rebuildIndexFromDisk();
        persistIndex(nameToRelativePath);
        return nameToRelativePath;
    }

    /** Real, one-time full scan -- only ever runs when no (or a corrupt) index file exists yet, e.g. the very first boot after this feature shipped, or recovering from a deleted/damaged index file. */
    private Map<String, String> rebuildIndexFromDisk() {
        Map<String, String> fresh = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return fresh;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(EXTENSION)).forEach(path -> {
                BlueprintRecord record = readRecord(path);
                if (record != null) {
                    fresh.put(record.name(), root.relativize(path).toString());
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to rebuild Blueprint index", e);
        }
        return fresh;
    }

    private void persistIndex(Map<String, String> index) {
        try {
            Files.createDirectories(root);
            Files.writeString(indexFile(), GSON.toJson(index, INDEX_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Worst case the in-memory index (already updated by the caller) just doesn't survive a
            // restart -- not fatal, the next ensureIndexLoaded() call rebuilds it from disk again.
        }
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
