package br.com.serpens.block;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SerpensBlockPlugin extends JavaPlugin {

    private volatile Settings settings;
    private BlockStore store;
    private BlockPolicy policy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        store = new BlockStore(new File(getDataFolder(), "blocks.yml"), getLogger());
        store.load();
        policy = new BlockPolicy(this);

        BlockCommands commands = new BlockCommands(this);
        for (String name : new String[] {"block", "unblock", "blocklist", "serpensblock"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
        }

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);

        getLogger().info("Loaded block lists.");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.close();
        }
    }

    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public BlockStore store() {
        return store;
    }

    public BlockPolicy policy() {
        return policy;
    }
}
