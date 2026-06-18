package com.czqwq.wikisearch.mixin.late;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.wikisearch.GTNHWikiSearch;
import com.czqwq.wikisearch.mixin.GUIKeyDownMixin;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipe;

/**
 * Fixes search key not working inside NEI's {@link GuiRecipe} and
 * BlockRenderer6343's multiblock preview GUI.
 * <p>
 * {@link GuiRecipe#keyTyped(char, int)} overrides {@link GuiContainer#keyTyped}
 * without calling {@code super.keyTyped()}, so the main
 * {@link GUIKeyDownMixin} cannot intercept key presses on these screens.
 * This mixin injects directly into {@code GuiRecipe.keyTyped} to bridge the gap.
 */
@Mixin(GuiRecipe.class)
public abstract class GuiRecipeKeyDownMixin extends GuiScreen {

    @Inject(method = "keyTyped(CI)V", at = @At("HEAD"), remap = false)
    public void onKeyInput(char typedChar, int keyCode, CallbackInfo ci) {
        if (GTNHWikiSearch.key != null && keyCode == GTNHWikiSearch.key.getKeyCode()) {
            ItemStack stack = GuiContainerManager.getStackMouseOver((GuiContainer) (Object) this);
            if (stack != null) {
                GTNHWikiSearch.search(stack);
            }
        }
    }
}
