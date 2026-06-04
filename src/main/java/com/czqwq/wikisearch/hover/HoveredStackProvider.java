package com.czqwq.wikisearch.hover;

import net.minecraft.item.ItemStack;

@FunctionalInterface
public interface HoveredStackProvider {

    ItemStack find(com.czqwq.wikisearch.hover.HoveredStackContext context);
}
