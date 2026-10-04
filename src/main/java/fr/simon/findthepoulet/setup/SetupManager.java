package fr.simon.findthepoulet.setup;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.Arena;
import fr.simon.findthepoulet.arena.EnclosureBuilder;
import fr.simon.findthepoulet.arena.Region;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Locs;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Gère les créations d'arène : nom → bâton (zone) → réglages → faux (enclos) → validation. */
public final class SetupManager {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{2,24}");
    /** Distance minimale entre l'enclos et le bord de la zone. */
    private static final int MARGIN = 4;

    private final FindThePoulet plugin;
    private final Map<UUID, SetupSession> sessions = new ConcurrentHashMap<>();

    public SetupManager(FindThePoulet plugin) {
        this.plugin = plugin;
        // Aperçu de la limite de la zone en particules (visible uniquement par l'admin)
        Bukkit.getScheduler().runTaskTimer(plugin, this::drawPreviews, 10L, 10L);
    }

    private void drawPreviews() {
        for (Map.Entry<UUID, SetupSession> e : sessions.entrySet()) {
            Region r = e.getValue().region;
            Player p = Bukkit.getPlayer(e.getKey());
            if (r == null || p == null) continue;
            fr.simon.findthepoulet.util.Outline.draw(p, r.world(), r.minX(), r.minZ(), r.maxX(), r.maxZ(), Particle.HAPPY_VILLAGER);
        }
    }

    public SetupSession get(UUID id) {
        return sessions.get(id);
    }

    public void start(Player p) {
        SetupSession existing = sessions.get(p.getUniqueId());
        if (existing != null) {
            if (existing.stage == SetupSession.Stage.NAME) askName(p);
            else Menus.openSetup(p, existing);
            return;
        }
        if (plugin.games().of(p) != null) {
            Msg.send(p, "<red>Quitte ta partie avant de créer une arène.");
            return;
        }
        SetupSession session = new SetupSession();
        session.size = plugin.arenaSize();
        sessions.put(p.getUniqueId(), session);
        p.closeInventory();
        askName(p);
    }

    private void askName(Player p) {
        Msg.send(p, "<yellow>Création d'arène : <white>écris le nom de l'arène dans le chat.");
        Msg.send(p, "<gray>(2 à 24 caractères : lettres, chiffres, _ ou -) - écris <red>annuler</red> pour arrêter.");
        Msg.sound(p, "block.note_block.pling", 1.4f);
    }

