package fr.simon.findthepoulet.game;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GameManager {

    private final FindThePoulet plugin;
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    /** Joueurs morts au moment de la fin de partie : on leur rend leur inventaire à la réapparition. */
    private final Map<UUID, PlayerSnapshot> pending = new HashMap<>();
    private final BukkitTask lobbyTask;

    public GameManager(FindThePoulet plugin) {
        this.plugin = plugin;
        this.lobbyTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> new ArrayList<>(games.values()).forEach(Game::lobbyTick), 20L, 20L);
    }

    public Game get(Arena arena) {
        return games.computeIfAbsent(key(arena.name()), k -> new Game(plugin, arena));
    }

    public Game existing(String arenaName) {
        return games.get(key(arenaName));
    }

    public Game of(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    void bind(UUID id, Game game) { byPlayer.put(id, game); }
    void unbind(UUID id) { byPlayer.remove(id); }

    /** Partie en cours (ou en fin) dont la zone contient cette position. */
    public Game activeAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        for (Game g : games.values()) {
            if (g.isActive() && g.arena().region().contains(l)) return g;
        }
        return null;
    }

    public Game byChicken(Entity e) {
        if (!(e instanceof Chicken)) return null;
        for (Game g : games.values()) if (g.isChicken(e)) return g;
        return null;
    }

    void addPending(UUID id, PlayerSnapshot snapshot) { pending.put(id, snapshot); }

    public PlayerSnapshot takePending(UUID id) { return pending.remove(id); }

    public void removeArena(Arena arena) {
        Game g = games.remove(key(arena.name()));
        if (g != null) g.shutdown();
    }

    public void shutdown() {
        lobbyTask.cancel();
        for (Game g : new ArrayList<>(games.values())) g.shutdown();
        games.clear();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
