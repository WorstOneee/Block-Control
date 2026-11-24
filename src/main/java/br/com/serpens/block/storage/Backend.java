package br.com.serpens.block.storage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Where block lists are persisted. All methods run on the store's single writer thread. */
public interface Backend {

    /** Every block list, owner to targets. */
    Map<UUID, Set<UUID>> loadAll() throws Exception;

    void add(UUID owner, UUID target, Map<UUID, Set<UUID>> snapshot) throws Exception;

    void remove(UUID owner, UUID target, Map<UUID, Set<UUID>> snapshot) throws Exception;

    /**
     * A value that changes whenever any server changes the data, or -1 when the
     * backend is not shared and never needs a reload.
     */
    long revision() throws Exception;

    void close();

    String name();
}
