package com.czqwq.wikisearch.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.wikisearch.GTNHWikiSearch;

import codechicken.nei.guihook.GuiContainerManager;

@Mixin(GuiContainer.class)
public abstract class GUIKeyDownMixin extends GuiScreen {

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"))
    public void onKeyInput(char typedChar, int keyCode, CallbackInfo ci) {
        if (GTNHWikiSearch.key != null) {
            GTNHWikiSearch.LOGGER.debug("Key binding exists, key code: " + GTNHWikiSearch.key.getKeyCode());
            if (keyCode == GTNHWikiSearch.key.getKeyCode()) {
                GTNHWikiSearch.LOGGER.debug("Key match found");
                // Use GuiContainerManager.getStackMouseOver to support container slots, NEI item panel,
                // bookmark panel, and all other sources.
                ItemStack stack = GuiContainerManager.getStackMouseOver((GuiContainer) (Object) this);
                if (stack != null) {
                    GTNHWikiSearch.LOGGER.debug("Item found: " + stack.getDisplayName());
                    GTNHWikiSearch.search(stack);
                } else {
                    GTNHWikiSearch.LOGGER.debug("No item under mouse");
                }
            }
        } else {
            GTNHWikiSearch.LOGGER.debug("Key binding is null");
        }
    }
}
