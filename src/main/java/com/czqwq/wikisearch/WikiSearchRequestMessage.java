package com.czqwq.wikisearch;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class WikiSearchRequestMessage implements IMessage {

    private String itemName;

    public WikiSearchRequestMessage() {}

    public WikiSearchRequestMessage(String itemName) {
        this.itemName = itemName;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, itemName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        itemName = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<WikiSearchRequestMessage, IMessage> {

        @Override
        public IMessage onMessage(WikiSearchRequestMessage message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final String name = message.itemName;

            // Notify the player that search is in progress
            IChatComponent searching = new ChatComponentText(
                EnumChatFormatting.GOLD + "[WikiSearch] " + EnumChatFormatting.RESET + "正在搜索 \""
                    + EnumChatFormatting.YELLOW
                    + name
                    + EnumChatFormatting.RESET
                    + "\"...");
            player.addChatMessage(searching);

            // Run HTTP search on a background thread to avoid blocking the server
            Thread thread = new Thread(() -> WikiSearchFetcher.search(name, player), "WikiSearch-" + name);
            thread.setDaemon(true);
            thread.start();
            return null;
        }
    }
}
