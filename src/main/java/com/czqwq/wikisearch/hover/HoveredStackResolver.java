package com.czqwq.wikisearch.hover;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

public final class HoveredStackResolver {

    private final List<HoveredStackProvider> providers = new ArrayList<>();

    public HoveredStackResolver register(HoveredStackProvider provider) {
        providers.add(provider);
        return this;
    }

    public ItemStack resolve(HoveredStackContext context) {
        for (HoveredStackProvider provider : providers) {
            ItemStack stack = provider.find(context);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }
}
