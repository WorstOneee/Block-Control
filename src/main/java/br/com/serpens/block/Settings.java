package br.com.serpens.block;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Immutable snapshot of config.yml; swapped as a whole on reload. */
public final class Settings {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public final int maxBlocks;
    public final boolean chat;
    public final boolean chatMutual;
    public final boolean privateMessages;
    public final boolean voice;
    public final boolean voiceMutual;
    public final boolean teleport;
    /** Lower-case labels, no slash, no namespace: teleport commands plus extra commands. */
    public final Set<String> targetCommands;

    private final FileConfiguration config;
    private final String prefix;

    public Settings(FileConfiguration config) {
        this.config = config;
        this.maxBlocks = Math.max(1, config.getInt("max-blocks", 100));
        this.chat = config.getBoolean("chat.enabled", true);
        this.chatMutual = config.getBoolean("chat.mutual", false);
        this.privateMessages = config.getBoolean("private-messages.enabled", true);
        this.voice = config.getBoolean("voice.enabled", true);
        this.voiceMutual = config.getBoolean("voice.mutual", true);
        this.teleport = config.getBoolean("teleport.enabled", true);

        Set<String> commands = new HashSet<>();
        if (teleport) {
            config.getStringList("teleport.commands").forEach(c -> commands.add(normalize(c)));
        }
        config.getStringList("extra-commands").forEach(c -> commands.add(normalize(c)));
        commands.remove("");
        this.targetCommands = Set.copyOf(commands);
        this.prefix = config.getString("messages.prefix", "");
    }

    /** "/Essentials:TPA" -> "tpa". */
    public static String normalize(String label) {
        String s = label.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    public void send(Audience to, String key) {
        to.sendMessage(message(key, null));
    }

    public void send(Audience to, String key, String playerName, TagResolver... tags) {
        to.sendMessage(message(key, playerName, tags));
    }

    /**
     * {@code <player>} is substituted as text before parsing so it also works inside
     * click and hover arguments; the name is tag-escaped first.
     */
    public Component message(String key, String playerName, TagResolver... tags) {
        String raw = prefix + config.getString("messages." + key, key);
        if (playerName != null) {
            raw = raw.replace("<player>", MM.escapeTags(playerName));
        }
        return MM.deserialize(raw, tags);
    }

    public Component line(String key, String playerName) {
        String raw = config.getString("messages." + key, key);
        if (playerName != null) {
            raw = raw.replace("<player>", MM.escapeTags(playerName));
        }
        return MM.deserialize(raw);
    }

    public static TagResolver tag(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }
}
