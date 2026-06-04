package com.czqwq.wikisearch.hover.providers;

import net.minecraft.item.ItemStack;

import com.czqwq.wikisearch.NeiPanelSearch;
import com.czqwq.wikisearch.hover.HoveredStackContext;
import com.czqwq.wikisearch.hover.HoveredStackProvider;

public final class NeiPanelHoveredStackProvider implements HoveredStackProvider {

    @Override
    public ItemStack find(HoveredStackContext context) {
        return NeiPanelSearch.findHoveredStack(context.getMouseX(), context.getMouseY());
    }
}
