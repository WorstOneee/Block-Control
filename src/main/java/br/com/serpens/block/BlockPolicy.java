package br.com.serpens.block;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The rules, in one place. Safe to call from any thread: the store is concurrent and
 * {@link Bukkit#getPlayer(UUID)} reads a concurrent map.
 */
public final class BlockPolicy {

    public static final String EXEMPT = "serpensblock.exempt";

    public enum Contact {
        /** Allowed. */
        OK,
        /** The target has blocked the sender. */
        TARGET_BLOCKED_YOU,
        /** The sender has blocked the target. */
        YOU_BLOCKED_TARGET
    }

    private final SerpensBlockPlugin plugin;

    public BlockPolicy(SerpensBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private BlockStore store() {
        return plugin.store();
    }

    public boolean exempt(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null && p.hasPermission(EXEMPT);
    }

    /** Should {@code viewer} stop seeing public chat from {@code sender}? */
    public boolean hideChat(UUID viewer, UUID sender) {
        Settings s = plugin.settings();
        return s.chat && hidden(viewer, sender, s.chatMutual);
    }

    /** Should {@code listener} stop hearing {@code speaker} on voice chat? */
    public boolean muteVoice(UUID listener, UUID speaker) {
        Settings s = plugin.settings();
        return s.voice && hidden(listener, speaker, s.voiceMutual);
    }

    private boolean hidden(UUID viewer, UUID sender, boolean mutual) {
        if (viewer == null || sender == null || viewer.equals(sender)) {
            return false;
        }
        if (store().isBlocking(viewer, sender) && !exempt(sender)) {
            return true;
        }
        return mutual && store().isBlocking(sender, viewer) && !exempt(viewer);
    }

    /** Direct contact (private message, teleport request): blocked in both directions. */
    public Contact contact(UUID from, UUID to) {
        if (from == null || to == null || from.equals(to) || exempt(from) || exempt(to)) {
            return Contact.OK;
        }
        if (store().isBlocking(to, from)) {
            return Contact.TARGET_BLOCKED_YOU;
        }
        if (store().isBlocking(from, to)) {
            return Contact.YOU_BLOCKED_TARGET;
        }
        return Contact.OK;
    }

    public static String messageKey(Contact contact) {
        return contact == Contact.TARGET_BLOCKED_YOU ? "target-blocked-you" : "you-blocked-target";
    }
}
