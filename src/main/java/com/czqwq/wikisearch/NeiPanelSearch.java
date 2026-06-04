package com.czqwq.wikisearch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import net.minecraft.item.ItemStack;

/** Reflection helper for NEI panel hover lookup. */
public final class NeiPanelSearch {

    private NeiPanelSearch() {}

    public static ItemStack findHoveredStack(int mouseX, int mouseY) {
        try {
            Class<?> panelsClass = Class.forName("codechicken.nei.ItemPanels");
            for (Field field : panelsClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object panel = field.get(null);
                if (panel == null) {
                    continue;
                }

                ItemStack stack = invokePanelHoverLookup(panel, mouseX, mouseY);
                if (stack != null) {
                    return stack;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // NEI internals vary by version; ignore when unavailable.
        }
        return null;
    }

    private static ItemStack invokePanelHoverLookup(Object panel, int mouseX, int mouseY) {
        for (Method method : panel.getClass()
            .getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 2 || params[0] != int.class || params[1] != int.class) {
                continue;
            }

            String name = method.getName()
                .toLowerCase(Locale.ROOT);
            if (!name.contains("stack") && !name.contains("mouse")
                && !name.contains("hover")
                && !name.contains("item")) {
                continue;
            }

            try {
                Object value = method.invoke(panel, mouseX, mouseY);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Continue trying alternate method names/signatures.
            }
        }
        return null;
    }
}
