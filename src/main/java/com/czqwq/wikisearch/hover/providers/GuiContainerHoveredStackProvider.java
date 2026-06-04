package com.czqwq.wikisearch.hover.providers;

import java.lang.reflect.Method;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.czqwq.wikisearch.hover.HoveredStackContext;
import com.czqwq.wikisearch.hover.HoveredStackProvider;

/**
 * Uses reflection to call NEI's GuiContainerManager.getStackMouseOver() so that
 * the mod works even when NEI is absent (e.g. in the dev-run environment).
 */
public final class GuiContainerHoveredStackProvider implements HoveredStackProvider {

    /** Lazily resolved; null means NEI is not present or the method was not found. */
    private static Method getStackMouseOver;
    private static boolean resolved;

    private static Method resolveMethod() {
        if (resolved) {
            return getStackMouseOver;
        }
        resolved = true;
        try {
            Class<?> mgr = Class.forName("codechicken.nei.guihook.GuiContainerManager");
            getStackMouseOver = mgr.getMethod("getStackMouseOver", GuiContainer.class);
        } catch (ReflectiveOperationException ignored) {
            // NEI not present – leave getStackMouseOver null.
        }
        return getStackMouseOver;
    }

    @Override
    public ItemStack find(HoveredStackContext context) {
        GuiScreen screen = context.getScreen();
        if (!(screen instanceof GuiContainer)) {
            return null;
        }
        Method method = resolveMethod();
        if (method == null) {
            return null;
        }
        try {
            return (ItemStack) method.invoke(null, screen);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
