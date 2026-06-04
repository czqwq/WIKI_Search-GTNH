package com.czqwq.wikisearch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.czqwq.wikisearch.hover.HoveredStackContext;
import com.czqwq.wikisearch.hover.HoveredStackResolver;
import com.czqwq.wikisearch.hover.providers.GuiContainerHoveredStackProvider;
import com.czqwq.wikisearch.hover.providers.NeiPanelHoveredStackProvider;
import com.czqwq.wikisearch.hover.providers.PreviewReflectionHoveredStackProvider;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class GTNHWikiSearch {
    // 虽然重写了很多方法,但是还是要感谢McModSearchRebornAgain的开源
    // 相关地址https://github.com/yiyuyan/McModSearchRebornAgain

    public static Logger LOGGER = LogManager.getLogger();

    @SideOnly(Side.CLIENT)
    public static KeyBinding key;

    @SideOnly(Side.CLIENT)
    private static final HoveredStackResolver STACK_RESOLVER = new HoveredStackResolver()
        .register(new GuiContainerHoveredStackProvider())
        .register(new NeiPanelHoveredStackProvider())
        .register(new PreviewReflectionHoveredStackProvider());

    public static void init() {
        registerKeyBindings();
    }

    @SideOnly(Side.CLIENT)
    private static void registerKeyBindings() {
        if (key == null) {
            key = new KeyBinding("key.open", Keyboard.KEY_HOME, "key.gui.search");
            ClientRegistry.registerKeyBinding(key);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (key == null) {
            return;
        }

        while (key.isPressed()) {
            tryTriggerSearch(Minecraft.getMinecraft().currentScreen);
        }
    }

    /** GUI fallback for screens where Forge KeyInputEvent does not fire consistently. */
    @SideOnly(Side.CLIENT)
    public static void onGuiKeyboardEvent(GuiScreen screen) {
        if (key == null || screen == null || !Keyboard.getEventKeyState()) {
            return;
        }

        int eventKey = Keyboard.getEventKey();
        int keyCode = eventKey == 0 ? Keyboard.getEventCharacter() + 256 : eventKey;
        if (keyCode == key.getKeyCode()) {
            tryTriggerSearch(screen);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void tryTriggerSearch(GuiScreen screen) {
        if (screen == null) {
            return;
        }

        HoveredStackContext context = HoveredStackContext
            .from(screen, Minecraft.getMinecraft(), Mouse.getX(), Mouse.getY());
        ItemStack hovered = STACK_RESOLVER.resolve(context);
        if (hovered != null) {
            search(hovered);
        }
    }

    /**
     * Unified keyboard event handler for all mixin injection points.
     * This method centralizes the search key press logic to avoid code duplication.
     */
    @SideOnly(Side.CLIENT)
    public static void handleSearchKeyPress(GuiScreen screen, char typedChar, int keyCode) {
        if (key == null || screen == null || keyCode != key.getKeyCode()) {
            return;
        }

        LOGGER.debug(
            "[WikiSearch] Key pressed on screen: {}",
            screen.getClass()
                .getName());
        tryTriggerSearch(screen);
    }

    /** Start an async wiki search for the given item stack. Runs entirely on the client. */
    @SideOnly(Side.CLIENT)
    public static void search(ItemStack stack) {
        String displayName = stack.getDisplayName();
        Thread thread = new Thread(() -> WikiSearchFetcher.fetchAndDisplay(displayName), "WikiSearch-" + displayName);
        thread.setDaemon(true);
        thread.start();
    }
}
