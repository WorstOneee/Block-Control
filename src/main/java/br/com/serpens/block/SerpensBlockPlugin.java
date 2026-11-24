package br.com.serpens.block;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import br.com.serpens.block.hook.CarbonHook;
import br.com.serpens.block.hook.VoiceHook;

public final class SerpensBlockPlugin extends JavaPlugin {

    private volatile Settings settings;
    private BlockStore store;
    private BlockPolicy policy;
    private CarbonHook carbon;
    private VoiceHook voice;

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

        getLogger().info("Loaded block lists.");
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
