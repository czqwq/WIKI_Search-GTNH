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
        if (GTNHWikiSearch.key != null && keyCode == GTNHWikiSearch.key.getKeyCode()) {
            ItemStack stack = GuiContainerManager.getStackMouseOver((GuiContainer) (Object) this);
            if (stack != null) {
                GTNHWikiSearch.search(stack);
            }
        }
    }
}
