package com.sweattypalms.skyblock.core.helpers;

import net.minecraft.core.IRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.world.entity.EntityTypes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class NmsEntityTypes {

    private static volatile IRegistry entityTypeRegistry;

    private NmsEntityTypes() {
    }

    public static EntityTypes byKey(String namespace, String key) {
        return byKey(new MinecraftKey(namespace, key));
    }

    public static EntityTypes byKey(String key) {
        if (key.contains(":")) {
            return byKey(new MinecraftKey(key));
        }
        return byKey(new MinecraftKey("minecraft", key));
    }

    public static EntityTypes byKey(MinecraftKey key) {
        IRegistry registry = getEntityTypeRegistry();
        Object value = registry.a(key);
        if (!(value instanceof EntityTypes entityTypes)) {
            throw new IllegalStateException("Could not resolve EntityTypes for key: " + key);
        }
        return entityTypes;
    }

    private static IRegistry getEntityTypeRegistry() {
        IRegistry cached = entityTypeRegistry;
        if (cached != null) {
            return cached;
        }

        synchronized (NmsEntityTypes.class) {
            if (entityTypeRegistry != null) {
                return entityTypeRegistry;
            }

            MinecraftKey probe = new MinecraftKey("minecraft", "enderman");

            for (Field field : BuiltInRegistries.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!IRegistry.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                field.setAccessible(true);
                try {
                    Object registryObj = field.get(null);
                    if (!(registryObj instanceof IRegistry registry)) {
                        continue;
                    }

                    Object probeValue;
                    try {
                        probeValue = registry.a(probe);
                    } catch (Throwable ignored) {
                        continue;
                    }

                    if (probeValue instanceof EntityTypes) {
                        entityTypeRegistry = registry;
                        return registry;
                    }
                } catch (IllegalAccessException ignored) {
                    // ignored
                }
            }

            throw new IllegalStateException("Failed to locate EntityTypes registry in BuiltInRegistries");
        }
    }
}

