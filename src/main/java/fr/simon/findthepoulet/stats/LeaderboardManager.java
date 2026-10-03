package fr.simon.findthepoulet.stats;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.util.Locs;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hologrammes de classement (entités "text display" vanilla).
 * Ils ne sont pas sauvegardés dans le monde : le plugin les recrée dès que le chunk est chargé.
 */
public final class LeaderboardManager {

    public static final class Board {
        final String id;
        final Stat stat;
        final Location location;
        TextDisplay display;

        Board(String id, Stat stat, Location location) {
            this.id = id;
            this.stat = stat;
            this.location = location;
        }

        public String id() { return id; }
        public Stat stat() { return stat; }
        public Location location() { return location; }
    }

    private final FindThePoulet plugin;
    private final File file;
    private final NamespacedKey key;
    private final Map<String, Board> boards = new LinkedHashMap<>();

    public LeaderboardManager(FindThePoulet plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
        this.key = new NamespacedKey(plugin, "leaderboard");
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, 20L * 20);
    }

    public void load() {
        boards.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("boards");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            Stat stat = Stat.parse(root.getString(id + ".stat"));
            Location l = Locs.load(root, id + ".location");
            if (stat != null && l != null) boards.put(id, new Board(id, stat, l));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board b : boards.values()) {
            yaml.set("boards." + b.id + ".stat", b.stat.key());
            Locs.save(yaml, "boards." + b.id + ".location", b.location);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder leaderboards.yml : " + ex.getMessage());
        }
    }

    public Board create(Stat stat, Location at) {
        int n = 1;
        while (boards.containsKey("lb" + n)) n++;
        Board b = new Board("lb" + n, stat, at.clone());
        boards.put(b.id, b);
        save();
        update(b);
        return b;
    }

    public List<Board> all() {
        return new ArrayList<>(boards.values());
    }

    /** Supprime le classement le plus proche (dans un rayon donné). */
    public Board removeNear(Location l, double radius) {
        Board best = null;
        double bestD = radius * radius;
        for (Board b : boards.values()) {
            if (!b.location.getWorld().equals(l.getWorld())) continue;
            double d = b.location.distanceSquared(l);
            if (d <= bestD) {
                bestD = d;
                best = b;
            }
        }
        if (best != null) {
            if (best.display != null && best.display.isValid()) best.display.remove();
            boards.remove(best.id);
            save();
        }
        return best;
    }

    public void refresh() {
        for (Board b : boards.values()) update(b);
    }

    private void update(Board b) {
        if (b.location.getWorld() == null || !b.location.isChunkLoaded()) return;
        if (b.display == null || !b.display.isValid()) {
            // Nettoie un éventuel ancien hologramme resté au même endroit
            for (Entity e : b.location.getWorld().getNearbyEntities(b.location, 0.5, 0.5, 0.5)) {
                if (e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) e.remove();
            }
            TextDisplay d = b.location.getWorld().spawn(b.location, TextDisplay.class);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(260);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            d.getPersistentDataContainer().set(key, PersistentDataType.STRING, b.id);
            b.display = d;
        }
        b.display.text(Msg.mm(text(b.stat)));
    }

    private String text(Stat stat) {
        StringBuilder sb = new StringBuilder("<gold><bold>★ " + stat.label().toUpperCase() + " ★</bold>\n<gray>Find The Poulet\n");
        List<Map.Entry<String, Integer>> top = plugin.stats().top(stat, 10);
        if (top.isEmpty()) sb.append("\n<gray>Personne pour l'instant...");
        String[] colors = {"<gold>", "<white>", "<#cd7f32>"};
        for (int i = 0; i < top.size(); i++) {
            String c = i < 3 ? colors[i] : "<yellow>";
            sb.append("\n").append(c).append("#").append(i + 1).append(" <white>").append(top.get(i).getKey())
                    .append(" <dark_gray>- ").append(c).append(top.get(i).getValue());
        }
        return sb.toString();
    }

    public void removeAll() {
        for (Board b : boards.values()) {
            if (b.display != null && b.display.isValid()) b.display.remove();
            b.display = null;
        }
    }
}
