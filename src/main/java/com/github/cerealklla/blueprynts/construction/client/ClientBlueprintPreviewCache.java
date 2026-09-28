package com.github.cerealklla.blueprynts.construction.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.blueprynts.BluepryntsMod;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Client-only (genuinely touches {@code Minecraft}/texture registration -- never referenced from
 * common code; see {@code ClientConstructionRequests} for the zero-client-import bridge that hands
 * this class its work). Avoids re-fetching a Blueprint's preview image from the server on every
 * screen open: an in-memory map of already-registered textures for this session, backed by an
 * on-disk cache under {@code FMLPaths.GAMEDIR/blueprynts/preview_cache/} that survives between
 * client sessions (the same {@code FMLPaths.GAMEDIR} API already proven working server-side by
 * {@code BlueprintStorage} -- it's a per-process instance path, not server-specific).
 *
 * <p>Freshness is mtime-based, mirroring the exact same pattern BluepryntImager's own {@code
 * BlueprintFileProcessor} already uses server-side: the server tells the client each Blueprint's
 * current preview mtime (in {@code OpenConstructionSiteScreenPayload}, no image bytes); this cache
 * only needs to fetch actual bytes for an entry it's missing or stale on.
 */
public final class ClientBlueprintPreviewCache {

    private static final Path CACHE_ROOT = FMLPaths.GAMEDIR.get().resolve("blueprynts").resolve("preview_cache");
    private static final Map<String, CachedTexture> MEMORY = new ConcurrentHashMap<>();

    private record CachedTexture(long mtime, Identifier textureId) {
    }

    private ClientBlueprintPreviewCache() {
    }

    /**
     * @return a registered texture {@link Identifier} if a copy at least as fresh as {@code
     * requiredMtime} is already cached (memory or disk) -- ready to {@code blit} immediately. Empty
     * means the caller should send a {@code RequestBlueprintPreviewPayload} and wait for {@link
     * #store} to be called once the response arrives.
     */
    public static Optional<Identifier> get(String name, String variant, long requiredMtime) {
        String key = key(name, variant);
        CachedTexture cached = MEMORY.get(key);
        if (cached != null && cached.mtime() >= requiredMtime) {
            return Optional.of(cached.textureId());
        }

        Path imageFile = imageFileFor(name, variant);
        long diskMtime = readMtimeMarker(name, variant);
        if (diskMtime < requiredMtime || !Files.exists(imageFile)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(imageFile);
            Identifier textureId = registerTexture(name, variant, bytes);
            MEMORY.put(key, new CachedTexture(diskMtime, textureId));
            return Optional.of(textureId);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Called once a {@code BlueprintPreviewImagePayload} arrives -- persists to disk and registers the texture. */
    public static void store(String name, String variant, long mtime, byte[] pngBytes) {
        try {
            Files.createDirectories(CACHE_ROOT);
            Files.write(imageFileFor(name, variant), pngBytes);
            Files.writeString(mtimeMarkerFor(name, variant), Long.toString(mtime), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Worst case this Blueprint gets re-fetched next time -- not fatal, just less caching.
        }
        Identifier textureId = registerTexture(name, variant, pngBytes);
        MEMORY.put(key(name, variant), new CachedTexture(mtime, textureId));
    }

    private static Identifier registerTexture(String name, String variant, byte[] pngBytes) {
        Identifier textureId = Identifier.fromNamespaceAndPath(BluepryntsMod.MODID,
                "preview/" + sanitizeForIdentifier(name) + "_" + variant);
        try {
            NativeImage image = NativeImage.read(pngBytes);
            DynamicTexture texture = new DynamicTexture(() -> "blueprynts preview: " + name, image);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode preview image for '" + name + "' (" + variant + ")", e);
        }
        return textureId;
    }

    private static long readMtimeMarker(String name, String variant) {
        try {
            return Long.parseLong(Files.readString(mtimeMarkerFor(name, variant), StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return -1L;
        }
    }

    private static Path imageFileFor(String name, String variant) {
        return CACHE_ROOT.resolve(sanitize(name) + "-" + variant + ".png");
    }

    private static Path mtimeMarkerFor(String name, String variant) {
        return CACHE_ROOT.resolve(sanitize(name) + "-" + variant + ".mtime");
    }

    private static String key(String name, String variant) {
        return name + "|" + variant;
    }

    /** Same sanitization as {@code BlueprintStorage} -- Blueprint names are player-supplied. For disk cache filenames (Windows tolerates spaces/case fine). */
    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /**
     * {@link Identifier} paths are far stricter than filenames -- only {@code [a-z0-9/._-]}
     * (confirmed the hard way: "Farm 1" crashed the client with {@code IdentifierException}, since
     * a space and an uppercase letter are both invalid). Lossy on purpose -- two names differing only
     * in case/punctuation could collide onto the same texture id, which just means one overwrites the
     * other's registration slot; harmless since only one is ever displayed at a time.
     */
    private static String sanitizeForIdentifier(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}
