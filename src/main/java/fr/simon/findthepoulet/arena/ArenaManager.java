package fr.simon.findthepoulet.arena;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.game.TeamMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ArenaManager {

    private final FindThePoulet plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(FindThePoulet plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Region region = s.contains("min-y")
                        ? new Region(s.getString("world"), s.getInt("min-x"), s.getInt("min-y"), s.getInt("min-z"),
                                s.getInt("max-x"), s.getInt("max-y"), s.getInt("max-z"))
                        : new Region(s.getString("world"), s.getInt("min-x"), s.getInt("min-z"), s.getInt("max-x"), s.getInt("max-z"));
                Arena arena = new Arena(s.getString("name", key), region,
                        TeamMode.parse(s.getString("mode")), s.getBoolean("pvp", true), s.getBoolean("enabled", true),
                        s.getInt("enclosure.x"), s.getInt("enclosure.y"), s.getInt("enclosure.z"),
                        s.getInt("enclosure.size", 4));
                arena.setSkin(s.getString("skin", "aleatoire"));
                arena.setFormat(fr.simon.findthepoulet.game.GameFormat.parse(s.getString("format")));
                arena.setFox(s.getBoolean("fox", false));
                arena.setKit(s.getBoolean("kit", true));
                arena.setPvpMode(fr.simon.findthepoulet.game.PvpMode.parse(s.getString("pvp-mode"), arena.pvpMode()));
                arenas.put(key(arena.name()), arena);
            } catch (Exception ex) {
                plugin.getLogger().warning("Arène invalide '" + key + "' : " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.name() + ".";
            Region r = a.region();
            yaml.set(p + "name", a.name());
            yaml.set(p + "world", r.world());
            yaml.set(p + "min-x", r.minX());
            yaml.set(p + "min-z", r.minZ());
            yaml.set(p + "max-x", r.maxX());
            yaml.set(p + "max-z", r.maxZ());
            if (!r.fullHeight()) {
                yaml.set(p + "min-y", r.minY());
                yaml.set(p + "max-y", r.maxY());
            }
            yaml.set(p + "mode", a.mode().name());
            yaml.set(p + "pvp", a.isPvp());
            yaml.set(p + "pvp-mode", a.pvpMode().name());
            yaml.set(p + "enabled", a.isEnabled());
            yaml.set(p + "skin", a.skin());
            yaml.set(p + "format", a.format().name());
            yaml.set(p + "fox", a.isFox());
            yaml.set(p + "kit", a.isKit());
            yaml.set(p + "enclosure.x", a.ex());
            yaml.set(p + "enclosure.y", a.ey());
            yaml.set(p + "enclosure.z", a.ez());
            yaml.set(p + "enclosure.size", a.enclosureSize());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder arenas.yml : " + ex.getMessage());
        }
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(key(name));
    }

    public boolean exists(String name) {
        return arenas.containsKey(key(name));
    }

    public Collection<Arena> all() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public void add(Arena arena) {
        arenas.put(key(arena.name()), arena);
        save();
    }

    public void remove(String name) {
        arenas.remove(key(name));
        save();
    }

    public Arena overlapping(Region region) {
        for (Arena a : arenas.values()) if (a.region().overlaps(region)) return a;
        return null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
