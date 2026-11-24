package br.com.serpens.block.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import br.com.serpens.block.BlockCommands;
import br.com.serpens.block.SerpensBlockPlugin;
import br.com.serpens.block.Settings;
import net.kyori.adventure.text.Component;

/** The player's block list: header with what blocking covers, the heads, and navigation. */
public final class MainMenu extends Menu {

    private final int page;
    /** Just blocked: shown first, with a glint. May be null. */
    private final UUID highlight;

    public MainMenu(SerpensBlockPlugin plugin, Player viewer, int page, UUID highlight) {
        super(plugin, viewer, 6, plugin.panel().title("main", 6, null));
        this.page = page;
        this.highlight = highlight;
    }

    @Override
    protected void build() {
        Settings s = plugin.settings();
        UUID me = viewer.getUniqueId();
        List<UUID> blocked = new ArrayList<>(plugin.store().blockedBy(me));
        blocked.sort(Comparator.comparing((UUID u) -> !u.equals(highlight))
                .thenComparing(u -> BlockCommands.nameOf(u).toLowerCase()));

        // Header: what a block covers on this server, and the summary.
        feature(1, "chat", s.chat);
        feature(2, "tell", s.privateMessages && plugin.carbonActive());
        set(4, panel.icon("escudo", panel.text("summary-name", null),
                panel.lore("summary-lore", null, Panel.tag("count", blocked.size()), Panel.tag("max", s.maxBlocks))), null);
        feature(6, "tpa", s.teleport && !s.targetCommands.isEmpty());
        feature(7, "voz", s.voice && plugin.voiceActive());

        int pages = pages(blocked.size());
        int current = Math.min(page, pages - 1);
        int from = current * LIST_SLOTS.length;
        for (int i = 0; i < LIST_SLOTS.length && from + i < blocked.size(); i++) {
            UUID target = blocked.get(from + i);
            String name = BlockCommands.nameOf(target);
            boolean isNew = target.equals(highlight);
            List<Component> lore = new ArrayList<>();
            lore.add(Bukkit.getPlayer(target) != null ? panel.text("head-online", name) : panel.text("head-offline", name));
            if (isNew) {
                lore.add(panel.text("head-new", name));
            }
            lore.add(Component.empty());
            lore.add(panel.text("head-unblock-hint", name));
            ItemStack head = panel.head(target, name, panel.text("head-name", name), lore, isNew);
            int back = current;
            reveal(LIST_SLOTS[i], head, e -> {
                clickSound();
                go(new ConfirmMenu(plugin, viewer, target, name, false, back));
            });
        }
        if (blocked.isEmpty()) {
            reveal(22, panel.icon("vazio", panel.text("empty-name", null), panel.lore("empty-lore", null)), null);
        }

        // Navigation.
        List<Component> pageLore = panel.lore("page-lore", null, Panel.tag("page", current + 1), Panel.tag("pages", pages));
        if (current > 0) {
            set(45, panel.icon("anterior", panel.text("previous-name", null), pageLore), e -> {
                clickSound();
                go(new MainMenu(plugin, viewer, current - 1, null));
            });
        }
        set(47, panel.icon("ajuda", panel.text("help-name", null), panel.lore("help-lore", null)), null);
        set(49, panel.icon("bloquear", panel.text("add-name", null), panel.lore("add-lore", null)), e -> {
            clickSound();
            go(new PickerMenu(plugin, viewer, 0));
        });
        set(51, panel.icon("fechar", panel.text("close-name", null), List.of()), e -> close());
        if (current < pages - 1) {
            set(53, panel.icon("proximo", panel.text("next-name", null), pageLore), e -> {
                clickSound();
                go(new MainMenu(plugin, viewer, current + 1, null));
            });
        }
        frame(0, 5);
    }

    private void feature(int slot, String id, boolean active) {
        List<Component> lore = new ArrayList<>(panel.lore("feature-" + id + "-lore", null));
        lore.add(Component.empty());
        lore.add(panel.text(active ? "feature-on" : "feature-off", null));
        set(slot, panel.icon(id, panel.text("feature-" + id, null), lore), null);
    }

    /** Opens the panel from a command, with its opening sound. */
    public static void openFromCommand(SerpensBlockPlugin plugin, Player player) {
        plugin.panel().sound(player, Sound.BLOCK_ENDER_CHEST_OPEN, 1.3f);
        new MainMenu(plugin, player, 0, null).open();
    }
}
