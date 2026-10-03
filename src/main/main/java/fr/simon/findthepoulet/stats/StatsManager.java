package fr.simon.findthepoulet.stats;

import fr.simon.findthepoulet.FindThePoulet;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Statistiques des joueurs, sauvegardées dans plugins/FindThePoulet/stats.yml. */
public final class StatsManager {

    private final FindThePoulet plugin;
    private final File file;
    private final Map<UUID, EnumMap<Stat, Integer>> data = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private boolean dirty;

    public StatsManager(FindThePoulet plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 1200L, 1200L);
    }

    public void load() {
        data.clear();
        names.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                names.put(id, s.getString("name", "?"));
                EnumMap<Stat, Integer> map = new EnumMap<>(Stat.class);
                for (Stat st : Stat.values()) {
                    int v = s.getInt(st.key(), 0);
                    if (v != 0) map.put(st, v);
                }
                data.put(id, map);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, EnumMap<Stat, Integer>> e : data.entrySet()) {
            String p = "players." + e.getKey() + ".";
            yaml.set(p + "name", names.getOrDefault(e.getKey(), "?"));
            for (Map.Entry<Stat, Integer> s : e.getValue().entrySet()) yaml.set(p + s.getKey().key(), s.getValue());
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder stats.yml : " + ex.getMessage());
        }
    }

    public void add(Player p, Stat stat) {
        add(p, stat, 1);
    }

    public void add(Player p, Stat stat, int amount) {
        names.put(p.getUniqueId(), p.getName());
        data.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Stat.class)).merge(stat, amount, Integer::sum);
        dirty = true;
    }

    public int get(UUID id, Stat stat) {
        EnumMap<Stat, Integer> m = data.get(id);
        return m == null ? 0 : m.getOrDefault(stat, 0);
    }

    /** Recherche un joueur par pseudo (même hors ligne) parmi ceux qui ont des stats. */
    public UUID find(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    public String name(UUID id) {
        return names.getOrDefault(id, "?");
    }

    /** Classement : pseudo → valeur, du meilleur au moins bon (valeurs nulles exclues). */
    public List<Map.Entry<String, Integer>> top(Stat stat, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (UUID id : data.keySet()) {
            int v = get(id, stat);
            if (v > 0) list.add(new AbstractMap.SimpleEntry<>(name(id), v));
        }
        list.sort((a, b) -> b.getValue() != a.getValue().intValue()
                ? Integer.compare(b.getValue(), a.getValue())
                : a.getKey().toLowerCase(Locale.ROOT).compareTo(b.getKey().toLowerCase(Locale.ROOT)));
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
