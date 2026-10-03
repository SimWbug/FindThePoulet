package fr.simon.findthepoulet;

import fr.simon.findthepoulet.arena.Arena;
import fr.simon.findthepoulet.arena.ArenaManager;
import fr.simon.findthepoulet.command.PouletCommand;
import fr.simon.findthepoulet.game.GameManager;
import fr.simon.findthepoulet.game.GameTeam;
import fr.simon.findthepoulet.listener.GameListener;
import fr.simon.findthepoulet.listener.HubListener;
import fr.simon.findthepoulet.listener.MenuListener;
import fr.simon.findthepoulet.listener.SetupListener;
import fr.simon.findthepoulet.setup.SetupManager;
import fr.simon.findthepoulet.skin.SkinManager;
import fr.simon.findthepoulet.stats.LeaderboardManager;
import fr.simon.findthepoulet.stats.StatsManager;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Locs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class FindThePoulet extends JavaPlugin {

    private static FindThePoulet instance;

    private ArenaManager arenas;
    private GameManager games;
    private SetupManager setup;
    private SkinManager skins;
    private StatsManager stats;
    private LeaderboardManager leaderboards;

    public static FindThePoulet get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Items.init(this);
        fr.simon.findthepoulet.game.Flute.init(this);
        fr.simon.findthepoulet.game.Gadgets.init(this);
        stats = new StatsManager(this);
        stats.load();
        leaderboards = new LeaderboardManager(this);
        leaderboards.load();

        skins = new SkinManager(this);
        skins.load();
        arenas = new ArenaManager(this);
        arenas.load();
        games = new GameManager(this);
        setup = new SetupManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new HubListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new SetupListener(this), this);
        pm.registerEvents(new GameListener(this), this);

        PluginCommand command = getCommand("poulet");
        if (command != null) {
            PouletCommand executor = new PouletCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        for (Player p : Bukkit.getOnlinePlayers()) giveMenuItem(p);
        getLogger().info("Find The Poulet activé - " + arenas.all().size() + " arène(s) chargée(s).");
    }

    @Override
    public void onDisable() {
        if (games != null) games.shutdown();
        if (setup != null) setup.cancelAll();
        if (stats != null) stats.save();
        if (leaderboards != null) leaderboards.removeAll();
    }

    public ArenaManager arenas() { return arenas; }
    public GameManager games() { return games; }
    public SetupManager setup() { return setup; }
    public SkinManager skins() { return skins; }
    public StatsManager stats() { return stats; }
    public LeaderboardManager leaderboards() { return leaderboards; }

    /** /poulet reload : config + déguisements (les arènes ne bougent pas). */
    public void reload() {
        reloadConfig();
        skins.load();
    }

    // ---------------------------------------------------------------- config

    public int minPlayers() { return Math.max(2, getConfig().getInt("min-players", 2)); }

    public int maxPlayers() {
        return Math.min(GameTeam.MAX, Math.max(minPlayers(), getConfig().getInt("max-players", 15)));
    }

    public int gameDuration() { return Math.max(30, getConfig().getInt("game-duration-seconds", 1200)); }
    public int startCountdown() { return Math.max(1, getConfig().getInt("start-countdown-seconds", 10)); }
    public int arenaSize() { return Math.max(32, getConfig().getInt("arena-size", 64)); }
    public int enclosureSize() { return Math.max(2, getConfig().getInt("enclosure-inner-size", 4)); }
    public double chickenMinDistance() { return getConfig().getDouble("chicken-min-distance-from-enclosure", 12); }
    public boolean dropOnHit() { return getConfig().getBoolean("drop-chicken-on-hit", true); }
    public boolean keepInventory() { return getConfig().getBoolean("keep-inventory-on-death", false); }

    public Location waitingLobby(Arena arena) {
        Location l = Locs.load(getConfig(), "lobby");
        return l != null ? l : arena.spawnPoint();
    }

    public Location mainSpawn() {
        Location l = Locs.load(getConfig(), "main-spawn");
        return l != null ? l : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    /** Donne la tête de poulet (si activé et si le joueur ne l'a pas déjà). */
    public void giveMenuItem(Player p) {
        if (!getConfig().getBoolean("lobby-item.give-on-join", true)) return;
        if (games.of(p) != null || Items.has(p, Items.MENU)) return;
        int slot = getConfig().getInt("lobby-item.slot", 4);
        ItemStack head = Items.menuHead(getConfig());
        ItemStack current = p.getInventory().getItem(slot);
        if (Items.isEmpty(current)) p.getInventory().setItem(slot, head);
        else p.getInventory().addItem(head);
    }
}
