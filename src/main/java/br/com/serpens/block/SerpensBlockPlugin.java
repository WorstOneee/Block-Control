package br.com.serpens.block;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import br.com.serpens.block.gui.MenuListener;
import br.com.serpens.block.gui.Panel;
import br.com.serpens.block.hook.CarbonHook;
import br.com.serpens.block.hook.VoiceHook;
import br.com.serpens.block.storage.Backend;
import br.com.serpens.block.storage.MysqlBackend;
import br.com.serpens.block.storage.YamlBackend;

public final class SerpensBlockPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Panel panel;
    private BlockCommands commands;
    private BlockStore store;
    private BlockPolicy policy;
    private CarbonHook carbon;
    private VoiceHook voice;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        panel = new Panel(getConfig());
        Backend backend = openBackend();
        if (backend == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        store = new BlockStore(backend, getLogger());
        store.loadNow();
        startSync();
        policy = new BlockPolicy(this);

        commands = new BlockCommands(this);
        for (String name : new String[] {"block", "unblock", "blocklist", "serpensblock"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
        }

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        if (getServer().getPluginManager().isPluginEnabled("CarbonChat")) {
            try {
                carbon = new CarbonHook(this);
                carbon.register();
                getLogger().info("Hooked into CarbonChat (chat and private messages).");
            } catch (Throwable t) {
                carbon = null;
                getLogger().severe("Could not hook into CarbonChat: " + t);
            }
        } else {
            getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
            getLogger().info("CarbonChat not found; filtering vanilla chat only.");
        }

        if (getServer().getPluginManager().isPluginEnabled("voicechat")) {
            try {
                voice = VoiceHook.register(this);
                getLogger().info(voice != null ? "Hooked into Simple Voice Chat." : "Simple Voice Chat service not found.");
            } catch (Throwable t) {
                getLogger().severe("Could not hook into Simple Voice Chat: " + t);
            }
        }

        getLogger().info("Block lists stored in " + backend.name() + ".");
    }

    /** Opens the configured storage; imports blocks.yml into an empty MySQL table once. */
    private Backend openBackend() {
        File yaml = new File(getDataFolder(), "blocks.yml");
        String type = getConfig().getString("storage.type", "yaml");
        if (!type.equalsIgnoreCase("mysql")) {
            return new YamlBackend(yaml);
        }
        MysqlBackend mysql;
        try {
            mysql = new MysqlBackend(getConfig().getConfigurationSection("storage.mysql"));
            mysql.init();
        } catch (Exception e) {
            getLogger().severe("Could not connect to MySQL: " + e.getMessage());
            getLogger().severe("Fix storage.mysql in config.yml. SerpensBlock is disabled so blocks are not lost.");
            return null;
        }
        if (yaml.exists()) {
            try {
                // Merged with INSERT IGNORE, so every server's old blocks.yml can be imported.
                int rows = mysql.importAll(YamlBackend.read(yaml));
                getLogger().info("Imported " + rows + " blocks from blocks.yml into MySQL.");
                File done = new File(getDataFolder(), "blocks.yml.imported");
                if (!yaml.renameTo(done)) {
                    getLogger().warning("Could not rename blocks.yml; it is ignored while storage is mysql.");
                }
            } catch (Exception e) {
                getLogger().severe("Could not import blocks.yml into MySQL: " + e.getMessage());
            }
        }
        return mysql;
    }

    /** MySQL only: a tiny revision check off the main thread picks up changes from other servers. */
    private void startSync() {
        if (store.backend().getClass() != MysqlBackend.class) {
            return;
        }
        long seconds = Math.max(1, getConfig().getLong("storage.mysql.sync-seconds", 3));
        getServer().getScheduler().runTaskTimerAsynchronously(this, store::sync, seconds * 20, seconds * 20);
    }

    @Override
    public void onDisable() {
        if (carbon != null) {
            carbon.unregister();
        }
        if (voice != null) {
            voice.disable();
        }
        if (store != null) {
            store.close();
        }
    }

    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
        panel = new Panel(getConfig());
    }

    public boolean carbonActive() {
        return carbon != null;
    }

    public boolean voiceActive() {
        return voice != null;
    }

    public Panel panel() {
        return panel;
    }

    public BlockCommands commands() {
        return commands;
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
