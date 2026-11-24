package br.com.serpens.block;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

/** Fallback for public chat when CarbonChat is not installed. */
public final class PaperChatListener implements Listener {

    private final SerpensBlockPlugin plugin;

    public PaperChatListener(SerpensBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID sender = event.getPlayer().getUniqueId();
        BlockPolicy policy = plugin.policy();
        event.viewers().removeIf(a -> a instanceof Player p && policy.hideChat(p.getUniqueId(), sender));
    }
}
