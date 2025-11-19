package com.czqwq.wikisearch;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        // 注册按键绑定
        registerKeyBindings();
    }

    @SideOnly(Side.CLIENT)
    private static void registerKeyBindings() {
        // 确保按键绑定只注册一次
        if (key == null) {
            key = new KeyBinding("key.open", Keyboard.KEY_HOME, "key.gui.search");
            ClientRegistry.registerKeyBinding(key);
            LOGGER.info("Key binding registered: " + key.getKeyDescription());
        } else {
            LOGGER.info("Key binding already registered: " + key.getKeyDescription());
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (key != null && key.isPressed()) {
            LOGGER.info("Wiki search key pressed");
        }
    }

    public static boolean open(ItemStack stack) {
        String displayName, url;

        try {
            // 只获取物品的显示名称
            displayName = URLEncoder.encode(stack.getDisplayName(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // 构建GTNH Wiki搜索URL
        url = String.format(
            "https://gtnh.huijiwiki.com/index.php?title=特殊:搜索&search=%s&profile=default&sort=just_match",
            displayName);

        try {
            if (Desktop.isDesktopSupported() || System.getProperty("os.name")
                .contains("Windows")) {
                // Windows
                Desktop.getDesktop()
                    .browse(new URI(url));
            } else {
                Runtime runtime = Runtime.getRuntime();
                if (System.getProperty("os.name")
                    .contains("Mac")) {
                    // Mac
                    runtime.exec(new String[] { "xdg-open", "\"" + url + "\"" });
                } else {
                    // Linux和其他系统
                    runtime.exec(new String[] { "xdg-open", url });
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to open the url.");
            e.printStackTrace();
            return false;
        }
    }
}
