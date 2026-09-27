package com.github.cerealklla.blueprynts.construction;

import java.lang.reflect.Field;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sets a freshly-created {@link Display.BlockDisplay}'s block state -- {@code setBlockState} itself
 * is private on that class, and its synced-data accessor is a private static field, so this
 * resolves it once via reflection (cached) and writes it directly into the entity's own (inherited,
 * protected) {@code SynchedEntityData}. Direct port of Settlemynts' {@code founding.GhostBlockDisplays}
 * (no code-sharing dependency between the two mods; same small helper, copied) -- see that class's
 * own doc for why this is done via the synced-data field rather than {@code Entity#load(ValueInput)}.
 */
public final class GhostBlockDisplays {

    private static volatile EntityDataAccessor<BlockState> blockStateAccessor;

    private GhostBlockDisplays() {
    }

    public static void setBlockState(Display.BlockDisplay entity, BlockState state) {
        entity.getEntityData().set(blockStateAccessor(), state);
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<BlockState> blockStateAccessor() {
        EntityDataAccessor<BlockState> accessor = blockStateAccessor;
        if (accessor == null) {
            synchronized (GhostBlockDisplays.class) {
                accessor = blockStateAccessor;
                if (accessor == null) {
                    try {
                        Field field = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
                        field.setAccessible(true);
                        accessor = (EntityDataAccessor<BlockState>) field.get(null);
                        blockStateAccessor = accessor;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not resolve Display.BlockDisplay's block state accessor", e);
                    }
                }
            }
        }
        return accessor;
    }
}
