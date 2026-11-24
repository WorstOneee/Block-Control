package br.com.serpens.block.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Snapshot of the {@code panel} config section: texts, icons and titles. Rebuilt on reload. */
public final class Panel {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Characters of the blockcontrol:gui font (see tools/gerar_assets.py). */
    private static final String SHIFT_START = "";
    private static final String SHIFT_BACK = "";
    private static final Map<Integer, String> BACKGROUNDS = Map.of(3, "", 6, "");

    public final boolean enabled;
    public final boolean pack;
    public final int revealTicks;
    public final boolean sounds;
    private final String namespace;
    private final Key font;
    private final ConfigurationSection texts;
    private final ConfigurationSection titles;
    private final ConfigurationSection materials;

    public Panel(FileConfiguration config) {
        this.enabled = config.getBoolean("panel.enabled", true);
        this.pack = config.getBoolean("panel.resource-pack", false);
        this.revealTicks = Math.max(0, config.getInt("panel.reveal-ticks", 1));
        this.sounds = config.getBoolean("panel.sounds", true);
        this.namespace = config.getString("panel.namespace", "blockcontrol");
        Key parsed;
        try {
            parsed = Key.key(config.getString("panel.font", "blockcontrol:gui"));
        } catch (RuntimeException e) {
            parsed = Key.key("blockcontrol", "gui");
        }
        this.font = parsed;
        this.texts = section(config, "panel.texts");
        this.titles = section(config, "panel.titles");
        this.materials = section(config, "panel.materials");
    }

    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection s = config.getConfigurationSection(path);
        return s != null ? s : config.createSection(path);
    }

    // ------------------------------------------------------------------ text

    private static String fill(String raw, String player) {
        return player == null ? raw : raw.replace("<player>", MM.escapeTags(player));
    }

    /** One line of item text: never italic, white unless the text says otherwise. */
    public Component text(String key, String player, TagResolver... tags) {
        return ui(texts.getString(key, key), player, tags);
    }

    public List<Component> lore(String key, String player, TagResolver... tags) {
        List<Component> out = new ArrayList<>();
        if (texts.isList(key)) {
            for (String line : texts.getStringList(key)) {
                out.add(ui(line, player, tags));
            }
        } else if (texts.isString(key)) {
            out.add(ui(texts.getString(key), player, tags));
        }
        return out;
    }

    private static Component ui(String raw, String player, TagResolver... tags) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE)
                .append(MM.deserialize(fill(raw, player), tags));
    }

    public static TagResolver tag(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Menu title; with the resource pack the background is drawn behind it through the font. */
    public Component title(String key, int rows, String player) {
        Component text = MM.deserialize(fill(titles.getString(key, key), player));
        String bg = BACKGROUNDS.get(rows);
        if (!pack || bg == null) {
            return Component.empty().color(NamedTextColor.DARK_GRAY).append(text);
        }
        return Component.text(SHIFT_START + bg + SHIFT_BACK).font(font).color(NamedTextColor.WHITE)
                .append(Component.empty().font(Key.key("minecraft", "default"))
                        .color(TextColor.color(0xFBEED5)).append(text));
    }

    // ------------------------------------------------------------------ items

    /** A panel button: resource pack model when enabled, otherwise the configured material. */
    public ItemStack icon(String id, Component name, List<Component> lore) {
        Material material = pack ? Material.PAPER : material(id);
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (pack) {
            NamespacedKey key = NamespacedKey.fromString(namespace + ":gui/" + id);
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Transparent filler (resource pack) or nothing at all without it. */
    public ItemStack filler() {
        if (!pack) {
            return null;
        }
        ItemStack item = icon("moldura", Component.empty(), List.of());
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private Material material(String id) {
        Material m = Material.matchMaterial(materials.getString(id, "PAPER"));
        return m != null && m.isItem() && !m.isAir() ? m : Material.PAPER;
    }

    /** Player head; online players keep their real skin, others are resolved by the client. */
    public ItemStack head(UUID uuid, String name, Component display, List<Component> lore, boolean glint) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        Player online = Bukkit.getPlayer(uuid);
        PlayerProfile profile = online != null ? online.getPlayerProfile() : Bukkit.createProfile(uuid, name);
        meta.setPlayerProfile(profile);
        meta.displayName(display);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    public void sound(Player player, Sound sound, float pitch) {
        if (sounds) {
            player.playSound(player.getLocation(), sound, 0.6f, pitch);
        }
    }
}
