package br.com.serpens.block.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import br.com.serpens.block.BlockPolicy;
import br.com.serpens.block.SerpensBlockPlugin;
import net.kyori.adventure.text.Component;

/** Online players on this server that the viewer can block. */
public final class PickerMenu extends Menu {

    private final int page;

    public PickerMenu(SerpensBlockPlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 6, plugin.panel().title("picker", 6, null));
        this.page = page;
    }

    @Override
    protected void build() {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != viewer && viewer.canSee(p) && !p.hasPermission(BlockPolicy.EXEMPT)
                    && !plugin.store().isBlocking(viewer.getUniqueId(), p.getUniqueId())) {
                candidates.add(p);
            }
        }
        candidates.sort(Comparator.comparing(p -> p.getName().toLowerCase()));

        set(4, panel.icon("bloquear", panel.text("picker-info-name", null), panel.lore("picker-info-lore", null)), null);

        int pages = pages(candidates.size());
        int current = Math.min(page, pages - 1);
        int from = current * LIST_SLOTS.length;
        for (int i = 0; i < LIST_SLOTS.length && from + i < candidates.size(); i++) {
            Player target = candidates.get(from + i);
            String name = target.getName();
            List<Component> lore = List.of(Component.empty(), panel.text("pick-hint", name));
            reveal(LIST_SLOTS[i], panel.head(target.getUniqueId(), name, panel.text("pick-name", name), lore, false), e -> {
                clickSound();
                go(new ConfirmMenu(plugin, viewer, target.getUniqueId(), name, true, current));
            });
        }
        if (candidates.isEmpty()) {
            reveal(22, panel.icon("vazio", panel.text("picker-empty-name", null), panel.lore("picker-empty-lore", null)), null);
        }

        List<Component> pageLore = panel.lore("page-lore", null, Panel.tag("page", current + 1), Panel.tag("pages", pages));
        if (current > 0) {
            set(45, panel.icon("anterior", panel.text("previous-name", null), pageLore), e -> {
                clickSound();
                go(new PickerMenu(plugin, viewer, current - 1));
            });
        }
        set(49, panel.icon("voltar", panel.text("back-name", null), List.of()), e -> {
            clickSound();
            go(new MainMenu(plugin, viewer, 0, null));
        });
        if (current < pages - 1) {
            set(53, panel.icon("proximo", panel.text("next-name", null), pageLore), e -> {
                clickSound();
                go(new PickerMenu(plugin, viewer, current + 1));
            });
        }
        frame(0, 5);
    }
}
