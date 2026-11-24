package br.com.serpens.block;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Who blocked whom. Reads are lock-free so the voice chat thread and the async chat
 * thread can query it; writes save the whole file on a single background thread.
 */
public final class BlockStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SerpensBlock-save");
        t.setDaemon(true);
        return t;
    });

    public BlockStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        blocks.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID owner = parse(key);
            if (owner == null) {
                continue;
            }
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String value : players.getStringList(key)) {
                UUID target = parse(value);
                if (target != null) {
                    set.add(target);
                }
            }
            if (!set.isEmpty()) {
                blocks.put(owner, set);
            }
        }
    }

    /** True when {@code owner} has blocked {@code target}. */
    public boolean isBlocking(UUID owner, UUID target) {
        if (owner == null || target == null) {
            return false;
        }
        Set<UUID> set = blocks.get(owner);
        return set != null && set.contains(target);
    }

    public Set<UUID> blockedBy(UUID owner) {
        Set<UUID> set = blocks.get(owner);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public int count(UUID owner) {
        Set<UUID> set = blocks.get(owner);
        return set == null ? 0 : set.size();
    }

    public boolean add(UUID owner, UUID target) {
        boolean changed = blocks.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(target);
        if (changed) {
            saveAsync();
        }
        return changed;
    }

    public boolean remove(UUID owner, UUID target) {
        Set<UUID> set = blocks.get(owner);
        if (set == null || !set.remove(target)) {
            return false;
        }
        if (set.isEmpty()) {
            blocks.remove(owner, set);
        }
        saveAsync();
        return true;
    }

    private void saveAsync() {
        String snapshot = serialize();
        writer.execute(() -> write(snapshot));
    }

    /** Flushes pending writes and stops the writer thread. Call on disable. */
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for block list save.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        write(serialize());
    }

    private String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        blocks.forEach((owner, set) -> {
            if (!set.isEmpty()) {
                yaml.set("players." + owner, set.stream().map(UUID::toString).sorted().toList());
            }
        });
        return yaml.saveToString();
    }

    private synchronized void write(String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
