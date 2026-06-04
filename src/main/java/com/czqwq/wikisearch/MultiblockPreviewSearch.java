package com.czqwq.wikisearch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Reflection bridge for multiblock preview GUIs from StructureLib/BlockRenderer. */
public final class MultiblockPreviewSearch {

    private static final Logger LOGGER = LogManager.getLogger("WikiSearch");

    private MultiblockPreviewSearch() {}

    public static boolean isLikelyPreviewScreen(GuiScreen screen) {
        String className = screen.getClass()
            .getName()
            .toLowerCase(Locale.ROOT);

        // 检查标准预览界面标记
        boolean isPreview = className.contains("structurelib") || className.contains("multiblock")
            || className.contains("construct")
            || className.contains("hologram")
            || className.contains("blockrenderer");

        // 调试日志：记录所有 GUI 类的名称
        if (isPreview) {
            LOGGER.debug(
                "[WikiSearch] Detected preview screen: {}",
                screen.getClass()
                    .getName());
        }

        return isPreview;
    }

    public static ItemStack findHoveredStack(GuiScreen screen, int mouseX, int mouseY) {
        ItemStack fromMethod = findByMethod(screen, mouseX, mouseY);
        if (fromMethod != null) {
            return fromMethod;
        }

        return findByField(screen);
    }

    private static ItemStack findByMethod(GuiScreen screen, int mouseX, int mouseY) {
        Class<?> type = screen.getClass();
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                try {
                    method.setAccessible(true);
                    Class<?>[] params = method.getParameterTypes();
                    Object value;
                    if (params.length == 0) {
                        value = method.invoke(screen);
                    } else if (params.length == 2 && params[0] == int.class && params[1] == int.class) {
                        value = method.invoke(screen, mouseX, mouseY);
                    } else {
                        continue;
                    }

                    ItemStack stack = extractItemStack(value);
                    if (stack != null) {
                        return stack;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Keep scanning candidates across versions/obfuscation states.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ItemStack findByField(GuiScreen screen) {
        Class<?> type = screen.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    ItemStack stack = extractItemStack(field.get(screen));
                    if (stack != null) {
                        return stack;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Best-effort fallback only.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ItemStack extractItemStack(Object value) {
        if (value instanceof ItemStack) {
            return (ItemStack) value;
        }
        if (value == null) {
            return null;
        }

        if (value instanceof List<?>) {
            for (Object element : (List<?>) value) {
                ItemStack stack = extractItemStack(element);
                if (stack != null) {
                    return stack;
                }
            }
            return null;
        }

        ItemStack fromMethod = invokeNoArgStackMethod(value, "getItemStack", "getStack", "item", "stack", "getResult");
        if (fromMethod != null) {
            return fromMethod;
        }

        Class<?> type = value.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName()
                    .toLowerCase(Locale.ROOT);
                if (!name.contains("item") && !name.contains("stack")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    ItemStack stack = extractItemStack(field.get(value));
                    if (stack != null) {
                        return stack;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Continue probing potential wrappers.
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    private static ItemStack invokeNoArgStackMethod(Object value, String... candidates) {
        for (String methodName : candidates) {
            try {
                Method method = value.getClass()
                    .getMethod(methodName);
                method.setAccessible(true);
                ItemStack stack = extractItemStack(method.invoke(value));
                if (stack != null) {
                    return stack;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next method candidate.
            }
        }
        return null;
    }
}
