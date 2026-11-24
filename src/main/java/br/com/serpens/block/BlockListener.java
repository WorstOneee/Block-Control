package br.com.serpens.block;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Teleport requests and any other "command player" listed in the config. Runs before the
 * command reaches its plugin, so it works with EssentialsX, HuskHomes, CMI and others.
 */
public final class BlockListener implements Listener {

    private final SerpensBlockPlugin plugin;

    public BlockListener(SerpensBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Settings s = plugin.settings();
        if (s.targetCommands.isEmpty()) {
            return;
        }
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length < 2 || !s.targetCommands.contains(Settings.normalize(parts[0]))) {
            return;
        }
        Player sender = event.getPlayer();
        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null) {
            target = Bukkit.getPlayer(parts[1]);
        }
        // Do not reveal vanished players: let the command plugin answer as usual.
        if (target == null || !sender.canSee(target)) {
            return;
        }
        BlockPolicy.Contact contact = plugin.policy().contact(sender.getUniqueId(), target.getUniqueId());
        if (contact != BlockPolicy.Contact.OK) {
            event.setCancelled(true);
            s.send(sender, BlockPolicy.messageKey(contact), target.getName());
        }
    }
}
