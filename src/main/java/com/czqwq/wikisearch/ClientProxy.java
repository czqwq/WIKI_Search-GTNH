package com.czqwq.wikisearch;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // Initialize client-local cookie config — intentionally NOT calling super.preInit()
        // so the server never touches this config file.
        Config.init(event.getSuggestedConfigurationFile());
        wikisearch.LOG.info("WikiSearch version " + Tags.VERSION + " initializing (client)");
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Register key bindings and event handler
        GTNHWikiSearch.init();
        FMLCommonHandler.instance()
            .bus()
            .register(new GTNHWikiSearch());
        // Register client-side command (runs locally, never sent to server)
        ClientCommandHandler.instance.registerCommand(new WikiSearchCommand());
    }
}
