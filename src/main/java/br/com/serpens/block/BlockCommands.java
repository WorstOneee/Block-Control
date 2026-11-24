package br.com.serpens.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class BlockCommands implements TabExecutor {

    private final SerpensBlockPlugin plugin;

    public BlockCommands(SerpensBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Settings s = plugin.settings();
        String name = command.getName();

        if (name.equals("serpensblock")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reload();
                s = plugin.settings();
                s.send(sender, "reloaded");
            } else {
                sender.sendMessage("/serpensblock reload");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            s.send(sender, "only-players");
            return true;
        }

        switch (name) {
            case "block" -> block(player, args, s);
            case "unblock" -> unblock(player, args, s);
            case "blocklist" -> list(player, s);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void block(Player player, String[] args, Settings s) {
        if (args.length != 1) {
            s.send(player, "usage-block");
            return;
        }
        OfflinePlayer target = resolve(args[0]);
        if (target == null) {
            s.send(player, "not-found", args[0]);
            return;
        }
        String targetName = nameOf(target.getUniqueId());
        if (target.getUniqueId().equals(player.getUniqueId())) {
            s.send(player, "self");
            return;
        }
        Player online = target.getPlayer();
        if (online != null && online.hasPermission(BlockPolicy.EXEMPT)) {
            s.send(player, "exempt", targetName);
            return;
        }
        BlockStore store = plugin.store();
        if (store.isBlocking(player.getUniqueId(), target.getUniqueId())) {
            s.send(player, "already-blocked", targetName);
            return;
        }
        if (store.count(player.getUniqueId()) >= s.maxBlocks) {
            s.send(player, "limit", null, Settings.tag("max", s.maxBlocks));
            return;
        }
        store.add(player.getUniqueId(), target.getUniqueId());
        s.send(player, "blocked", targetName);
    }

    private void unblock(Player player, String[] args, Settings s) {
        if (args.length != 1) {
            s.send(player, "usage-unblock");
            return;
        }
        UUID owner = player.getUniqueId();
        UUID target = null;
        // Look in the player's own list first, so names of players the server
        // no longer has cached can still be unblocked.
        for (UUID uuid : plugin.store().blockedBy(owner)) {
            if (nameOf(uuid).equalsIgnoreCase(args[0])) {
                target = uuid;
                break;
            }
        }
        if (target == null) {
            OfflinePlayer resolved = resolve(args[0]);
            if (resolved != null) {
                target = resolved.getUniqueId();
            }
        }
        if (target == null || !plugin.store().remove(owner, target)) {
            s.send(player, "not-blocked", target == null ? args[0] : nameOf(target));
            return;
        }
        s.send(player, "unblocked", nameOf(target));
    }

    private void list(Player player, Settings s) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : plugin.store().blockedBy(player.getUniqueId())) {
            names.add(nameOf(uuid));
        }
        if (names.isEmpty()) {
            s.send(player, "list-empty");
            return;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        s.send(player, "list-header", null, Settings.tag("count", names.size()));
        for (String n : names) {
            player.sendMessage(s.line("list-entry", n));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            if (command.getName().equals("serpensblock") && args.length == 1) {
                return List.of("reload");
            }
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        switch (command.getName()) {
            case "block" -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p != player && player.canSee(p)
                            && !plugin.store().isBlocking(player.getUniqueId(), p.getUniqueId())
                            && p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(p.getName());
                    }
                }
            }
            case "unblock" -> {
                for (UUID uuid : plugin.store().blockedBy(player.getUniqueId())) {
                    String n = nameOf(uuid);
                    if (n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(n);
                    }
                }
            }
            default -> {
            }
        }
        out.sort(Comparator.comparing(String::toLowerCase));
        return out;
    }

    /** Online player by exact name, otherwise a player who has joined before. */
    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return cached;
        }
        return null;
    }

    static String nameOf(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString();
    }
}
