package com.czqwq.wikisearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
@SuppressWarnings("unused")
public class LateMixinPlugin implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.wikisearch.late.json";
    }

    @Override
    public @NotNull List<String> getMixins(Set<String> loadedMods) {
        return new ArrayList<>(Arrays.asList("GuiRecipeKeyDownMixin"));
    }
}
