package com.czqwq.wikisearch.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.wikisearch.GTNHWikiSearch;

@Mixin(GuiContainer.class)
public abstract class GUIKeyDownMixin {

    private static final Logger LOGGER = LogManager.getLogger("WikiSearch");

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"))
    public void onKeyInput(char typedChar, int keyCode, CallbackInfo ci) {
        LOGGER.debug("[WikiSearch] GuiContainer.keyTyped() called - keyCode: {}", keyCode);
        GTNHWikiSearch.handleSearchKeyPress((GuiContainer) (Object) this, typedChar, keyCode);
    }
}
