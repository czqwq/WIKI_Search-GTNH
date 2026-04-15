package com.czqwq.wikisearch;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class WikiSearchNetwork {

    public static SimpleNetworkWrapper CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(wikisearch.MODID);
        CHANNEL.registerMessage(WikiSearchRequestMessage.Handler.class, WikiSearchRequestMessage.class, 0, Side.SERVER);
    }
}
