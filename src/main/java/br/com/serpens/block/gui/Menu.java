package br.com.serpens.block.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import br.com.serpens.block.SerpensBlockPlugin;
import net.kyori.adventure.text.Component;

/**
 * Base of every panel screen. Fixed parts (header, navigation) show at once; the content
 * slots appear in a diagonal wave, one diagonal every {@code reveal-ticks}. The wave task
 * only lives while it runs (a few ticks) and is cancelled when the menu closes.
 */
public abstract class Menu implements InventoryHolder {

    protected final SerpensBlockPlugin plugin;
    protected final Player viewer;
    protected final Panel panel;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private final Map<Integer, ItemStack> reveal = new HashMap<>();
    private BukkitTask task;

    protected Menu(SerpensBlockPlugin plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.panel = plugin.panel();
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Fills the slots; called once before the menu opens. */
    protected abstract void build();

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    /** Content slot that appears with the opening wave. */
    protected void reveal(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        reveal.put(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    /** Fills every empty slot of the given rows with the transparent pack filler. */
    protected void frame(int... rows) {
        ItemStack filler = panel.filler();
        if (filler == null) {
            return;
        }
        for (int row : rows) {
            for (int slot = row * 9; slot < row * 9 + 9; slot++) {
                if (inventory.getItem(slot) == null && !reveal.containsKey(slot)) {
                    inventory.setItem(slot, filler);
                }
            }
        }
    }

    public void open() {
        build();
        viewer.openInventory(inventory);
        if (reveal.isEmpty()) {
            return;
        }
        if (panel.revealTicks <= 0) {
            reveal.forEach(inventory::setItem);
            return;
        }
        List<Integer> order = new ArrayList<>(reveal.keySet());
        order.sort(Comparator.comparingInt((Integer s) -> s / 9 + s % 9).thenComparingInt(s -> s));
        int last = order.isEmpty() ? 0 : diagonal(order.get(order.size() - 1));
        int first = diagonal(order.get(0));
        int[] step = {first};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!viewer.isOnline() || viewer.getOpenInventory().getTopInventory() != inventory) {
                stop();
                return;
            }
            for (int slot : order) {
                if (diagonal(slot) == step[0]) {
                    inventory.setItem(slot, reveal.get(slot));
                }
            }
            if (++step[0] > last) {
                stop();
            }
        }, 1L, panel.revealTicks);
    }

    private static int diagonal(int slot) {
        return slot / 9 + slot % 9;
    }

    /** Puts any content the wave has not shown yet and stops the wave. */
    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
            reveal.forEach((slot, item) -> {
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, item);
                }
            });
        }
    }

    void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    /** Opens another menu on the next tick (never inside the click event itself). */
    protected void go(Menu next) {
        stop();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                next.open();
            }
        });
    }

    protected void close() {
        panel.sound(viewer, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.2f);
        viewer.closeInventory();
    }

    protected void clickSound() {
        panel.sound(viewer, Sound.UI_BUTTON_CLICK, 1.4f);
    }

    /** Page helper: 28 heads in the four middle rows, two columns of margin. */
    protected static final int[] LIST_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43,
    };

    protected static int pages(int size) {
        return Math.max(1, (size + LIST_SLOTS.length - 1) / LIST_SLOTS.length);
    }
}
