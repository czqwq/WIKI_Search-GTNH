package com.czqwq.wikisearch;

import java.awt.Desktop;
import java.net.URI;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

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

    public static void init() {
        registerKeyBindings();
    }

    @SideOnly(Side.CLIENT)
    private static void registerKeyBindings() {
        if (key == null) {
            key = new KeyBinding("key.open", Keyboard.KEY_HOME, "key.gui.search");
            ClientRegistry.registerKeyBinding(key);
            LOGGER.debug("Key binding registered: " + key.getKeyDescription());
        } else {
            LOGGER.debug("Key binding already registered: " + key.getKeyDescription());
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (key != null && key.isPressed()) {
            LOGGER.debug("Wiki search key pressed");
        }
    }

    /** Start an async wiki search for the given item stack. Runs entirely on the client. */
    @SideOnly(Side.CLIENT)
    public static void search(ItemStack stack) {
        String displayName = stack.getDisplayName();
        LOGGER.debug("Starting wiki search for: " + displayName);
        Thread thread = new Thread(() -> WikiSearchFetcher.fetchAndDisplay(displayName), "WikiSearch-" + displayName);
        thread.setDaemon(true);
        thread.start();
    }
}
