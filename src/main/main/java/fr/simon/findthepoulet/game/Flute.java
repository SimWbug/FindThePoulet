package fr.simon.findthepoulet.game;

import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/**
 * La flûte à poulet : un bâton qui prend l'apparence d'un autre objet vanilla (bambou par défaut)
 * grâce au composant "item_model" — aucun pack de textures nécessaire.
 * Sa barre de durabilité montre les utilisations restantes.
 */
public final class Flute {

    private static NamespacedKey usesKey;

    private Flute() {}

    public static void init(Plugin plugin) {
        usesKey = new NamespacedKey(plugin, "flute_uses");
    }

    public static int maxUses(FileConfiguration cfg) {
        return Math.max(1, cfg.getInt("flute.uses", 2));
    }

    public static ItemStack create(FileConfiguration cfg) {
        int max = maxUses(cfg);
        ItemStack item = new ItemStack(Material.STICK);
        String model = cfg.getString("flute.model", "bamboo").toLowerCase(Locale.ROOT);
        item.editMeta(meta -> {
            meta.setMaxStackSize(1);
            NamespacedKey modelKey = NamespacedKey.fromString(model.contains(":") ? model : "minecraft:" + model);
            if (modelKey != null) meta.setItemModel(modelKey);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, max);
            if (meta instanceof Damageable d) {
                d.setMaxDamage(max);
                d.setDamage(0);
            }
            describe(meta, max, max);
        });
        return Items.tag(item, Items.FLUTE);
    }

    public static boolean isFlute(ItemStack item) {
        return Items.FLUTE.equals(Items.tagOf(item));
    }

    public static int uses(ItemStack item) {
        Integer v = item.getItemMeta().getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    /** Retire une utilisation. @return false si la flûte est maintenant cassée (à supprimer). */
    public static boolean consume(ItemStack item, int max) {
        int left = uses(item) - 1;
        if (left <= 0) return false;
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, left);
            if (meta instanceof Damageable d) d.setDamage(max - left);
            describe(meta, left, max);
        });
        return true;
    }

    private static void describe(ItemMeta meta, int left, int max) {
        meta.displayName(Msg.item("<gold><bold>Flûte à poulet"));
        meta.lore(List.of(
                Msg.item("<gray>Joue un air que seul <yellow>le poulet</yellow>"),
                Msg.item("<gray>ne peut pas ignorer : il se met à caqueter,"),
                Msg.item("<gray>écoute d'où vient le son !"),
                Msg.item(""),
                Msg.item("<yellow>Clic droit : <white>jouer"),
                Msg.item("<gray>Utilisations : <white>" + left + "/" + max)));
    }
}
