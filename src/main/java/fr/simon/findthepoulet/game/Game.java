package fr.simon.findthepoulet.game;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.Arena;
import fr.simon.findthepoulet.arena.BlockRestorer;
import fr.simon.findthepoulet.arena.Region;
import fr.simon.findthepoulet.skin.ChickenSkin;
import fr.simon.findthepoulet.skin.SkinHandle;
import fr.simon.findthepoulet.stats.Stat;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Locs;
import fr.simon.findthepoulet.util.Msg;
import fr.simon.findthepoulet.util.Players;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Game {

    public enum State { WAITING, STARTING, RUNNING, ENDING }

    private final FindThePoulet plugin;
    private final Arena arena;
    private State state = State.WAITING;

    // ---- joueurs / équipes
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> ready = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final List<GameTeam> teams = new ArrayList<>();
    private final Map<UUID, GameTeam> teamOf = new HashMap<>();
    private final Map<GameTeam, Integer> points = new HashMap<>();
    private UUID fox;
    private GameTeam foxTeam;
    /** Joueurs éliminés par le Renard : secondes avant retour (-1 = à la prochaine manche). */
    private final Map<UUID, Integer> eliminated = new HashMap<>();

    // ---- cooldowns / états temporaires
    private final Map<UUID, Long> grabCooldown = new HashMap<>();
    private final Map<UUID, Long> fluteCooldown = new HashMap<>();
    private final Map<UUID, Long> featherCooldown = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private final Map<UUID, Integer> campSeconds = new HashMap<>();
    private final Set<UUID> trackedEntities = new HashSet<>();

    // ---- poulet
    private BlockRestorer restorer;
    private Chicken chicken;
    private SkinHandle skin;
    private ChickenSkin gameSkin;
    private UUID lastCarrier;
    private boolean carrierSafeUsed;
    private long carrierSafeUntil;
    private int fleeCycles, restCycles;
    private int eggTimer;
    private int pauseCycles;

    // ---- temps / HUD
    private int duration;
    private int timeLeft;
    private int countdown;
    private int ticks;
    private int round;
    private boolean suddenDeath;
    private BukkitTask countdownTask;
    private BukkitTask gameTask;
    private Scoreboard board;
    private Objective sidebar;
    private BossBar bossBar;

    public Game(FindThePoulet plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    // ================================================================ accès

    public Arena arena() { return arena; }
    public State state() { return state; }
    public boolean isRunning() { return state == State.RUNNING; }
    public boolean isActive() { return state == State.RUNNING || state == State.ENDING; }
    public int size() { return players.size(); }
    public boolean isReady(Player p) { return ready.contains(p.getUniqueId()); }
    public BlockRestorer restorer() { return restorer; }
    public boolean isFox(Player p) { return fox != null && fox.equals(p.getUniqueId()); }
    public boolean isEliminated(Player p) { return eliminated.containsKey(p.getUniqueId()); }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    public boolean isChicken(Entity e) {
        return chicken != null && chicken.getUniqueId().equals(e.getUniqueId());
    }

    public boolean isCarrier(Player p) {
        return chicken != null && p.getPassengers().contains(chicken);
    }

    private Player carrier() {
        return chicken != null && chicken.getVehicle() instanceof Player cp ? cp : null;
    }

    /** Porteur protégé quelques secondes en arrivant près de l'enclos (anti-camping). */
    public boolean isCarrierSafe(Player p) {
        return isCarrier(p) && System.currentTimeMillis() < carrierSafeUntil;
    }

    /** Pas de dégâts de chute juste après une plume de saut. */
    public boolean hasNoFall(Player p) {
        Long until = noFallUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public boolean sameTeam(Player a, Player b) {
        GameTeam ta = teamOf.get(a.getUniqueId());
        return ta != null && ta == teamOf.get(b.getUniqueId());
    }

    public void trackEntity(Entity e) {
        if (!(e instanceof Player)) trackedEntities.add(e.getUniqueId());
    }

    public boolean isTracked(Entity e) {
        return trackedEntities.contains(e.getUniqueId());
    }

    private List<Player> online() {
        List<Player> list = new ArrayList<>();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }

    /** Joueurs en jeu (ni éliminés, ni morts). */
    private List<Player> active() {
        List<Player> list = new ArrayList<>();
        for (Player p : online()) if (!isEliminated(p) && !p.isDead()) list.add(p);
        return list;
    }

    private void broadcast(String mini) {
        for (Player p : online()) Msg.send(p, mini);
    }

    private void soundAll(String key, float pitch) {
        for (Player p : online()) Msg.sound(p, key, pitch);
    }

    private void title(Player p, String title, String subtitle, int stayTicks) {
        p.showTitle(Title.title(Msg.mm(title), Msg.mm(subtitle), Title.Times.times(
                Duration.ofMillis(150), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(400))));
    }

    private String coloredName(Player p) {
        GameTeam t = teamOf.get(p.getUniqueId());
        if (t != null && t.isFox()) return "<gold>" + p.getName() + "</gold>";
        return t == null ? "<yellow>" + p.getName() + "</yellow>" : "<" + t.colorTag() + ">" + p.getName() + "</" + t.colorTag() + ">";
    }

    /** Nom d'équipe à afficher (en solo : le pseudo du joueur). */
    private String teamLabel(GameTeam t) {
        if (t.isFox() || arena.mode() != TeamMode.SOLO) return t.mini();
        for (UUID id : t.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) return coloredName(p);
        }
        return t.mini();
    }

    // ================================================================ lobby d'attente

    public boolean join(Player p) {
        if (!arena.isEnabled()) { Msg.send(p, "<red>Cette arène est désactivée."); return false; }
        if (arena.world() == null) { Msg.send(p, "<red>Le monde de cette arène n'est pas chargé."); return false; }
        if (state == State.RUNNING || state == State.ENDING) {
            Msg.send(p, "<red>Une partie est déjà en cours dans cette arène, reviens dans quelques minutes !");
            return false;
        }
        if (players.size() >= plugin.maxPlayers()) { Msg.send(p, "<red>Cette arène est pleine."); return false; }

        UUID id = p.getUniqueId();
        players.add(id);
        snapshots.put(id, PlayerSnapshot.capture(p));
        plugin.games().bind(id, this);

        p.closeInventory();
        Players.reset(p, GameMode.ADVENTURE);
        p.getInventory().setItem(4, Items.menuHead(cfg()));
        p.getInventory().setItem(8, Items.leaveItem());
        p.teleport(plugin.waitingLobby(arena));

        broadcast("<yellow>" + p.getName() + " <gray>a rejoint <gold>" + arena.name()
                + " <gray>(" + players.size() + "/" + plugin.maxPlayers() + ")");
        Msg.send(p, "<gray>Mode : <white>" + arena.mode().label() + " <dark_gray>| <white>" + arena.format().label()
                + (arena.isFox() ? " <dark_gray>| <gold>Renard" : "") + " <dark_gray>| <white>PvP " + (arena.isPvp() ? "ON" : "OFF"));
        Msg.sound(p, "entity.chicken.ambient", 1f);

        int min = plugin.minPlayers();
        if (players.size() == min) {
            for (Player o : online()) if (!isReady(o)) sendReadyPrompt(o);
        } else if (players.size() > min) {
            sendReadyPrompt(p);
        } else {
            Msg.send(p, "<gray>En attente d'au moins <yellow>" + min + "</yellow> joueurs...");
        }
        return true;
    }

    private void sendReadyPrompt(Player p) {
        p.sendMessage(Msg.mm(Msg.PREFIX + "<gray>Assez de joueurs ! "
                + "<click:run_command:'/poulet pret'><hover:show_text:'<green>Clique pour te mettre prêt'>"
                + "<green><bold>[✔ CLIQUE ICI : JE SUIS PRÊT]</bold></green></hover></click>"));
        Msg.sound(p, "block.note_block.pling", 1.2f);
    }

    public void toggleReady(Player p) {
        if (state != State.WAITING && state != State.STARTING) {
            Msg.send(p, "<red>La partie a déjà commencé.");
            return;
        }
        if (players.size() < plugin.minPlayers()) {
            Msg.send(p, "<red>Il faut au moins " + plugin.minPlayers() + " joueurs pour se mettre prêt.");
            return;
        }
        UUID id = p.getUniqueId();
        if (ready.remove(id)) {
            broadcast("<yellow>" + p.getName() + " <red>n'est plus prêt.");
            if (state == State.STARTING) cancelCountdown("<red>Démarrage annulé.");
            return;
        }
        ready.add(id);
        broadcast("<yellow>" + p.getName() + " <green>est prêt ! <gray>(" + ready.size() + "/" + players.size() + ")");
        Msg.sound(p, "block.note_block.pling", 1.6f);
        checkStart();
    }

    private void checkStart() {
        if (state != State.WAITING) return;
        if (players.size() < plugin.minPlayers() || !ready.containsAll(players)) return;
        startCountdown();
    }

    private void startCountdown() {
        state = State.STARTING;
        countdown = plugin.startCountdown();
        broadcast("<green>Tout le monde est prêt ! La partie commence dans <yellow>" + countdown + "</yellow> secondes.");
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (players.size() < plugin.minPlayers() || !ready.containsAll(players)) {
                cancelCountdown("<red>Démarrage annulé : tout le monde n'est pas prêt.");
                return;
            }
            if (countdown <= 0) {
                countdownTask.cancel();
                countdownTask = null;
                start();
                return;
            }
            if (countdown <= 5 || countdown % 5 == 0) {
                for (Player o : online()) {
                    title(o, "<gold><bold>" + countdown, "<yellow>Prépare-toi...", 25);
                    Msg.sound(o, "block.note_block.hat", 1f);
                }
            }
            countdown--;
        }, 0L, 20L);
    }

    private void cancelCountdown(String reason) {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (state == State.STARTING) state = State.WAITING;
        broadcast(reason);
    }

    /** Appelé chaque seconde par le GameManager (barre d'action du lobby). */
    void lobbyTick() {
        if (state != State.WAITING && state != State.STARTING) return;
        String text = players.size() < plugin.minPlayers()
                ? "<gray>En attente de joueurs... <yellow>" + players.size() + "/" + plugin.minPlayers()
                : "<gray>Prêts : <green>" + ready.size() + "<gray>/" + players.size()
                  + " <dark_gray>- <gray>clique sur le message du chat ou utilise la tête";
        for (Player p : online()) p.sendActionBar(Msg.mm(text));
    }

    public void leave(Player p) {
        UUID id = p.getUniqueId();
        if (!players.remove(id)) return;
        ready.remove(id);
        if (isCarrier(p)) dropChicken(p, null);
        GameTeam t = teamOf.remove(id);
        if (t != null) t.members().remove(id);
        eliminated.remove(id);
        campSeconds.remove(id);
        boolean wasFox = id.equals(fox);
        if (wasFox) fox = null;
        if (board != null) {
            Team st = board.getEntryTeam(p.getName());
            if (st != null) st.removeEntry(p.getName());
        }
        restorePlayer(p);
        Msg.send(p, "<gray>Tu as quitté la partie.");
        broadcast("<yellow>" + p.getName() + " <gray>a quitté la partie (" + players.size() + " restant(s))");
        if (wasFox && state == State.RUNNING) broadcast("<gold>Le Renard a quitté la partie !");

        switch (state) {
            case WAITING -> checkStart();
            case STARTING -> {
                if (players.size() < plugin.minPlayers()) cancelCountdown("<red>Pas assez de joueurs, démarrage annulé.");
            }
            case RUNNING -> {
                if (players.isEmpty()) end(null, "<gray>Tous les joueurs sont partis.");
                else updateHud();
            }
            default -> { }
        }
    }

    // ================================================================ démarrage

    private void start() {
        World w = arena.world();
        if (w == null) {
            state = State.WAITING;
            broadcast("<red>Le monde de l'arène est introuvable, partie annulée.");
            return;
        }
        state = State.RUNNING;
        restorer = new BlockRestorer(arena.region());
        trackedEntities.clear();
        grabCooldown.clear();
        lastCarrier = null;
        ticks = 0;
        round = 1;
        suddenDeath = false;
        eggTimer = eggInterval();
        duration = arena.format() == GameFormat.ROUNDS
                ? Math.max(30, cfg().getInt("rounds.game-duration-seconds", 900))
                : plugin.gameDuration();
        timeLeft = duration;

        setChunkTickets(true);
        pickFox();
        buildTeams();
        buildScoreboard();
        bossBar = BossBar.bossBar(Msg.mm("<yellow>Temps restant : <white>" + Msg.time(timeLeft)),
                1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

        boolean solo = arena.mode() == TeamMode.SOLO;
        for (Player p : online()) {
            Players.reset(p, GameMode.SURVIVAL); // la tête de poulet disparaît de la barre
            p.teleport(respawnLocation());
            applyBorder(p);
            p.setScoreboard(board);
            p.showBossBar(bossBar);
            if (arena.isKit()) giveKit(p);
            plugin.stats().add(p, Stat.GAMES);
            GameTeam t = teamOf.get(p.getUniqueId());
            if (isFox(p)) {
                applyFoxEffects(p);
                title(p, "<gold><bold>TU ES LE RENARD", "<yellow>Tape les porteurs pour les éliminer", 80);
                Msg.send(p, "<gold>Tu es le Renard ! <gray>Tu ne peux pas porter le poulet, mais si tu frappes le porteur, "
                        + "il est <red>éliminé</red>. Si personne ne ramène le poulet avant la fin, <gold>tu gagnes</gold>.");
                Msg.sound(p, "entity.fox.screech", 1f);
                continue;
            }
            title(p, "<gold><bold>C'EST PARTI !", "<yellow>Trouve le poulet et ramène-le dans l'enclos", 60);
            if (solo || t == null) Msg.send(p, "<gray>Chacun pour soi ! <yellow>Clic droit sur le poulet <gray>pour l'attraper.");
            else Msg.send(p, "<gray>Tu es dans l'" + t.mini() + "<gray>. <yellow>Clic droit sur le poulet <gray>pour l'attraper.");
            Msg.sound(p, "entity.chicken.ambient", 0.8f);
        }
        if (fox != null) {
            Player f = Bukkit.getPlayer(fox);
            broadcast("<gold>Attention : " + (f != null ? f.getName() : "un joueur") + " est le Renard ! <gray>S'il frappe le porteur, celui-ci est éliminé.");
        }
        if (arena.format() == GameFormat.ROUNDS) {
            broadcast("<yellow>Mode Manches : <white>la première équipe à ramener <gold>" + roundsToWin() + "</gold> poulets gagne !");
        }
        Msg.send(Bukkit.getConsoleSender(), "<gray>Partie lancée sur " + arena.name() + " (" + players.size() + " joueurs)");

        gameSkin = plugin.skins().pick(arena.skin());
        spawnChicken();
        if (gameSkin != null) broadcast("<gray>Le poulet du jour : " + gameSkin.name() + " <gray>!");
        spawnLootChests();
        updateHud();
        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private void pickFox() {
        fox = null;
        foxTeam = null;
        int min = Math.max(3, cfg().getInt("fox.min-players", 3));
        if (!arena.isFox() || players.size() < min) {
            if (arena.isFox()) broadcast("<gray>Pas de Renard cette fois : il faut au moins " + min + " joueurs.");
            return;
        }
        List<UUID> list = new ArrayList<>(players);
        fox = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        foxTeam = GameTeam.fox();
        foxTeam.members().add(fox);
    }

    private void applyFoxEffects(Player p) {
        int speed = cfg().getInt("fox.speed-level", 1);
        if (speed > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, speed - 1, false, false, true));
    }

    private void buildTeams() {
        teams.clear();
        teamOf.clear();
        points.clear();
        List<UUID> list = new ArrayList<>(players);
        if (fox != null) list.remove(fox);
        Collections.shuffle(list);
        int count = (int) Math.ceil(list.size() / (double) arena.mode().size());
        count = Math.max(2, Math.min(GameTeam.MAX, count)); // toujours au moins 2 équipes
        for (int i = 0; i < count; i++) teams.add(new GameTeam(i));
        for (int i = 0; i < list.size(); i++) {
            GameTeam t = teams.get(i % count);
            t.members().add(list.get(i));
            teamOf.put(list.get(i), t);
        }
        teams.removeIf(t -> t.members().isEmpty());
        for (GameTeam t : teams) points.put(t, 0);
        if (foxTeam != null) teamOf.put(fox, foxTeam);
    }

    private void buildScoreboard() {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        sidebar = board.registerNewObjective("ftp", Criteria.DUMMY, Msg.mm("<gold><bold>FIND THE POULET"));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.numberFormat(NumberFormat.blank());
        List<GameTeam> all = new ArrayList<>(teams);
        if (foxTeam != null) all.add(foxTeam);
        for (GameTeam t : all) {
            Team st = board.registerNewTeam(t.scoreboardId());
            st.color(t.color());
            st.setAllowFriendlyFire(false);
            for (UUID id : t.members()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) st.addEntry(p.getName());
            }
        }
    }

    private void giveKit(Player p) {
        for (String line : cfg().getStringList("kit.items")) {
            String[] parts = line.trim().split(":");
            Material m = Material.matchMaterial(parts[0]);
            if (m == null || !m.isItem()) continue;
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            p.getInventory().addItem(new ItemStack(m, amount));
        }
    }

    /** Réapparition pendant la partie : bordure, kit, effets du Renard. */
    public void onRespawn(Player p) {
        applyBorder(p);
        if (!isRunning()) return;
        if (arena.isKit() && cfg().getBoolean("kit.on-respawn", true) && !plugin.keepInventory()) giveKit(p);
        if (isFox(p)) applyFoxEffects(p);
    }

    // ================================================================ boucle de jeu (toutes les 5 ticks)

    private void tick() {
        if (state != State.RUNNING) return;
        ticks++;

        if (pauseCycles > 0) {
            if (--pauseCycles == 0) {
                spawnChicken();
                broadcast("<yellow>Le poulet est de retour quelque part dans l'arène !");
                soundAll("entity.chicken.ambient", 1.2f);
            }
        } else if (chicken == null || !chicken.isValid()) {
            if (chicken != null && chicken.isDead()) return; // l'événement de mort gère la fin
            if (skin != null) {
                skin.remove();
                skin = null;
            }
            spawnChicken();
            broadcast("<yellow>Le poulet a réapparu quelque part dans l'arène !");
        }

        if (chicken != null && pauseCycles == 0 && chickenTick()) return;
        updateCompasses();
        if (ticks % 4 == 0) secondTick();
    }

    /** @return true si le poulet vient d'être ramené (fin de partie ou de manche). */
    private boolean chickenTick() {
        Entity vehicle = chicken.getVehicle();
        if (vehicle instanceof Player carrier && players.contains(carrier.getUniqueId())) {
            lastCarrier = carrier.getUniqueId();
            if (arena.isInEnclosure(carrier.getLocation())) {
                deliver(carrier);
                return true;
            }
            if (!carrierSafeUsed && inEnclosureZone(carrier.getLocation())) {
                carrierSafeUsed = true;
                carrierSafeUntil = System.currentTimeMillis() + Math.max(0, cfg().getInt("anti-camp.carrier-protection-seconds", 3)) * 1000L;
                carrier.sendActionBar(Msg.mm("<green><bold>Protégé ! <gray>Personne ne peut te faire lâcher le poulet pendant quelques secondes"));
                Msg.sound(carrier, "block.beacon.power_select", 1.5f);
            }
            featherTrail(carrier);
            return false;
        }
        if (vehicle != null) chicken.leaveVehicle();
        if (lastCarrier != null && arena.isInEnclosure(chicken.getLocation())) {
            Player last = Bukkit.getPlayer(lastCarrier);
            if (last != null && teamOf.containsKey(lastCarrier)) {
                deliver(last);
                return true;
            }
        }
        if (chicken.isInLava() || !arena.region().contains(chicken.getLocation())) {
            chicken.setFireTicks(0);
            chicken.teleport(randomChickenSpot(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        flee();
        plugin.skins().trail(chicken);
        return false;
    }

    private void secondTick() {
        timeLeft--;

        if (chicken != null && chicken.isValid() && chicken.getVehicle() == null && --eggTimer <= 0) {
            layEgg();
            eggTimer = eggInterval();
        }
        antiCamp();
        tickEliminated();
        if (!suddenDeath && cfg().getBoolean("sudden-death.enabled", true)
                && timeLeft <= cfg().getInt("sudden-death.seconds", 180) && timeLeft > 0) {
            startSuddenDeath();
        }
        updateHud();
        announceTime();
        if (timeLeft <= 0) timeout();
    }

    private void announceTime() {
        switch (timeLeft) {
            case 600, 300, 120, 60 -> {
                broadcast("<yellow>Plus que <gold>" + (timeLeft / 60) + " minute" + (timeLeft > 60 ? "s" : "") + "</gold> !");
                soundAll("block.note_block.bell", 1f);
            }
            case 30, 10, 5, 4, 3, 2, 1 -> {
                broadcast("<red>Plus que <gold>" + timeLeft + "</gold> seconde" + (timeLeft > 1 ? "s" : "") + " !");
                soundAll("block.note_block.hat", 1.5f);
            }
            default -> { }
        }
    }

    // ================================================================ HUD

    private void updateHud() {
        if (sidebar == null) return;
        Player carrier = carrier();
        boolean alive = chicken != null && !chicken.isDead();
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Arène : <white>" + arena.name());
        lines.add("");
        lines.add("<yellow>Temps : " + (suddenDeath ? "<red>" : "<white>") + Msg.time(timeLeft));
        lines.add("<yellow>Joueurs : <white>" + players.size());
        lines.add("<yellow>Poulets : <white>" + (alive ? 1 : 0));
        lines.add("<yellow>Porteur : " + (carrier == null ? "<gray>personne" : coloredName(carrier)));
        if (arena.format() == GameFormat.ROUNDS) {
            lines.add("<yellow>Manche : <white>" + round + " <dark_gray>| <yellow>But : <white>" + roundsToWin());
            lines.add("<yellow>Score : " + scoreSummary());
        }
        if (fox != null) {
            Player f = Bukkit.getPlayer(fox);
            lines.add("<gold>Renard : <white>" + (f != null ? f.getName() : "?"));
        }
        if (suddenDeath) lines.add("<red><bold>☠ MORT SUBITE");
        lines.add(" ");
        lines.add("<yellow>" + arena.mode().label() + " <dark_gray>| <yellow>PvP " + (arena.isPvp() ? "<green>ON" : "<red>OFF"));

        for (int i = 0; i < lines.size(); i++) {
            Score s = sidebar.getScore("line" + i);
            s.setScore(lines.size() - i);
            s.customName(Msg.mm(lines.get(i)));
        }
        for (int i = lines.size(); i < 16; i++) board.resetScores("line" + i);

        if (bossBar != null) {
            bossBar.name(Msg.mm((suddenDeath ? "<red><bold>MORT SUBITE</bold> <dark_gray>- " : "")
                    + "<yellow>Temps restant : <white>" + Msg.time(timeLeft)));
            bossBar.progress(Math.max(0f, Math.min(1f, timeLeft / (float) duration)));
            bossBar.color(suddenDeath || timeLeft <= 60 ? BossBar.Color.RED : BossBar.Color.YELLOW);
        }
        boolean solo = arena.mode() == TeamMode.SOLO;
        for (Player p : online()) {
            if (holdsActiveCompass(p)) continue; // la boussole affiche la distance
            Integer elim = eliminated.get(p.getUniqueId());
            String info;
            if (elim != null) {
                info = elim < 0 ? "<red>Éliminé <gray>- retour à la prochaine manche" : "<red>Éliminé <gray>- retour dans <white>" + elim + "s";
            } else if (isFox(p)) {
                info = "<gold>Renard <dark_gray>| " + (carrier != null ? "<yellow>Chasse " + coloredName(carrier) + " <yellow>!" : "<gray>Attends qu'un joueur attrape le poulet...");
            } else {
                GameTeam t = teamOf.get(p.getUniqueId());
                String team = solo || t == null ? "<gray>Solo" : t.mini();
                String state = p.equals(carrier) ? "<gold><bold>Tu as le poulet ! Fonce à l'enclos !"
                        : carrier != null ? "<gray>Poulet porté par " + coloredName(carrier)
                        : "<gray>Le poulet est en liberté... <dark_gray>(accroupis-toi pour l'approcher)";
                info = team + " <dark_gray>| " + state;
            }
            p.sendActionBar(Msg.mm(info));
        }
    }

    private String scoreSummary() {
        List<GameTeam> sorted = new ArrayList<>(teams);
        sorted.sort((a, b) -> Integer.compare(points.getOrDefault(b, 0), points.getOrDefault(a, 0)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            GameTeam t = sorted.get(i);
            sb.append("<").append(t.colorTag()).append(">●").append(points.getOrDefault(t, 0)).append(" ");
        }
        return sb.toString().trim();
    }

    // ================================================================ poulet

    private void spawnChicken() {
        Location l = randomChickenSpot();
        Chicken c = l.getWorld().spawn(l, Chicken.class);
        c.customName(Msg.mm(gameSkin != null ? gameSkin.name() : "<gold><bold>LE POULET"));
        c.setCustomNameVisible(true);
        c.setRemoveWhenFarAway(false);
        c.setPersistent(false);
        c.setAdult();
        c.getPersistentDataContainer().set(Items.chickenKey(), PersistentDataType.BYTE, (byte) 1);
        if (plugin.skins().isEnabled()) plugin.skins().applyVariant(c);
        if (suddenDeath) c.setGlowing(true);
        chicken = c;
        lastCarrier = null;
        carrierSafeUsed = false;
        fleeCycles = 0;
        restCycles = 0;
        if (gameSkin != null) skin = plugin.skins().apply(c, gameSkin);
    }

    private Location randomChickenSpot() {
        return randomSpot(plugin.chickenMinDistance());
    }

    /** Position aléatoire où l'on peut se tenir (surface ou cavernes), à au moins minD blocs du centre de l'enclos. */
    private Location randomSpot(double minD) {
        World w = arena.world();
        Region r = arena.region();
        Location center = arena.enclosureCenter();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 400; i++) {
            int x = rnd.nextInt(r.minX() + 1, r.maxX());
            int z = rnd.nextInt(r.minZ() + 1, r.maxZ());
            double dx = x + 0.5 - center.getX(), dz = z + 0.5 - center.getZ();
            if (dx * dx + dz * dz < minD * minD) continue;
            int top = w.getHighestBlockYAt(x, z);
            int y = rnd.nextInt(w.getMinHeight() + 1, top + 2);
            if (Locs.standable(w, x, y, z)) return Locs.center(w, x, y, z);
        }
        int x = rnd.nextBoolean() ? r.minX() + 3 : r.maxX() - 3;
        int z = rnd.nextBoolean() ? r.minZ() + 3 : r.maxZ() - 3;
        return Locs.safeNear(w, x, z, w.getHighestBlockYAt(x, z) + 1);
    }

    /**
     * Le poulet fuit les joueurs qui approchent (sauf s'ils s'accroupissent).
     * Après quelques secondes de course il s'essouffle et se repose : c'est le moment de l'attraper.
     */
    private void flee() {
        if (!cfg().getBoolean("chicken.flee.enabled", true) || chicken.getVehicle() != null) return;
        if (restCycles > 0) {
            restCycles--;
            if (restCycles % 4 == 0) chicken.getWorld().spawnParticle(Particle.CLOUD, chicken.getLocation().add(0, 0.6, 0), 2, 0.1, 0.1, 0.1, 0.01);
            return;
        }
        double radius = cfg().getDouble("chicken.flee.radius", 6);
        Player threat = null;
        double best = radius * radius;
        for (Player p : active()) {
            if (p.isSneaking() || p.getGameMode() == GameMode.SPECTATOR || !p.getWorld().equals(chicken.getWorld())) continue;
            double d = p.getLocation().distanceSquared(chicken.getLocation());
            if (d < best) {
                best = d;
                threat = p;
            }
        }
        if (threat == null) {
            fleeCycles = Math.max(0, fleeCycles - 1);
            return;
        }
        int maxCycles = Math.max(1, cfg().getInt("chicken.flee.run-seconds", 4)) * 4;
        if (++fleeCycles > maxCycles) {
            fleeCycles = 0;
            restCycles = Math.max(1, cfg().getInt("chicken.flee.rest-seconds", 3)) * 4;
            chicken.getPathfinder().stopPathfinding();
            return;
        }
        Vector away = chicken.getLocation().toVector().subtract(threat.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1e-3) away = new Vector(1, 0, 0);
        away.normalize().multiply(8);
        Region r = arena.region();
        Location target = chicken.getLocation().add(away);
        target.setX(Math.max(r.minX() + 2, Math.min(r.maxX() - 2, target.getX())));
        target.setZ(Math.max(r.minZ() + 2, Math.min(r.maxZ() - 2, target.getZ())));
        double speed = cfg().getDouble("chicken.flee.speed", 1.8) * (suddenDeath ? 1.15 : 1.0);
        chicken.getPathfinder().moveTo(target, speed);
    }

    private int eggInterval() {
        int base = Math.max(5, cfg().getInt("chicken.egg-interval-seconds", 45));
        return suddenDeath ? Math.max(5, base / 3) : base;
    }

    /** Le poulet pond un œuf (impossible à ramasser) : un indice pour le retrouver. */
    private void layEgg() {
        if (!cfg().getBoolean("chicken.eggs", true)) return;
        Location l = chicken.getLocation();
        Item egg = l.getWorld().dropItem(l, new ItemStack(Material.EGG));
        egg.setCanPlayerPickup(false);
        egg.setCanMobPickup(false);
        egg.setVelocity(new Vector(0, 0.1, 0));
        trackEntity(egg);
        l.getWorld().playSound(Sound.sound(Key.key("entity.chicken.egg"), Sound.Source.NEUTRAL, 1.5f, 1f), l.getX(), l.getY(), l.getZ());
        int life = Math.max(5, cfg().getInt("chicken.egg-lifetime-seconds", 90));
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (egg.isValid()) egg.remove(); }, life * 20L);
    }

    /** Traînée de plumes derrière le porteur (particules + plumes au sol qu'on ne peut pas ramasser). */
    private void featherTrail(Player carrier) {
        if (!cfg().getBoolean("carrier.feather-trail", true)) return;
        Location l = carrier.getLocation().add(0, 0.8, 0);
        l.getWorld().spawnParticle(Particle.ITEM, l, 4, 0.25, 0.3, 0.25, 0.02, new ItemStack(Material.FEATHER));
        if (ticks % 8 == 0) {
            Item f = l.getWorld().dropItem(carrier.getLocation(), new ItemStack(Material.FEATHER));
            f.setCanPlayerPickup(false);
            f.setCanMobPickup(false);
            trackEntity(f);
            int life = Math.max(3, cfg().getInt("carrier.feather-lifetime-seconds", 20));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (f.isValid()) f.remove(); }, life * 20L);
        }
    }

    public void tryGrab(Player p) {
        if (state != State.RUNNING || chicken == null || isEliminated(p)) return;
        if (isFox(p)) {
            Msg.send(p, "<gold>Le Renard ne peut pas porter le poulet ! <gray>Chasse plutôt celui qui l'a.");
            return;
        }
        if (chicken.getVehicle() != null) {
            Msg.send(p, "<red>Le poulet est déjà porté ! Frappe le porteur pour qu'il le lâche.");
            return;
        }
        Long until = grabCooldown.get(p.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) return;
        if (!p.getPassengers().isEmpty() || p.isInsideVehicle()) return;
        if (!p.addPassenger(chicken)) return;

        lastCarrier = p.getUniqueId();
        carrierSafeUsed = false;
        fleeCycles = 0;
        restCycles = 0;
        p.setGlowing(true);
        plugin.stats().add(p, Stat.CATCHES);
        broadcast(coloredName(p) + " <yellow>a attrapé le poulet !");
        soundAll("entity.chicken.hurt", 1.2f);
        title(p, "<gold><bold>POULET ATTRAPÉ !", "<yellow>Ramène-le dans l'enclos", 30);
        updateHud();
    }

    public void dropChicken(Player carrier, Player by) {
        if (chicken == null || !carrier.getPassengers().contains(chicken)) return;
        carrier.removePassenger(chicken);
        carrier.setGlowing(false);
        chicken.teleport(carrier.getLocation(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        grabCooldown.put(carrier.getUniqueId(), System.currentTimeMillis() + 1500);
        if (by != null) broadcast(coloredName(by) + " <gray>a fait lâcher le poulet à " + coloredName(carrier) + " <gray>!");
        else broadcast("<yellow>Le poulet s'est échappé !");
        soundAll("entity.chicken.ambient", 1.5f);
        updateHud();
    }

    public void onPlayerDeath(Player p) {
        if (isCarrier(p)) dropChicken(p, null);
        p.setGlowing(false);
    }

    public void chickenKilled(Player killer) {
        if (killer != null && players.contains(killer.getUniqueId())) plugin.stats().add(killer, Stat.KILLS);
        end(null, "<red>Le poulet est mort" + (killer != null ? " (tué par <yellow>" + killer.getName() + "</yellow>)" : "")
                + " ! <dark_red>Tout le monde a perdu.");
    }

    private void removeChicken() {
        if (skin != null) {
            skin.remove();
            skin = null;
        }
        if (chicken == null) return;
        if (chicken.getVehicle() instanceof Player p) p.setGlowing(false);
        if (chicken.getVehicle() != null) chicken.leaveVehicle();
        if (chicken.isValid()) chicken.remove();
        chicken = null;
    }

    // ================================================================ mort subite

    private void startSuddenDeath() {
        suddenDeath = true;
        if (chicken != null && chicken.isValid()) chicken.setGlowing(true);
        eggTimer = Math.min(eggTimer, eggInterval());
        for (Player p : online()) {
            title(p, "<red><bold>☠ MORT SUBITE ☠", "<yellow>Le poulet brille à travers les murs !", 60);
            Msg.sound(p, "entity.ender_dragon.growl", 1.3f);
        }
        broadcast("<red><bold>MORT SUBITE !</bold> <yellow>Le poulet est visible à travers les murs et pond plus souvent. Dernière chance !");
    }

    // ================================================================ anti-camping

    private boolean inEnclosureZone(Location l) {
        Location c = arena.enclosureCenter();
        if (!l.getWorld().equals(c.getWorld()) || Math.abs(l.getY() - c.getY()) > 8) return false;
        double r = cfg().getDouble("anti-camp.radius", 8);
        double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        return dx * dx + dz * dz <= r * r;
    }

    /** Un joueur sans poulet qui reste trop longtemps près de l'enclos est repoussé et ralenti. */
    private void antiCamp() {
        if (!cfg().getBoolean("anti-camp.enabled", true)) return;
        int limit = Math.max(3, cfg().getInt("anti-camp.max-seconds", 10));
        Location c = arena.enclosureCenter();
        for (Player p : active()) {
            UUID id = p.getUniqueId();
            if (isCarrier(p) || p.getGameMode() == GameMode.SPECTATOR || !inEnclosureZone(p.getLocation())) {
                campSeconds.remove(id);
                continue;
            }
            int s = campSeconds.merge(id, 1, Integer::sum);
            if (s == limit - 3) {
                p.sendActionBar(Msg.mm("<red><bold>Ne campe pas devant l'enclos ! <gray>Éloigne-toi..."));
                Msg.sound(p, "block.note_block.bass", 0.6f);
            }
            if (s >= limit) {
                Vector push = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (push.lengthSquared() < 1e-3) push = new Vector(0, 0, 1);
                p.setVelocity(push.normalize().multiply(1.4).setY(0.45));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                Msg.send(p, "<red>Tu campais près de l'enclos : repoussé !");
                Msg.sound(p, "entity.chicken.hurt", 0.5f);
                campSeconds.put(id, limit - 3);
            }
        }
    }

    // ================================================================ mode Renard

    /** Le Renard a frappé le porteur : le poulet tombe et le porteur est éliminé. */
    public void foxCatch(Player foxPlayer, Player victim) {
        if (!isRunning() || !isFox(foxPlayer) || !isCarrier(victim)) return;
        dropChicken(victim, foxPlayer);
        plugin.stats().add(foxPlayer, Stat.ELIMINATIONS);
        int seconds = arena.format() == GameFormat.ROUNDS ? -1 : Math.max(5, cfg().getInt("fox.elimination-seconds", 45));
        eliminated.put(victim.getUniqueId(), seconds);
        campSeconds.remove(victim.getUniqueId());
        victim.setGameMode(GameMode.SPECTATOR);
        title(victim, "<red><bold>ÉLIMINÉ !", "<gold>Le Renard t'a attrapé", 50);
        Msg.sound(victim, "entity.fox.bite", 1f);
        soundAll("entity.fox.screech", 1f);
        broadcast("<gold>Le Renard a éliminé " + coloredName(victim) + " <gold>!"
                + (seconds < 0 ? " <gray>(retour à la prochaine manche)" : " <gray>(retour dans " + seconds + "s)"));
    }

    private void tickEliminated() {
        for (UUID id : new ArrayList<>(eliminated.keySet())) {
            int left = eliminated.get(id);
            if (left < 0) continue;
            if (left <= 1) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) revive(p);
                else eliminated.remove(id);
            } else {
                eliminated.put(id, left - 1);
            }
        }
    }

    private void revive(Player p) {
        eliminated.remove(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(respawnLocation());
        p.setFallDistance(0f);
        applyBorder(p);
        Msg.send(p, "<green>Tu es de retour dans la partie !");
        Msg.sound(p, "entity.player.levelup", 1.4f);
    }

    // ================================================================ objets des coffres

    /** Coffres cachés au hasard (surface ou cavernes), 2 à 3 par joueur, un objet dans chacun. */
    private void spawnLootChests() {
        if (!cfg().getBoolean("loot.enabled", true)) return;
        int min = Math.max(0, cfg().getInt("loot.chests-per-player-min", 2));
        int max = Math.max(min, cfg().getInt("loot.chests-per-player-max", 3));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = 0;
        for (int i = 0; i < players.size(); i++) count += rnd.nextInt(min, max + 1);
        count = Math.min(count, 64);

        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        int placed = 0;
        for (int attempt = 0; attempt < count * 10 && placed < count; attempt++) {
            Block b = randomSpot(6).getBlock();
            if (arena.isEnclosureBlock(b) || b.getType() == Material.CHEST
                    || b.getRelative(BlockFace.DOWN).getType() == Material.CHEST) continue;
            ItemStack loot = randomLoot();
            if (loot == null) return;
            restorer.record(b); // le coffre disparaît au reset de l'arène
            org.bukkit.block.data.type.Chest data = (org.bukkit.block.data.type.Chest) Material.CHEST.createBlockData();
            data.setFacing(faces[rnd.nextInt(faces.length)]);
            b.setBlockData(data, false);
            if (b.getState() instanceof org.bukkit.block.Chest chest) {
                chest.getSnapshotInventory().setItem(rnd.nextInt(27), loot);
                chest.update(true, false);
            }
            placed++;
        }
        if (placed > 0) {
            broadcast("<yellow>" + placed + " coffres <gray>sont cachés dans l'arène : <gold>flûtes, boussoles et plumes de saut</gold> !");
        }
    }

    private ItemStack randomLoot() {
        FileConfiguration c = cfg();
        int flute = c.getBoolean("flute.enabled", true) ? Math.max(0, c.getInt("loot.weights.flute", 40)) : 0;
        int compass = c.getBoolean("compass.enabled", true) ? Math.max(0, c.getInt("loot.weights.compass", 25)) : 0;
        int feather = c.getBoolean("feather.enabled", true) ? Math.max(0, c.getInt("loot.weights.feather", 35)) : 0;
        int total = flute + compass + feather;
        if (total <= 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        if (r < flute) return Flute.create(c);
        if (r < flute + compass) return Gadgets.compass(c);
        return Gadgets.feather(c);
    }

    /** Flûte : le poulet caquette fort, là où il est. @return true si la flûte a été utilisée. */
    public boolean useFlute(Player p) {
        if (state != State.RUNNING || !players.contains(p.getUniqueId()) || isEliminated(p)) return false;
        long now = System.currentTimeMillis();
        Long until = fluteCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            p.sendActionBar(Msg.mm("<red>Reprends ton souffle..."));
            return false;
        }
        if (chicken == null || !chicken.isValid()) {
            Msg.send(p, "<red>Le poulet n'est pas là pour l'instant, garde ta flûte !");
            return false;
        }
        fluteCooldown.put(p.getUniqueId(), now + Math.max(0, cfg().getInt("flute.cooldown-seconds", 3)) * 1000L);

        float[] melody = {1.06f, 1.26f, 1.41f, 1.68f};
        for (int i = 0; i < melody.length; i++) {
            float pitch = melody[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.getWorld().playSound(Sound.sound(Key.key("block.note_block.flute"),
                        Sound.Source.PLAYER, 1f, pitch), p);
            }, i * 3L);
        }
        float volume = (float) Math.max(1.0, cfg().getDouble("flute.chicken-volume", 4.0));
        Chicken c = chicken;
        for (int i = 0; i < 3; i++) {
            float pitch = 0.9f + i * 0.2f;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!c.isValid()) return;
                Location l = c.getLocation();
                l.getWorld().playSound(Sound.sound(Key.key("entity.chicken.ambient"), Sound.Source.NEUTRAL, volume, pitch),
                        l.getX(), l.getY(), l.getZ());
                l.getWorld().spawnParticle(Particle.NOTE, l.clone().add(0, 1.2, 0), 3, 0.3, 0.2, 0.3, 1);
            }, 14L + i * 8L);
        }
        int glow = cfg().getInt("flute.glow-seconds", 0);
        if (glow > 0 && !suddenDeath) {
            c.setGlowing(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (c.isValid() && !suddenDeath) c.setGlowing(false); }, glow * 20L);
        }
        for (Player o : online()) {
            if (!o.equals(p)) o.sendActionBar(Msg.mm(coloredName(p) + " <gray>a joué de la flûte... <yellow>le poulet caquette !"));
        }
        return true;
    }

    /** Boussole : s'active au clic droit, pointe vers le poulet pendant quelques secondes. */
    public void useCompass(Player p, ItemStack item) {
        if (state != State.RUNNING || !players.contains(p.getUniqueId()) || isEliminated(p)) return;
        long until = Gadgets.activeUntil(item);
        if (until > 0) {
            p.sendActionBar(Msg.mm("<gray>La boussole est déjà active."));
            return;
        }
        Gadgets.activate(item, System.currentTimeMillis() + Gadgets.compassSeconds(cfg()) * 1000L);
        if (chicken != null && chicken.isValid()) Gadgets.point(item, chicken.getLocation());
        Msg.sound(p, "item.lodestone_compass.lock", 1f);
        Msg.send(p, "<gold>La boussole s'agite... <gray>elle pointe vers le poulet pendant <white>"
                + Gadgets.compassSeconds(cfg()) + " s<gray>.");
    }

    private boolean holdsActiveCompass(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        return Gadgets.isCompass(hand) && Gadgets.activeUntil(hand) > System.currentTimeMillis();
    }

    /** Met à jour les boussoles actives (toutes les 5 ticks). */
    private void updateCompasses() {
        long now = System.currentTimeMillis();
        for (Player p : online()) {
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (!Gadgets.isCompass(item)) continue;
                long until = Gadgets.activeUntil(item);
                if (until <= 0) continue;
                if (now >= until) {
                    p.getInventory().setItem(i, null);
                    Msg.sound(p, "entity.item.break", 1f);
                    p.sendActionBar(Msg.mm("<gray>Ta boussole à poulet s'est brisée."));
                    continue;
                }
                if (chicken == null || !chicken.isValid()) continue;
                Gadgets.point(item, chicken.getLocation());
                p.getInventory().setItem(i, item);
                if (i == p.getInventory().getHeldItemSlot() && chicken.getWorld().equals(p.getWorld())) {
                    int dist = (int) Math.round(chicken.getLocation().distance(p.getLocation()));
                    int dy = (int) Math.round(chicken.getLocation().getY() - p.getLocation().getY());
                    String height = dy > 3 ? " <gray>(au-dessus ↑)" : dy < -3 ? " <gray>(en dessous ↓)" : "";
                    p.sendActionBar(Msg.mm("<gold>Poulet à <white>" + dist + " m" + height
                            + " <dark_gray>| <gray>" + Math.max(0, (until - now) / 1000) + "s"));
                }
            }
        }
    }

    /** Plume : bond en avant, sans dégâts de chute. @return true si la plume a été utilisée. */
    public boolean useFeather(Player p) {
        if (state != State.RUNNING || !players.contains(p.getUniqueId()) || isEliminated(p)) return false;
        long now = System.currentTimeMillis();
        Long until = featherCooldown.get(p.getUniqueId());
        if (until != null && until > now) return false;
        featherCooldown.put(p.getUniqueId(), now + Math.max(0, cfg().getInt("feather.cooldown-seconds", 2)) * 1000L);
        double power = cfg().getDouble("feather.power", 1.3);
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-3) dir = new Vector(0, 0, 1);
        p.setVelocity(dir.normalize().multiply(power).setY(Math.max(0.5, power * 0.5)));
        p.setFallDistance(0f);
        noFallUntil.put(p.getUniqueId(), now + 5000);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.1, 0.3, 0.05);
        p.getWorld().playSound(Sound.sound(Key.key("entity.ender_dragon.flap"), Sound.Source.PLAYER, 0.6f, 1.6f), p);
        return true;
    }

    // ================================================================ fin de manche / de partie

    private int roundsToWin() {
        return Math.max(1, cfg().getInt("rounds.points-to-win", 3));
    }

    private void deliver(Player carrier) {
        GameTeam t = teamOf.get(carrier.getUniqueId());
        plugin.stats().add(carrier, Stat.DELIVERIES);
        if (arena.format() == GameFormat.ROUNDS && t != null && !t.isFox()) {
            int pts = points.merge(t, 1, Integer::sum);
            if (pts >= roundsToWin()) {
                end(t, coloredName(carrier) + " <yellow>ramène le poulet décisif ! " + teamLabel(t)
                        + " <yellow>gagne <gold>" + pts + "</gold> points à " + secondBest(t) + " !");
                return;
            }
            newRound(carrier, t, pts);
            return;
        }
        String reason = coloredName(carrier) + " <yellow>a ramené le poulet dans l'enclos !";
        if (arena.mode() != TeamMode.SOLO && t != null) reason += " " + t.mini() + " <yellow>gagne !";
        end(t, reason);
    }

    private int secondBest(GameTeam winner) {
        int best = 0;
        for (Map.Entry<GameTeam, Integer> e : points.entrySet()) if (e.getKey() != winner) best = Math.max(best, e.getValue());
        return best;
    }

    private void newRound(Player carrier, GameTeam t, int pts) {
        removeChicken();
        launchFirework();
        round++;
        lastCarrier = null;
        grabCooldown.clear();
        campSeconds.clear();
        eggTimer = eggInterval();
        pauseCycles = 4 * Math.max(1, cfg().getInt("rounds.pause-seconds", 4));

        for (UUID id : new ArrayList<>(eliminated.keySet())) {
            eliminated.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setGameMode(GameMode.SURVIVAL);
        }
        for (Player p : online()) {
            p.setGlowing(false);
            if (p.isDead()) continue;
            p.teleport(respawnLocation());
            p.setFallDistance(0f);
            applyBorder(p);
            boolean scored = t.has(p.getUniqueId());
            title(p, scored ? "<green><bold>POINT !" : "<red><bold>POINT ADVERSE",
                    teamLabel(t) + " <gray>: <gold>" + pts + "<gray>/" + roundsToWin(), 50);
            Msg.sound(p, scored ? "entity.player.levelup" : "entity.villager.no", 1f);
        }
        broadcast(coloredName(carrier) + " <yellow>a ramené le poulet ! " + teamLabel(t) + " <yellow>marque <gold>"
                + pts + "/" + roundsToWin() + "</gold>. <gray>Manche " + round + " dans quelques secondes...");
        updateHud();
    }

    private void timeout() {
        if (arena.format() == GameFormat.ROUNDS) {
            GameTeam best = null;
            int bestPts = 0;
            boolean tie = false;
            for (Map.Entry<GameTeam, Integer> e : points.entrySet()) {
                if (e.getValue() > bestPts) {
                    best = e.getKey();
                    bestPts = e.getValue();
                    tie = false;
                } else if (e.getValue() == bestPts && bestPts > 0) {
                    tie = true;
                }
            }
            if (best != null && !tie) {
                end(best, "<yellow>Temps écoulé ! " + teamLabel(best) + " <yellow>gagne aux points (<gold>" + bestPts + "</gold>) !");
                return;
            }
            if (tie && fox == null) {
                end(null, "<yellow>Temps écoulé ! <gray>Égalité, personne ne gagne.");
                return;
            }
        }
        if (foxTeam != null && fox != null && players.contains(fox)) {
            end(foxTeam, "<yellow>Temps écoulé ! Personne n'a ramené le poulet : <gold><bold>le Renard gagne</bold></gold> !");
            return;
        }
        end(null, "<red>Temps écoulé ! Personne n'a ramené le poulet.");
    }

    public void end(GameTeam winner, String reason) {
        if (state != State.RUNNING) return;
        state = State.ENDING;
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        removeChicken();
        if (winner != null) launchFirework();
        updateHud();

        for (Player p : online()) {
            p.setGlowing(false);
            boolean won = winner != null && winner.has(p.getUniqueId());
            if (won) plugin.stats().add(p, Stat.WINS);
            String t = winner == null ? "<red><bold>PERDU" : won ? "<gold><bold>VICTOIRE !" : "<red><bold>DÉFAITE";
            title(p, t, reason, 80);
            Msg.sound(p, won ? "ui.toast.challenge_complete" : "entity.chicken.death", 1f);
        }
        broadcast(reason);
        broadcast("<gray>Retour au spawn dans 5 secondes...");
        plugin.leaderboards().refresh();
        Bukkit.getScheduler().runTaskLater(plugin, this::finish, 100L);
    }

    private void finish() {
        if (state != State.ENDING) return;
        for (Player p : online()) restorePlayer(p);
        resetArena();
        plugin.leaderboards().refresh();
    }

    /** Arrêt forcé par un admin. */
    public void forceStop() {
        if (state == State.RUNNING) end(null, "<red>Partie arrêtée par un administrateur.");
        else if (state != State.ENDING) shutdown();
    }

    /** Arrêt immédiat (désactivation du plugin, suppression d'arène...). */
    public void shutdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        removeChicken();
        for (Player p : online()) restorePlayer(p);
        resetArena();
    }

    private void resetArena() {
        for (UUID id : players) plugin.games().unbind(id);
        players.clear();
        ready.clear();
        snapshots.clear();
        teams.clear();
        teamOf.clear();
        points.clear();
        eliminated.clear();
        grabCooldown.clear();
        fluteCooldown.clear();
        featherCooldown.clear();
        noFallUntil.clear();
        campSeconds.clear();
        fox = null;
        foxTeam = null;
        lastCarrier = null;
        gameSkin = null;
        suddenDeath = false;
        pauseCycles = 0;
        if (restorer != null) {
            restorer.restore(); // les blocs cassés / posés reviennent à l'état de base
            restorer = null;
        }
        clearEntities();
        setChunkTickets(false);
        board = null;
        sidebar = null;
        bossBar = null;
        state = State.WAITING;
    }

    private void restorePlayer(Player p) {
        UUID id = p.getUniqueId();
        plugin.games().unbind(id);
        if (chicken != null && p.getPassengers().contains(chicken)) p.removePassenger(chicken);
        p.setGlowing(false);
        p.setWorldBorder(null);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (bossBar != null) p.hideBossBar(bossBar);

        PlayerSnapshot snapshot = snapshots.remove(id);
        if (p.isDead()) {
            // Écran de mort : on rendra l'inventaire à la réapparition
            if (snapshot != null) plugin.games().addPending(id, snapshot);
            return;
        }
        Players.reset(p, GameMode.ADVENTURE);
        if (snapshot != null) snapshot.restore(p);
        p.teleport(plugin.mainSpawn());
        plugin.giveMenuItem(p);
    }

    private void clearEntities() {
        World w = arena.world();
        if (w == null) return;
        Region r = arena.region();
        for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++) {
            for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                for (Entity e : w.getChunkAt(cx, cz).getEntities()) {
                    if (e instanceof Player || !r.contains(e.getLocation())) continue;
                    boolean ours = e.getPersistentDataContainer().has(Items.chickenKey(), PersistentDataType.BYTE);
                    if (ours || trackedEntities.contains(e.getUniqueId())
                            || e instanceof Item || e instanceof ExperienceOrb || e instanceof Projectile
                            || e instanceof FallingBlock || e instanceof TNTPrimed) {
                        e.remove();
                    }
                }
            }
        }
        trackedEntities.clear();
    }

    private void setChunkTickets(boolean add) {
        World w = arena.world();
        if (w == null) return;
        Region r = arena.region();
        for (int cx = r.minX() >> 4; cx <= r.maxX() >> 4; cx++) {
            for (int cz = r.minZ() >> 4; cz <= r.maxZ() >> 4; cz++) {
                if (add) w.addPluginChunkTicket(cx, cz, plugin);
                else w.removePluginChunkTicket(cx, cz, plugin);
            }
        }
    }

    private void launchFirework() {
        Location c = arena.enclosureCenter().add(0, 1, 0);
        Firework fw = c.getWorld().spawn(c, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.ORANGE).withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE).flicker(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    // ================================================================ positions / bordure

    /** Point de départ / réapparition : devant la porte de l'enclos. */
    public Location respawnLocation() {
        Location s = arena.spawnPoint();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location l = Locs.safeNear(s.getWorld(), s.getBlockX() + r.nextInt(-2, 3), s.getBlockZ() + r.nextInt(0, 3), s.getBlockY());
        l.setYaw(180f);
        return l;
    }

    /** Bordure visible (et infranchissable) autour de l'arène, uniquement pour ce joueur. */
    public void applyBorder(Player p) {
        Region r = arena.region();
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(r.centerX(), r.centerZ());
        border.setSize(r.size());
        border.setWarningDistance(0);
        p.setWorldBorder(border);
    }
}
