package com.massivecraft.factions.util;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AsyncChunkLoader {

    private static volatile Method asyncFutureMethod;
    private static volatile Method asyncConsumerMethod;
    private static volatile boolean initialized;

    private AsyncChunkLoader() {
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private static void init() {
        if (initialized) return;
        synchronized (AsyncChunkLoader.class) {
            if (initialized) return;
            try {
                asyncFutureMethod = World.class.getMethod("getChunkAtAsync", int.class, int.class);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                asyncConsumerMethod = World.class.getMethod("getChunkAtAsync", int.class, int.class, Consumer.class);
            } catch (NoSuchMethodException ignored) {
            }
            initialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    public static void load(World world, int x, int z, CompletableFuture<Chunk> future) {
        init();
        try {
            if (asyncFutureMethod != null) {
                Object result = asyncFutureMethod.invoke(world, x, z);
                if (result instanceof CompletableFuture) {
                    ((CompletableFuture<Chunk>) result).whenComplete((chunk, ex) -> {
                        if (ex != null) future.completeExceptionally(ex);
                        else future.complete(chunk);
                    });
                    return;
                }
            }
            if (asyncConsumerMethod != null) {
                Consumer<Chunk> cb = future::complete;
                asyncConsumerMethod.invoke(world, x, z, cb);
                return;
            }
        } catch (Throwable ignored) {
        }
        future.complete(world.getChunkAt(x, z));
    }
}

