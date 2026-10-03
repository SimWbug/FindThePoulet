package fr.simon.findthepoulet.game;

import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Boussole à poulet et plume de saut (la flûte est dans {@link Flute}). */
public final class Gadgets {

    private static NamespacedKey usesKey;
    private static NamespacedKey activeKey;

    private Gadgets() {}

    public static void init(Plugin plugin) {
        usesKey = new NamespacedKey(plugin, "gadget_uses");
        activeKey = new NamespacedKey(plugin, "compass_until");
    }

    // ================================================================ boussole

    public static int compassSeconds(FileConfiguration cfg) {
        return Math.max(3, cfg.getInt("compass.duration-seconds", 15));
    }

    /**
     * Une vraie boussole vanilla : son aiguille pointe réellement vers le poulet une fois activée
     * (cible "lodestone" mise à jour en continu). Avant activation, l'aiguille tourne dans le vide.
     */
    public static ItemStack compass(FileConfiguration cfg) {
        ItemStack item = new ItemStack(Material.COMPASS);
        item.editMeta(CompassMeta.class, meta -> {
            meta.setLodestoneTracked(false);
            meta.setMaxStackSize(1);
            meta.setEnchantmentGlintOverride(true);
            meta.displayName(Msg.item("<gold><bold>Boussole à poulet"));
            meta.lore(lore(
                    "<gray>Son aiguille est attirée par les plumes...",
                    "",
                    "<yellow>Clic droit : <white>l'activer",
                    "<gray>Pointe vers le poulet pendant <white>" + compassSeconds(cfg) + " s",
                    "<gray>puis elle se brise."));
        });
        return Items.tag(item, Items.COMPASS);
    }

    public static boolean isCompass(ItemStack item) {
        return Items.COMPASS.equals(Items.tagOf(item));
    }

    public static long activeUntil(ItemStack item) {
        Long v = item.getItemMeta().getPersistentDataContainer().get(activeKey, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    public static void activate(ItemStack item, long until) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(activeKey, PersistentDataType.LONG, until));
    }

    public static void point(ItemStack item, Location target) {
        item.editMeta(CompassMeta.class, meta -> {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
        });
    }

    // ================================================================ plume

    public static int featherUses(FileConfiguration cfg) {
        return Math.max(1, cfg.getInt("feather.uses", 3));
    }

    public static ItemStack feather(FileConfiguration cfg) {
        int max = featherUses(cfg);
        ItemStack item = new ItemStack(Material.FEATHER);
        item.editMeta(meta -> {
            meta.setMaxStackSize(1);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, max);
            if (meta instanceof Damageable d) {
                d.setMaxDamage(max);
                d.setDamage(0);
            }
            describeFeather(meta, max, max);
        });
        return Items.tag(item, Items.FEATHER);
    }

    public static boolean isFeather(ItemStack item) {
        return Items.FEATHER.equals(Items.tagOf(item));
    }

    /** Retire une utilisation. @return false si la plume est usée (à supprimer). */
    public static boolean consumeFeather(ItemStack item, int max) {
        Integer v = item.getItemMeta().getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER);
        int left = (v == null ? 1 : v) - 1;
        if (left <= 0) return false;
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, left);
            if (meta instanceof Damageable d) d.setDamage(max - left);
            describeFeather(meta, left, max);
        });
        return true;
    }

    private static void describeFeather(ItemMeta meta, int left, int max) {
        meta.displayName(Msg.item("<aqua><bold>Plume de saut"));
        meta.lore(lore(
                "<gray>Un bond en avant, parfait pour",
                "<gray>semer tes poursuivants avec le poulet.",
                "",
                "<yellow>Clic droit : <white>bondir",
                "<gray>Utilisations : <white>" + left + "/" + max));
    }

    private static List<net.kyori.adventure.text.Component> lore(String... lines) {
        List<net.kyori.adventure.text.Component> out = new ArrayList<>();
        for (String l : lines) out.add(Msg.item(l));
        return out;
    }
}
