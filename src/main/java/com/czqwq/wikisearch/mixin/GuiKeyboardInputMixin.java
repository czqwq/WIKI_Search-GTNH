package com.czqwq.wikisearch.mixin;

import net.minecraft.client.gui.GuiScreen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.wikisearch.GTNHWikiSearch;

@Mixin(GuiScreen.class)
public abstract class GuiKeyboardInputMixin {

    private static final Logger LOGGER = LogManager.getLogger("WikiSearch");

    @Inject(method = "handleKeyboardInput()V", at = @At("TAIL"), require = 0)
    private void onKeyboardInput(CallbackInfo ci) {
        if (Keyboard.getEventKeyState()) {
            int eventKey = Keyboard.getEventKey();
            int keyCode = eventKey == 0 ? Keyboard.getEventCharacter() + 256 : eventKey;
            char typedChar = Keyboard.getEventCharacter();

            LOGGER.debug("[WikiSearch] handleKeyboardInput() - keyCode: {}, typedChar: {}", keyCode, (int) typedChar);

            GTNHWikiSearch.handleSearchKeyPress((GuiScreen) (Object) this, typedChar, keyCode);
        }
    }
}
