package br.com.serpens.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.serpens.block.storage.Backend;

/**
 * Who blocked whom, kept fully in memory so chat, voice and command checks never wait on
 * storage. Reads are lock-free (any thread); writes update memory at once and are persisted
 * on a single background thread, in order.
 */
public final class BlockStore {

    private final Backend backend;
    private final Logger logger;
    private volatile Map<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SerpensBlock-storage");
        t.setDaemon(true);
        return t;
    });
    /** Last revision seen; MIN_VALUE forces a reload on the next successful sync. */
    private long revision = Long.MIN_VALUE;
    private boolean failing;

    public BlockStore(Backend backend, Logger logger) {
        this.backend = backend;
        this.logger = logger;
    }

    public Backend backend() {
        return backend;
    }

    /** Blocking load, used once at startup. Returns false if storage could not be read. */
    public boolean loadNow() {
        try {
            long rev = backend.revision();
            replace(backend.loadAll());
            revision = rev;
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not load block lists from " + backend.name()
                    + "; will retry on the next sync.", e);
            failing = true;
            return false;
        }
    }

    /** Queues a cheap revision check; reloads everything only if another server changed data. */
    public void sync() {
        worker.execute(() -> {
            try {
                long rev = backend.revision();
                if (rev < 0 || rev == revision) {
                    recovered();
                    return;
                }
                replace(backend.loadAll());
                revision = rev;
                recovered();
            } catch (Exception e) {
                if (!failing) {
                    failing = true;
                    logger.log(Level.WARNING, "Block list sync failed (" + backend.name() + "): " + e.getMessage());
                }
            }
        });
    }

    private void recovered() {
        if (failing) {
            failing = false;
            logger.info("Block list storage is reachable again.");
        }
    }

    private void replace(Map<UUID, Set<UUID>> data) {
        Map<UUID, Set<UUID>> fresh = new ConcurrentHashMap<>();
        data.forEach((owner, set) -> {
            Set<UUID> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(set);
            fresh.put(owner, copy);
        });
        blocks = fresh;
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
            Map<UUID, Set<UUID>> snapshot = snapshot();
            persist(() -> backend.add(owner, target, snapshot));
        }
        return changed;
    }

    public boolean remove(UUID owner, UUID target) {
        Set<UUID> set = blocks.get(owner);
        if (set == null || !set.remove(target)) {
            return false;
        }
        Map<UUID, Set<UUID>> snapshot = snapshot();
        persist(() -> backend.remove(owner, target, snapshot));
        return true;
    }

    private interface Write {
        void run() throws Exception;
    }

    private void persist(Write write) {
        worker.execute(() -> {
            try {
                write.run();
                // Our own write bumped the revision; skip reloading what we already have.
                long rev = backend.revision();
                if (rev >= 0 && revision != Long.MIN_VALUE && rev == revision + 1) {
                    revision = rev;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not save a block change to " + backend.name(), e);
            }
        });
    }

    private Map<UUID, Set<UUID>> snapshot() {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        blocks.forEach((owner, set) -> {
            if (!set.isEmpty()) {
                copy.put(owner, Set.copyOf(set));
            }
        });
        return copy;
    }

    /** Finishes pending writes and closes storage. Call on disable. */
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for block list writes.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        backend.close();
    }
}
