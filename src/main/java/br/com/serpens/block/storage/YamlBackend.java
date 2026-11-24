package br.com.serpens.block.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** blocks.yml in the plugin folder. Only for a single server. */
public final class YamlBackend implements Backend {

    private final File file;

    public YamlBackend(File file) {
        this.file = file;
    }

    public static Map<UUID, Set<UUID>> read(File file) {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        if (!file.exists()) {
            return out;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return out;
        }
        for (String key : players.getKeys(false)) {
            UUID owner = parse(key);
            if (owner == null) {
                continue;
            }
            Set<UUID> set = new HashSet<>();
            for (String value : players.getStringList(key)) {
                UUID target = parse(value);
                if (target != null && !target.equals(owner)) {
                    set.add(target);
                }
            }
            if (!set.isEmpty()) {
                out.put(owner, set);
            }
        }
        return out;
    }

    @Override
    public Map<UUID, Set<UUID>> loadAll() {
        return read(file);
    }

    @Override
    public void add(UUID owner, UUID target, Map<UUID, Set<UUID>> snapshot) throws IOException {
        write(snapshot);
    }

    @Override
    public void remove(UUID owner, UUID target, Map<UUID, Set<UUID>> snapshot) throws IOException {
        write(snapshot);
    }

    @Override
    public long revision() {
        return -1;
    }

    @Override
    public void close() {
    }

    @Override
    public String name() {
        return "YAML (blocks.yml)";
    }

    private void write(Map<UUID, Set<UUID>> snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.forEach((owner, set) -> {
            if (!set.isEmpty()) {
                yaml.set("players." + owner, set.stream().map(UUID::toString).sorted().toList());
            }
        });
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File tmp = new File(file.getPath() + ".tmp");
        Files.writeString(tmp.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
