package fr.simon.findthepoulet.zone;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.Region;
import fr.simon.findthepoulet.util.Outline;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Zones protégées (lobby / spawn), sauvegardées dans zones.yml. */
public final class ZoneManager {

    private final FindThePoulet plugin;
    private final File file;
    private final Map<String, ProtectedZone> zones = new LinkedHashMap<>();
    /** Coins sélectionnés avec l'outil lobby : [0] = coin 1, [1] = coin 2. */
    private final Map<UUID, Location[]> selections = new HashMap<>();
    /** Zones affichées en particules pour un admin, jusqu'à un instant donné. */
    private final Map<UUID, Map.Entry<String, Long>> showing = new HashMap<>();

    public ZoneManager(FindThePoulet plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, this::drawSelections, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::clearMonsters, 40L, 40L);
    }

    // ================================================================ stockage

    public void load() {
        zones.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("zones");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            Region r = new Region(s.getString("world", "world"), s.getInt("min-x"), s.getInt("min-z"), s.getInt("max-x"), s.getInt("max-z"));
            ProtectedZone z = new ProtectedZone(name, r);
            for (ProtectedZone.Flag f : ProtectedZone.Flag.values()) z.set(f, s.getBoolean("flags." + f.key(), true));
            zones.put(name.toLowerCase(Locale.ROOT), z);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ProtectedZone z : zones.values()) {
            String p = "zones." + z.name() + ".";
            Region r = z.region();
            yaml.set(p + "world", r.world());
            yaml.set(p + "min-x", r.minX());
            yaml.set(p + "min-z", r.minZ());
            yaml.set(p + "max-x", r.maxX());
            yaml.set(p + "max-z", r.maxZ());
            for (ProtectedZone.Flag f : ProtectedZone.Flag.values()) yaml.set(p + "flags." + f.key(), z.has(f));
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder zones.yml : " + ex.getMessage());
        }
    }

    // ================================================================ accès

    public List<ProtectedZone> all() { return new ArrayList<>(zones.values()); }

    public ProtectedZone get(String name) {
        return name == null ? null : zones.get(name.toLowerCase(Locale.ROOT));
    }

    public void remove(String name) {
        zones.remove(name.toLowerCase(Locale.ROOT));
        save();
    }

    /** Zone qui contient ce point et qui a ce réglage activé (sinon null). */
    public ProtectedZone at(Location l, ProtectedZone.Flag flag) {
        if (l == null || l.getWorld() == null) return null;
        for (ProtectedZone z : zones.values()) {
            if (z.has(flag) && z.region().contains(l)) return z;
        }
        return null;
    }

    public boolean is(Location l, ProtectedZone.Flag flag) {
        return at(l, flag) != null;
    }

    public ProtectedZone overlapping(Region r) {
        for (ProtectedZone z : zones.values()) if (z.region().overlaps(r)) return z;
        return null;
    }

    // ================================================================ sélection avec l'outil

    public Location[] selection(UUID id) {
        return selections.computeIfAbsent(id, k -> new Location[2]);
    }

    public void clearSelection(UUID id) {
        selections.remove(id);
    }

    public Region selectedRegion(UUID id) {
        Location[] s = selections.get(id);
        if (s == null || s[0] == null || s[1] == null || !s[0].getWorld().equals(s[1].getWorld())) return null;
        return new Region(s[0].getWorld().getName(),
                Math.min(s[0].getBlockX(), s[1].getBlockX()), Math.min(s[0].getBlockZ(), s[1].getBlockZ()),
                Math.max(s[0].getBlockX(), s[1].getBlockX()), Math.max(s[0].getBlockZ(), s[1].getBlockZ()));
    }

    public ProtectedZone create(String name, Region r) {
        ProtectedZone z = new ProtectedZone(name, r);
        zones.put(name.toLowerCase(Locale.ROOT), z);
        save();
        return z;
    }

    public String nextName() {
        if (get("spawn") == null) return "spawn";
        int n = 2;
        while (get("zone" + n) != null) n++;
        return "zone" + n;
    }

    public void show(Player p, ProtectedZone z) {
        showing.put(p.getUniqueId(), Map.entry(z.name(), System.currentTimeMillis() + 15_000));
    }

    private void drawSelections() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Region sel = selectedRegion(p.getUniqueId());
            if (sel != null) Outline.draw(p, sel.world(), sel.minX(), sel.minZ(), sel.maxX(), sel.maxZ(), Particle.END_ROD);
            else {
                Location[] s = selections.get(p.getUniqueId());
                if (s != null) for (Location c : s) {
                    if (c != null && c.getWorld().equals(p.getWorld()))
                        p.spawnParticle(Particle.END_ROD, c.clone().add(0.5, 1.2, 0.5), 3, 0.1, 0.3, 0.1, 0);
                }
            }
            Map.Entry<String, Long> sh = showing.get(p.getUniqueId());
            if (sh != null) {
                ProtectedZone z = get(sh.getKey());
                if (z == null || sh.getValue() < now) showing.remove(p.getUniqueId());
                else {
                    Region r = z.region();
                    Outline.draw(p, r.world(), r.minX(), r.minZ(), r.maxX(), r.maxZ(), Particle.HAPPY_VILLAGER);
                }
            }
        }
    }

    /** Retire les monstres entrés dans les zones "monstres bloqués" (chunks chargés uniquement). */
    private void clearMonsters() {
        for (ProtectedZone z : zones.values()) {
            if (!z.has(ProtectedZone.Flag.NO_MOBS)) continue;
            Region r = z.region();
            World w = Bukkit.getWorld(r.world());
            if (w == null) continue;
            BoundingBox box = new BoundingBox(r.minX(), w.getMinHeight(), r.minZ(), r.maxX() + 1, w.getMaxHeight(), r.maxZ() + 1);
            for (Entity e : w.getNearbyEntities(box, en -> en instanceof Enemy)) {
                if (e.customName() == null) e.remove(); // les monstres nommés (déco) sont gardés
            }
        }
    }
}
