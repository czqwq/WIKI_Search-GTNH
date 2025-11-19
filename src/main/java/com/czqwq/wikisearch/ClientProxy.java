package com.czqwq.wikisearch;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // 初始化GTNH Wiki Search功能
        GTNHWikiSearch.init();
        FMLCommonHandler.instance()
            .bus()
            .register(new GTNHWikiSearch());
    }
}
