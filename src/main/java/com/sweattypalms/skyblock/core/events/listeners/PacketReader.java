package com.sweattypalms.skyblock.core.events.listeners;

import io.netty.channel.*;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.network.PlayerConnection;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Used to read packets both IN and OUT.
 */
public class PacketReader implements Listener {

    private static <T> T getFirstFieldByType(Object instance, Class<T> type) {
        Class<?> current = instance.getClass();
        while (current != null) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (!type.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(instance);
                    if (value != null) {
                        return type.cast(value);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("Could not find field of type " + type.getName() + " in " + instance.getClass().getName());
    }

    private static Channel getChannel(Player player) {
        Object handle = ((CraftPlayer) player).getHandle();
        PlayerConnection connection = getFirstFieldByType(handle, PlayerConnection.class);
        NetworkManager networkManager = getFirstFieldByType(connection, NetworkManager.class);
        return getFirstFieldByType(networkManager, Channel.class);
    }

    @EventHandler
    public void playerJoin(PlayerJoinEvent e) {
        injectPlayer(e.getPlayer());
    }

    @EventHandler
    public void playerLeave(PlayerQuitEvent e) {
        unInjectPlayer(e.getPlayer());
    }

    public void unInjectPlayer(Player p) {
        Channel channel = getChannel(p);
        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(p.getName()) != null) {
                channel.pipeline().remove(p.getName());
            }
            return null;
        });
    }

    public void injectPlayer(Player p) {
        ChannelDuplexHandler channelDuplexHandler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext channelHandlerContext, Object packet) throws Exception {
                super.channelRead(channelHandlerContext, packet);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise);
            }

        };
        ChannelPipeline pipeline = getChannel(p).pipeline();
        if (pipeline.get(p.getName()) != null) {
            pipeline.remove(p.getName());
        }
        pipeline.addBefore("packet_handler", p.getName(), channelDuplexHandler);
    }
}
