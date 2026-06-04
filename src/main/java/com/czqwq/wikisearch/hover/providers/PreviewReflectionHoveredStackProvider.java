package com.czqwq.wikisearch.hover.providers;

import net.minecraft.item.ItemStack;

import com.czqwq.wikisearch.MultiblockPreviewSearch;
import com.czqwq.wikisearch.hover.HoveredStackContext;
import com.czqwq.wikisearch.hover.HoveredStackProvider;

public final class PreviewReflectionHoveredStackProvider implements HoveredStackProvider {

    @Override
    public ItemStack find(HoveredStackContext context) {
        if (!MultiblockPreviewSearch.isLikelyPreviewScreen(context.getScreen())) {
            return null;
        }

        return MultiblockPreviewSearch.findHoveredStack(context.getScreen(), context.getMouseX(), context.getMouseY());
    }
}
