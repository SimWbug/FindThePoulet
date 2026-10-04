package fr.simon.findthepoulet.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class Items {

    public static final String MENU = "menu";
    public static final String LEAVE = "leave";
    public static final String ZONE = "zone";
    public static final String ENCLOS = "enclos";
    public static final String FLUTE = "flute";
    public static final String COMPASS = "compass";
    public static final String FEATHER = "feather";
    public static final String LOBBY = "lobby";

    /** Objets de jeu trouvés dans les coffres (on peut les jeter, les perdre...). */
    public static boolean isGadget(String tag) {
        return FLUTE.equals(tag) || COMPASS.equals(tag) || FEATHER.equals(tag);
    }

    private static NamespacedKey itemKey;
    private static NamespacedKey chickenKey;

    private Items() {}

    public static void init(Plugin plugin) {
        itemKey = new NamespacedKey(plugin, "item");
        chickenKey = new NamespacedKey(plugin, "chicken");
    }

    public static NamespacedKey chickenKey() {
        return chickenKey;
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static ItemStack build(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Msg.item(name));
            if (lore.length > 0) meta.lore(Arrays.stream(lore).map(Msg::item).toList());
        });
        return item;
    }

    public static ItemStack tag(ItemStack item, String id) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id));
        return item;
    }

    public static String tagOf(ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public static boolean has(Player p, String id) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (id.equals(tagOf(item))) return true;
        }
        return false;
    }

    public static void give(Player p, ItemStack item) {
        String id = tagOf(item);
        if (id != null && has(p, id)) return;
        p.getInventory().addItem(item);
    }

    public static void removeTagged(Player p, String... ids) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            String tag = tagOf(contents[i]);
            if (tag == null) continue;
            for (String id : ids) {
                if (id.equals(tag)) {
                    p.getInventory().setItem(i, null);
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- items du jeu

    public static ItemStack menuHead(FileConfiguration cfg) {
        ItemStack head = build(Material.PLAYER_HEAD, "<gold><bold>Find The Poulet",
                "<gray>Clic droit pour ouvrir le menu",
                "<gray>et rejoindre une partie !");
        String texture = cfg.getString("lobby-item.head-texture", "");
        PlayerProfile profile;
        if (texture != null && !texture.isBlank()) {
            UUID id = UUID.nameUUIDFromBytes("findthepoulet-head".getBytes(StandardCharsets.UTF_8));
            profile = Bukkit.createProfile(id, "FTPChicken");
            profile.setProperty(new ProfileProperty("textures", texture));
        } else {
            profile = Bukkit.createProfile(cfg.getString("lobby-item.head-owner", "MHF_Chicken"));
        }
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return tag(head, MENU);
    }

    public static ItemStack leaveItem() {
        return tag(build(Material.RED_BED, "<red><bold>Quitter la partie", "<gray>Clic droit pour quitter"), LEAVE);
    }

    public static ItemStack zoneTool() {
        return tag(build(Material.STICK, "<aqua><bold>Bâton de zone",
                "<gray>Clic gauche sur un bloc : <white>coin 1",
                "<gray>Clic droit sur le bloc opposé : <white>coin 2",
                "<gray>(mode taille fixe : clic droit = centre)",
                "<gray>Clic droit dans l'air : menu"), ZONE);
    }

    public static ItemStack lobbyTool() {
        return tag(build(Material.BLAZE_ROD, "<gold><bold>Outil zone lobby / spawn",
                "<gray>Clic gauche sur un bloc : <white>coin 1",
                "<gray>Clic droit sur un bloc : <white>coin 2",
                "<gray>Clic droit dans l'air : <white>menu des zones"), LOBBY);
    }

    public static ItemStack enclosTool() {
        return tag(build(Material.IRON_HOE, "<yellow><bold>Faux de l'enclos",
                "<gray>Clic droit au sol : place l'enclos",
                "<gray>(point de départ des joueurs)",
                "<gray>Clic droit dans l'air : menu"), ENCLOS);
    }
}
