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
            if (r == null || p == null || !p.getWorld().getName().equals(r.world())) continue;
            double y = p.getLocation().getY() + 1;
            double x0 = r.minX(), x1 = r.maxX() + 1, z0 = r.minZ(), z1 = r.maxZ() + 1;
            for (double d = 0; d <= r.size(); d += 1.5) {
                for (double h = -1; h <= 2; h += 3) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x0 + d, y + h, z0, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x0 + d, y + h, z1, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x0, y + h, z0 + d, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x1, y + h, z0 + d, 1, 0, 0, 0, 0);
                }
            }
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
        sessions.put(p.getUniqueId(), new SetupSession());
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
        Msg.send(p, "<yellow>Étape 2 : <white>fais un clic droit au sol avec le <aqua>Bâton de zone</aqua> "
                + "<white>pour créer la zone (" + plugin.arenaSize() + "×" + plugin.arenaSize() + " blocs autour de ce point).");
        Msg.sound(p, "entity.experience_orb.pickup", 1f);
    }

    public void onZoneClick(Player p, Block block) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (s.stage == SetupSession.Stage.NAME) {
            Msg.send(p, "<red>Écris d'abord le nom de l'arène dans le chat.");
            return;
        }
        Region region = Region.around(block.getLocation(), plugin.arenaSize());
        Arena other = plugin.arenas().overlapping(region);
        if (other != null) {
            Msg.send(p, "<red>Cette zone chevauche l'arène <yellow>" + other.name() + "</yellow>. Choisis un autre endroit.");
            return;
        }
        if (s.hasEnclosure() && !fits(region, s.ex, s.ez, s.n)) {
            EnclosureBuilder.undo(s.enclosureOriginal);
            s.enclosureOriginal = null;
            Msg.send(p, "<gold>L'enclos n'était plus dans la nouvelle zone : il a été retiré, replace-le avec la faux.");
        }
        s.region = region;
        s.stage = SetupSession.Stage.SETTINGS;
        Items.give(p, Items.enclosTool());
        Msg.send(p, "<green>Zone définie ! <gray>(" + region.minX() + ", " + region.minZ() + ") → ("
                + region.maxX() + ", " + region.maxZ() + "), toute la hauteur : surface et cavernes. <dark_gray>(contour en particules vertes)");
        Msg.send(p, "<yellow>Étape 3 : <white>choisis les équipes et le PvP dans le menu, puis place l'enclos "
                + "avec la <yellow>Faux de l'enclos</yellow> (clic droit au sol).");
        Msg.sound(p, "block.amethyst_block.chime", 1f);
        Menus.openSetup(p, s);
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
        Arena other = plugin.arenas().overlapping(s.region);
        if (other != null) {
            Msg.send(p, "<red>La zone chevauche l'arène <yellow>" + other.name() + "</yellow>.");
            return;
        }
        Arena arena = new Arena(s.name, s.region, s.mode, s.pvp, true, s.ex, s.ey, s.ez, s.n);
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
