package com.czqwq.wikisearch.hover;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public final class HoveredStackContext {

    private final GuiScreen screen;
    private final int mouseX;
    private final int mouseY;

    private HoveredStackContext(GuiScreen screen, int mouseX, int mouseY) {
        this.screen = screen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public static HoveredStackContext from(GuiScreen screen, Minecraft mc, int rawMouseX, int rawMouseY) {
        int guiMouseX = rawMouseX * screen.width / mc.displayWidth;
        int guiMouseY = screen.height - rawMouseY * screen.height / mc.displayHeight - 1;
        return new HoveredStackContext(screen, guiMouseX, guiMouseY);
    }

    public GuiScreen getScreen() {
        return screen;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }
}