    /** Appelé (sur le thread principal) quand l'admin écrit dans le chat pendant l'étape NAME. */
    public void handleName(Player p, String text) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null || s.stage != SetupSession.Stage.NAME) return;
        String name = text.trim();
        if (name.equalsIgnoreCase("annuler") || name.equalsIgnoreCase("cancel")) {
            cancel(p, true);
            return;
        }
        if (!NAME.matcher(name).matches()) {
            Msg.send(p, "<red>Nom invalide. <gray>2 à 24 caractères : lettres, chiffres, _ ou - (sans espace).");
            return;
        }
        if (plugin.arenas().exists(name)) {
            Msg.send(p, "<red>Une arène nommée <yellow>" + name + "</yellow> existe déjà.");
            return;
        }
        s.name = name;
        s.stage = SetupSession.Stage.ZONE;
        Items.give(p, Items.zoneTool());
        Msg.send(p, "<green>Arène <gold>" + name + "</gold> !");
        Msg.send(p, "<yellow>Étape 2 : <white>avec le <aqua>Bâton de zone</aqua>, <yellow>clic gauche</yellow> sur un bloc = coin 1, "
                + "<yellow>clic droit</yellow> sur le bloc opposé = coin 2. <gray>(rectangle de la taille que tu veux)");
        Msg.send(p, "<gray>Autres options dans le menu (<yellow>clic droit dans l'air</yellow>) : limiter la hauteur (cube), "
                + "ou une taille fixe 64 à 1024 autour d'un point.");
        Msg.sound(p, "entity.experience_orb.pickup", 1f);
    }

    /** Clic avec le bâton en mode coins : 0 = coin 1 (clic gauche), 1 = coin 2 (clic droit). */
    public void onZoneCorner(Player p, Block block, int idx) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (s.stage == SetupSession.Stage.NAME) {
            Msg.send(p, "<red>Écris d'abord le nom de l'arène dans le chat.");
            return;
        }
        if (s.corners[1 - idx] != null && !s.corners[1 - idx].getWorld().equals(block.getWorld())) s.corners[1 - idx] = null;
        s.corners[idx] = block.getLocation();
        Msg.send(p, "<green>Coin " + (idx + 1) + " : <white>" + block.getX() + ", " + block.getY() + ", " + block.getZ());
        Msg.sound(p, "block.note_block.pling", idx == 0 ? 1.2f : 1.6f);
        if (s.corners[0] == null || s.corners[1] == null) {
            Msg.send(p, "<gray>Maintenant le coin " + (2 - idx) + " : <yellow>" + (idx == 0 ? "clic droit" : "clic gauche") + " <gray>sur le bloc opposé.");
            return;
        }
        applyCorners(p, s);
    }

    /** Recalcule la zone à partir des 2 coins (après un clic ou un changement de l'option hauteur). */
    private boolean applyCorners(Player p, SetupSession s) {
        Region region = Region.between(s.corners[0], s.corners[1], s.withHeight);
        int min = 2 * MARGIN + plugin.enclosureSize() + 6;
        if (region.width() < min || region.depth() < min) {
            Msg.send(p, "<red>Zone trop petite (" + region.width() + " × " + region.depth() + ") : il faut au moins "
                    + min + " × " + min + " blocs pour l'enclos et ses marges.");
            return false;
        }
        if (s.withHeight && region.maxY() - region.minY() < 8) {
            Msg.send(p, "<red>Zone trop basse : il faut au moins 8 blocs de hauteur entre les deux coins "
                    + "<gray>(clique un coin en hauteur et l'autre en profondeur, ou désactive la limite de hauteur).");
            return false;
        }
        return setRegion(p, s, region);
    }

    public void onZoneClick(Player p, Block block) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (s.stage == SetupSession.Stage.NAME) {
            Msg.send(p, "<red>Écris d'abord le nom de l'arène dans le chat.");
            return;
        }
        Region region = Region.around(block.getLocation(), s.size);
        s.zoneCenter = block.getLocation();
        setRegion(p, s, region);
    }

    private boolean setRegion(Player p, SetupSession s, Region region) {
        if (!canUse(p, region)) return false;
        if (s.hasEnclosure() && !fitsAll(region, s)) {
            EnclosureBuilder.undo(s.enclosureOriginal);
            s.enclosureOriginal = null;
            Msg.send(p, "<gold>L'enclos n'était plus dans la nouvelle zone : il a été retiré, replace-le avec la faux.");
        }
        s.region = region;
        s.stage = SetupSession.Stage.SETTINGS;
        Items.give(p, Items.enclosTool());
        Msg.send(p, "<green>Zone définie : <white>" + region.describe() + "</white> blocs <gray>("
                + region.minX() + ", " + region.minZ() + " → " + region.maxX() + ", " + region.maxZ() + ")"
                + (region.fullHeight() ? ", toute la hauteur" : "") + ". <dark_gray>(contour en particules vertes)");
        if (region.size() > 256) {
            Msg.send(p, "<gold>Grande arène : <gray>pré-génère la zone (plugin Chunky par ex.) pour éviter les lags au lancement.");
        }
        Msg.send(p, "<yellow>Étape 3 : <white>choisis les équipes et le PvP dans le menu, puis place l'enclos "
                + "avec la <yellow>Faux de l'enclos</yellow> (clic droit au sol).");
        Msg.sound(p, "block.amethyst_block.chime", 1f);
        Menus.openSetup(p, s);
        return true;
    }

    private static boolean fitsAll(Region r, SetupSession s) {
        return fits(r, s.ex, s.ez, s.n) && (r.fullHeight() || (s.ey - 1 >= r.minY() && s.ey + 3 <= r.maxY()));
    }

    /** Menu : bascule entre "2 coins" et "taille fixe autour d'un point". */
    public void toggleZoneMode(Player p) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        s.cornerMode = !s.cornerMode;
        Msg.send(p, s.cornerMode
                ? "<gray>Mode <white>2 coins</white> : clic gauche = coin 1, clic droit = coin 2 avec le bâton."
                : "<gray>Mode <white>taille fixe</white> : clic droit au sol avec le bâton = centre de la zone.");
    }

    /** Menu : limiter (ou non) la zone à la hauteur entre les 2 coins. */
    public void toggleHeight(Player p) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        s.withHeight = !s.withHeight;
        if (s.cornerMode && s.corners[0] != null && s.corners[1] != null) {
            if (!applyCorners(p, s)) s.withHeight = !s.withHeight; // on annule si la zone n'est pas valide
        }
    }

    public void onEnclosClick(Player p, Block block) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (s.region == null) {
            Msg.send(p, "<red>Définis d'abord la zone avec le bâton.");
            return;
        }
        if (!s.region.contains(block)) {
            Msg.send(p, "<red>L'enclos doit être placé dans la zone de l'arène.");
            return;
        }
        int n = plugin.enclosureSize();
        int ex = block.getX() - (n - 1) / 2;
        int ez = block.getZ() - (n - 1) / 2;
        int ey = block.getY() + 1;
        if (!fits(s.region, ex, ez, n)) {
            Msg.send(p, "<red>Trop près du bord ! <gray>L'enclos doit être à au moins " + MARGIN + " blocs de la limite.");
            return;
        }
        if (!s.region.fullHeight() && (ey - 1 < s.region.minY() || ey + 3 > s.region.maxY())) {
            Msg.send(p, "<red>L'enclos doit tenir entre le sol et le plafond de la zone (Y " + s.region.minY() + " → " + s.region.maxY() + ").");
            return;
        }
        World w = block.getWorld();
        if (ey + 3 >= w.getMaxHeight()) {
            Msg.send(p, "<red>Trop haut pour construire l'enclos.");
            return;
        }
        if (s.hasEnclosure()) EnclosureBuilder.undo(s.enclosureOriginal);
        s.enclosureOriginal = EnclosureBuilder.build(w, ex, ey, ez, n);
        s.ex = ex;
        s.ey = ey;
        s.ez = ez;
        s.n = n;

        // Si l'admin se retrouve dans l'enclos, on le pose devant la porte
        Location pl = p.getLocation();
        if (pl.getX() >= ex - 1 && pl.getX() < ex + n + 1 && pl.getZ() >= ez - 1 && pl.getZ() < ez + n + 1) {
            Location front = Locs.safeNear(w, ex + n / 2, ez + n + 2, ey);
            front.setYaw(180f);
            p.teleport(front);
        }
        Msg.send(p, "<green>Enclos placé ! <gray>Les joueurs apparaîtront devant sa porte.");
        Msg.send(p, "<yellow>Dernière étape : <white>ouvre le menu (clic droit dans l'air) et clique sur <green>Valider l'arène</green>.");
        Msg.sound(p, "block.wood.place", 0.8f);
    }

    /** Vérifie qu'une zone ne chevauche ni une autre arène, ni une zone lobby protégée. */
    private boolean canUse(Player p, Region region) {
        Arena other = plugin.arenas().overlapping(region);
        if (other != null) {
            Msg.send(p, "<red>Cette zone chevauche l'arène <yellow>" + other.name() + "</yellow>. Choisis un autre endroit ou une taille plus petite.");
            return false;
        }
        var zone = plugin.zones().overlapping(region);
        if (zone != null) {
            Msg.send(p, "<red>Cette zone chevauche la zone protégée <yellow>" + zone.name() + "</yellow>. Choisis un autre endroit ou une taille plus petite.");
            return false;
        }
        return true;
    }

    /** Taille suivante (64 → 128 → 256 → 512 → 1024 → 64) ; recalcule la zone si elle est déjà placée. */
    public void cycleSize(Player p) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        java.util.List<Integer> sizes = new java.util.ArrayList<>(plugin.getConfig().getIntegerList("arena-sizes"));
        sizes.removeIf(v -> v < 32);
        if (sizes.isEmpty()) sizes = java.util.List.of(64, 128, 256, 512, 1024);
        if (s.cornerMode) {
            s.cornerMode = false;
            Msg.send(p, "<gray>Passage en mode <white>taille fixe</white> : clic droit au sol avec le bâton = centre de la zone.");
        }
        int start = Math.max(0, sizes.indexOf(s.size));
        for (int k = 1; k <= sizes.size(); k++) {
            int next = sizes.get((start + k) % sizes.size());
            if (s.zoneCenter == null) {
                s.size = next;
                return;
            }
            Region region = Region.around(s.zoneCenter, next);
            if (plugin.arenas().overlapping(region) != null || plugin.zones().overlapping(region) != null) {
                Msg.send(p, "<gray>" + next + "×" + next + " déborde sur une autre arène ou zone protégée, taille suivante...");
                continue;
            }
            if (s.hasEnclosure() && !fits(region, s.ex, s.ez, s.n)) {
                EnclosureBuilder.undo(s.enclosureOriginal);
                s.enclosureOriginal = null;
                Msg.send(p, "<gold>L'enclos ne tenait plus dans la zone : il a été retiré, replace-le avec la faux.");
            }
            s.region = region;
            s.size = next;
            if (next >= 512) {
                Msg.send(p, "<gold>Grande arène (" + next + "×" + next + ") : <gray>pré-génère la zone (plugin Chunky par ex.) "
                        + "pour éviter les lags au lancement des parties.");
            }
            return;
        }
    }

    private static boolean fits(Region r, int ex, int ez, int n) {
        return ex - 1 - r.minX() >= MARGIN && r.maxX() - (ex + n) >= MARGIN
                && ez - 1 - r.minZ() >= MARGIN && r.maxZ() - (ez + n) >= MARGIN;
    }

    public void validate(Player p) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (s.name == null || s.region == null) {
            Msg.send(p, "<red>Il faut d'abord définir la zone avec le bâton.");
            return;
        }
        if (!s.hasEnclosure()) {
            Msg.send(p, "<red>Il faut d'abord placer l'enclos avec la faux.");
            return;
        }
        if (plugin.arenas().exists(s.name)) {
            Msg.send(p, "<red>Une arène nommée <yellow>" + s.name + "</yellow> existe déjà.");
            return;
        }
        if (!canUse(p, s.region)) return;
        Arena arena = new Arena(s.name, s.region, s.mode, s.pvp, true, s.ex, s.ey, s.ez, s.n);
        arena.setFormat(s.format);
        arena.setPvpMode(s.pvpMode);
        arena.setFox(s.fox);
        arena.setKit(s.kit);
        arena.setSkin(s.skin);
        plugin.arenas().add(arena);
        sessions.remove(p.getUniqueId());
        cleanup(p);
        p.closeInventory();
        Msg.send(p, "<green><bold>Arène " + arena.name() + " validée !</bold> <gray>Elle est jouable depuis la tête de poulet.");
        Msg.sound(p, "ui.toast.challenge_complete", 1f);
    }

    public void cancel(Player p, boolean notify) {
        SetupSession s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        if (s.hasEnclosure()) EnclosureBuilder.undo(s.enclosureOriginal);
        cleanup(p);
        if (notify) {
            p.closeInventory();
            Msg.send(p, "<gray>Création d'arène annulée.");
        }
    }

    public void cancelAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                cancel(p, false);
            } else {
                SetupSession s = sessions.remove(id);
                if (s != null && s.hasEnclosure()) EnclosureBuilder.undo(s.enclosureOriginal);
            }
        }
    }

    private void cleanup(Player p) {
        Items.removeTagged(p, Items.ZONE, Items.ENCLOS);
    }
}
