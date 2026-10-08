package com.github.cerealklla.blueprynts.registration;

import com.github.cerealklla.blueprynts.BluepryntsMod;
import com.github.cerealklla.blueprynts.construction.ConstructionBoxMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** This mod's first {@link MenuType} registry -- see Yconomics' own {@code registration.ModMenus} (its Loot Bag) for the precedent this mirrors. */
public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, BluepryntsMod.MODID);

    // Passes null for the menuType param (only server-side button/slot dispatch needs it -- a menu
    // can't self-reference its own still-being-built DeferredHolder from within this initializer) and
    // no extraData -- the client rebuilds an identical empty backing container, and the funding
    // rows' actual required/supplied numbers arrive separately via ContainerData sync, not extraData.
    public static final DeferredHolder<MenuType<?>, MenuType<ConstructionBoxMenu>> CONSTRUCTION_BOX = MENU_TYPES.register(
            "construction_box",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new ConstructionBoxMenu(null, windowId, inventory)));
}
