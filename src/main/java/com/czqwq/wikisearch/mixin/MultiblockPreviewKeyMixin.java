package com.czqwq.wikisearch.mixin;

import net.minecraft.client.gui.GuiScreen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.wikisearch.GTNHWikiSearch;
import com.czqwq.wikisearch.MultiblockPreviewSearch;

@Mixin(GuiScreen.class)
public abstract class MultiblockPreviewKeyMixin {

    private static final Logger LOGGER = LogManager.getLogger("WikiSearch");

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"), require = 0)
    public void onPreviewSearchKey(char typedChar, int keyCode, CallbackInfo ci) {
        GuiScreen self = (GuiScreen) (Object) this;

        // 调试：所有 keyTyped 调用
        LOGGER.debug(
            "[WikiSearch] GuiScreen.keyTyped() called - Screen: {}, keyCode: {}",
            self.getClass()
                .getSimpleName(),
            keyCode);

        boolean isPreview = MultiblockPreviewSearch.isLikelyPreviewScreen(self);
        if (isPreview) {
            LOGGER.debug("[WikiSearch] Recognized as preview screen, calling handleSearchKeyPress");
            GTNHWikiSearch.handleSearchKeyPress(self, typedChar, keyCode);
        }
    }
}
