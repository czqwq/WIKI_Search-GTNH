package com.czqwq.wikisearch.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.czqwq.wikisearch.GTNHWikiSearch;

@Mixin(GuiContainer.class)
public abstract class GUIKeyDownMixin extends GuiScreen {

    @Shadow
    private Slot theSlot;

    @Inject(method = "keyTyped", at = @At("TAIL"))
    public void onKeyInput(char keyChar, int keyCode, CallbackInfo ci) {
        // 添加调试日志
        GTNHWikiSearch.LOGGER.info("Key typed in GUI: " + keyCode);

        // 确保按键绑定已经初始化且不为null
        if (GTNHWikiSearch.key != null) {
            GTNHWikiSearch.LOGGER.info("Key binding exists, key code: " + GTNHWikiSearch.key.getKeyCode());
            if (keyCode == GTNHWikiSearch.key.getKeyCode()) {
                GTNHWikiSearch.LOGGER.info("Key match found");
                if (this.theSlot != null) {
                    ItemStack stack = this.theSlot.getStack();
                    if (stack != null) {
                        GTNHWikiSearch.LOGGER.info("Item found: " + stack.getDisplayName());
                        if (!GTNHWikiSearch.open(stack)) {
                            GTNHWikiSearch.LOGGER.error("Can't open the item's url.");
                        }
                    } else {
                        GTNHWikiSearch.LOGGER.info("No item in slot");
                    }
                } else {
                    GTNHWikiSearch.LOGGER.info("No slot selected");
                }
            }
        } else {
            GTNHWikiSearch.LOGGER.info("Key binding is null");
        }
    }
}
