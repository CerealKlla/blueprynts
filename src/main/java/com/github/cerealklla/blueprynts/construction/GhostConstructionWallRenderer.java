package com.github.cerealklla.blueprynts.construction;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders {@link GhostConstructionWallEntity} via vanilla's own {@code
 * DisplayRenderer.BlockDisplayRenderer} -- that class's constructor is protected, so this is a
 * trivial subclass that just exposes it. Direct port of Settlemynts' {@code
 * founding.client.GhostBlockDisplayRenderer}.
 */
public final class GhostConstructionWallRenderer extends DisplayRenderer.BlockDisplayRenderer {

    public GhostConstructionWallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
