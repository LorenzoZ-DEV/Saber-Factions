package com.massivecraft.factions.util;

import com.massivecraft.factions.FLocation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FastChunk {

    private String world;
    private long key;

    public FastChunk() {
    }

    public FastChunk(String world, int x, int z) {
        this.world = world;
        this.key = WorldUtil.encodeChunk(x, z);
    }

    public FastChunk(String world, FLocation floc) {
        this(world, floc.getIntX(), floc.getIntZ());
    }

    public FastChunk(FLocation floc) {
        this(floc.getWorldName(), floc);
    }

    public FastChunk getRelative(String world, int dx, int dz) {
        return new FastChunk(world, getX() + dx, getZ() + dz);
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return WorldUtil.getChunkX(this.key);
    }

    public int getZ() {
        return WorldUtil.getChunkZ(this.key);
    }

    public Chunk getChunk() {
        return Bukkit.getWorld(world).getChunkAt(getX(), getZ());
    }

    public CompletableFuture<Chunk> getChunkAsync() {
        World w = Bukkit.getWorld(world);
        if (w == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Chunk> future = new CompletableFuture<>();
        AsyncChunkLoader.load(w, getX(), getZ(), future);
        return future;
    }

    public void getChunkAsync(Consumer<Chunk> callback) {
        getChunkAsync().thenAccept(callback);
    }

    public static CompletableFuture<Void> preloadAll(Iterable<FastChunk> chunks) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (FastChunk fc : chunks) futures.add(fc.getChunkAsync());
        if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FastChunk fastChunk = (FastChunk) o;
        return key == fastChunk.key && world.equals(fastChunk.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, key);
    }
}
