package br.com.serpens.block.gui;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import br.com.serpens.block.SerpensBlockPlugin;

/** Confirms a block (from the picker) or an unblock (from the list). */
public final class ConfirmMenu extends Menu {

    private final UUID target;
    private final String name;
    private final boolean blocking;
    /** Page of the screen to return to on cancel. */
    private final int returnPage;

    public ConfirmMenu(SerpensBlockPlugin plugin, Player viewer, UUID target, String name, boolean blocking, int returnPage) {
        super(plugin, viewer, 3, plugin.panel().title(blocking ? "confirm-block" : "confirm-unblock", 3, name));
        this.target = target;
        this.name = name;
        this.blocking = blocking;
        this.returnPage = returnPage;
    }

    @Override
    protected void build() {
        reveal(13, panel.head(target, name, panel.text("head-name", name),
                List.of(Bukkit.getPlayer(target) != null ? panel.text("head-online", name) : panel.text("head-offline", name)),
                false), null);
        if (blocking) {
            set(11, panel.icon("confirmar", panel.text("confirm-block-name", name), panel.lore("confirm-block-lore", name)),
                    e -> confirm());
        } else {
            set(11, panel.icon("desbloquear", panel.text("confirm-unblock-name", name), panel.lore("confirm-unblock-lore", name)),
                    e -> confirm());
        }
        set(15, panel.icon("cancelar", panel.text("cancel-name", name), List.of()), e -> {
            panel.sound(viewer, Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.8f);
            go(blocking ? new PickerMenu(plugin, viewer, returnPage) : new MainMenu(plugin, viewer, returnPage, null));
        });
        frame(0, 1, 2);
    }

    private void confirm() {
        if (blocking) {
            if (plugin.commands().block(viewer, target)) {
                panel.sound(viewer, Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f);
                panel.sound(viewer, Sound.BLOCK_ANVIL_LAND, 1.8f);
                go(new MainMenu(plugin, viewer, 0, target));
            } else {
                panel.sound(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f);
                go(new MainMenu(plugin, viewer, 0, null));
            }
        } else {
            if (plugin.commands().unblock(viewer, target)) {
                panel.sound(viewer, Sound.BLOCK_IRON_DOOR_OPEN, 1.2f);
                panel.sound(viewer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f);
            }
            go(new MainMenu(plugin, viewer, returnPage, null));
        }
    }
}
