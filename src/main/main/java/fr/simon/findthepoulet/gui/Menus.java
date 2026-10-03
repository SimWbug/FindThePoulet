package fr.simon.findthepoulet.gui;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.Arena;
import fr.simon.findthepoulet.arena.Region;
import fr.simon.findthepoulet.game.Game;
import fr.simon.findthepoulet.game.GameFormat;
import fr.simon.findthepoulet.stats.Stat;
import fr.simon.findthepoulet.setup.SetupSession;
import fr.simon.findthepoulet.skin.ChickenSkin;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Menus {

    public static final String ADMIN_PERM = "findthepoulet.admin";

    private Menus() {}

    private static FindThePoulet plugin() {
        return FindThePoulet.get();
    }

    private static Inventory create(MenuHolder holder, int size, String title) {
        Inventory inv = Bukkit.createInventory(holder, size, Msg.mm(title));
        holder.setInventory(inv);
        return inv;
    }

    private static void fill(Inventory inv, int from, int to) {
        ItemStack pane = Items.build(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = from; i <= to; i++) if (Items.isEmpty(inv.getItem(i))) inv.setItem(i, pane);
    }

    private static String onOff(boolean b) {
        return b ? "<green>Activé" : "<red>Désactivé";
    }

    // ================================================================ menu principal

    public static void openMain(Player p) {
        FindThePoulet plugin = plugin();
        boolean admin = p.hasPermission(ADMIN_PERM);
        Game current = plugin.games().of(p);
        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAIN, null);
        Inventory inv = create(holder, 54, "<dark_gray>Find The Poulet");

        int slot = 0;
        for (Arena a : plugin.arenas().all()) {
            if (slot > 44) break;
            Game g = plugin.games().existing(a.name());
            Game.State st = g == null ? Game.State.WAITING : g.state();
            int count = g == null ? 0 : g.size();

            Material mat;
            String state;
            if (!a.isEnabled()) { mat = Material.GRAY_CONCRETE; state = "<gray>Désactivée"; }
            else if (st == Game.State.RUNNING || st == Game.State.ENDING) { mat = Material.RED_CONCRETE; state = "<red>Partie en cours"; }
            else if (st == Game.State.STARTING) { mat = Material.YELLOW_CONCRETE; state = "<yellow>Démarrage..."; }
            else { mat = Material.LIME_CONCRETE; state = "<green>En attente de joueurs"; }

            List<String> lore = new ArrayList<>();
            lore.add("<gray>État : " + state);
            lore.add("<gray>Joueurs : <white>" + count + "/" + plugin.maxPlayers());
            lore.add("<gray>Équipes : <white>" + a.mode().label());
            lore.add("<gray>PvP : " + onOff(a.isPvp()));
            lore.add("");
            if (current == g && g != null) lore.add("<gold>▶ Tu es dans cette partie");
            else lore.add("<yellow>Clic gauche : <white>rejoindre");
            if (admin) lore.add("<aqua>Clic droit : <white>gérer l'arène");

            ItemStack item = Items.build(mat, "<gold><bold>" + a.name(), lore.toArray(String[]::new));
            if (current == g && g != null) item.editMeta(m -> m.setEnchantmentGlintOverride(true));
            inv.setItem(slot, item);
            holder.arenaSlots().put(slot, a.name());
            slot++;
        }
        if (slot == 0) {
            inv.setItem(22, Items.build(Material.BARRIER, "<red>Aucune arène",
                    admin ? "<gray>Crée la première avec l'étoile en bas à gauche !" : "<gray>Un admin doit en créer une."));
        }

        if (admin) {
            inv.setItem(45, Items.build(Material.COMMAND_BLOCK, "<aqua><bold>Outils admin",
                    "<gray>Créer une arène, zone lobby / spawn,",
                    "<gray>hologrammes de classement..."));
        }
        if (current != null) {
            boolean ready = current.isReady(p);
            inv.setItem(47, Items.build(ready ? Material.LIME_DYE : Material.GRAY_DYE,
                    ready ? "<green><bold>Tu es prêt !" : "<yellow><bold>Se mettre prêt",
                    ready ? "<gray>Clique pour ne plus être prêt" : "<gray>Clique quand tu es prêt à jouer"));
            if (admin && (current.state() == Game.State.WAITING || current.state() == Game.State.STARTING)) {
                inv.setItem(46, Items.build(Material.LIME_CANDLE, "<green><bold>Forcer le lancement",
                        "<gray>Admin : lance la partie maintenant,", "<gray>même seul et sans les \"prêt\""));
            }
            inv.setItem(51, Items.build(Material.RED_BED, "<red><bold>Quitter la partie",
                    "<gray>Partie : <white>" + current.arena().name()));
        }
        inv.setItem(49, Items.build(Material.BOOK, "<yellow><bold>Comment jouer ?",
                "<gray>Un seul poulet apparaît quelque part",
                "<gray>dans l'arène (surface ou cavernes).",
                "<white>Clic droit <gray>dessus pour l'attraper,",
                "<gray>puis ramène-le dans <gold>l'enclos</gold> pour gagner !",
                "",
                "<gray>Le poulet fuit : <white>accroupis-toi</white> pour l'approcher.",
                "<gray>Frappe le porteur pour qu'il le lâche.",
                "<gray>Coffres cachés : flûtes, boussoles, plumes.",
                "<red>Si le poulet meurt, tout le monde perd.",
                "<gray>Durée : <white>" + (plugin.gameDuration() / 60) + " min"));
        inv.setItem(53, Items.build(Material.GOLD_INGOT, "<gold><bold>Classements",
                "<gray>Victoires, poulets attrapés,", "<gray>poulets ramenés..."));
        fill(inv, 45, 53);
        p.openInventory(inv);
    }

    // ================================================================ gestion d'une arène

    public static void openArenaAdmin(Player p, Arena a) {
        FindThePoulet plugin = plugin();
        Game g = plugin.games().existing(a.name());
        MenuHolder holder = new MenuHolder(MenuHolder.Type.ARENA_ADMIN, a.name());
        Inventory inv = create(holder, 36, "<dark_gray>Arène : " + a.name());
        Region r = a.region();

        // Ligne 1 : réglages de jeu
        inv.setItem(10, Items.build(Material.WHITE_BANNER, "<yellow><bold>Équipes : <white>" + a.mode().label(),
                "<gray>Solo, Duo, Trio ou Quatuor", "<yellow>Clic : <white>changer"));
        inv.setItem(11, Items.build(a.isPvp() ? Material.IRON_SWORD : Material.WOODEN_SWORD,
                "<yellow><bold>PvP : " + onOff(a.isPvp()), "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(12, Items.build(a.format() == GameFormat.ROUNDS ? Material.CLOCK : Material.HAY_BLOCK,
                "<yellow><bold>Format : <white>" + a.format().label(),
                "<gray>Classique : le 1er poulet ramené gagne",
                "<gray>Manches : 1ère équipe à <white>" + plugin.getConfig().getInt("rounds.points-to-win", 3) + "<gray> poulets",
                "<yellow>Clic : <white>changer"));
        inv.setItem(13, Items.build(a.isFox() ? Material.SWEET_BERRIES : Material.DEAD_BUSH,
                "<gold><bold>Mode Renard : " + onOff(a.isFox()),
                "<gray>Un joueur tiré au sort est le Renard :",
                "<gray>il ne porte pas le poulet mais élimine",
                "<gray>le porteur qu'il frappe. (3 joueurs min.)",
                "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(14, Items.build(a.isKit() ? Material.WOODEN_PICKAXE : Material.BARRIER,
                "<yellow><bold>Kit de départ : " + onOff(a.isKit()),
                "<gray>Outils en bois + nourriture (config.yml)", "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(15, skinItem(a.skin()));
        inv.setItem(16, Items.build(a.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<yellow><bold>Arène : " + onOff(a.isEnabled()), "<yellow>Clic : <white>activer / désactiver"));

        // Ligne 2 : gestion
        inv.setItem(20, Items.build(Material.MAP, "<gold><bold>" + a.name(),
                "<gray>Monde : <white>" + r.world(),
                "<gray>Zone : <white>" + r.minX() + ", " + r.minZ() + " → " + r.maxX() + ", " + r.maxZ(),
                "<gray>Enclos : <white>" + a.ex() + ", " + a.ey() + ", " + a.ez(),
                "<gray>Partie : <white>" + (g == null ? "aucune" : g.state().name() + " (" + g.size() + " joueurs)")));
        inv.setItem(21, Items.build(Material.LIME_CANDLE, "<green><bold>Forcer le lancement",
                "<gray>Lance la partie avec les joueurs du lobby,", "<gray>même seul et sans attendre les \"prêt\""));
        inv.setItem(22, Items.build(Material.ENDER_PEARL, "<aqua><bold>Se téléporter", "<gray>Devant l'enclos"));
        inv.setItem(23, Items.build(Material.REDSTONE_BLOCK, "<red><bold>Arrêter la partie",
                "<gray>Arrête la partie / le lobby en cours", "<gray>et remet l'arène à zéro"));
        inv.setItem(24, Items.build(Material.TNT, "<dark_red><bold>Supprimer l'arène",
                "<red>Shift + clic <gray>pour confirmer", "<gray>(l'enclos reste en place dans le monde)"));
        inv.setItem(27, Items.build(Material.ARROW, "<gray>Retour"));
        fill(inv, 0, 35);
        p.openInventory(inv);
    }

    private static ItemStack skinItem(String choice) {
        FindThePoulet plugin = plugin();
        ChickenSkin s = plugin.skins().get(choice);
        ItemStack item = s != null ? s.item() : new ItemStack(Material.CARVED_PUMPKIN);
        String state = plugin.skins().isEnabled() ? "" : " <red>(désactivé dans la config)";
        item.editMeta(meta -> {
            meta.displayName(Msg.item("<yellow><bold>Déguisement : <white>" + plugin.skins().label(choice) + state));
            meta.lore(java.util.List.of(
                    Msg.item(s != null ? "<gray>Poulet : " + s.name() : "<gray>Aléatoire : un skin différent à chaque partie"),
                    Msg.item("<yellow>Clic gauche : <white>changer"),
                    Msg.item("<yellow>Clic droit : <white>aperçu devant toi")));
        });
        return item;
    }

    // ================================================================ création d'arène

    public static void openSetup(Player p, SetupSession s) {
        FindThePoulet plugin = plugin();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SETUP, s.name());
        Inventory inv = create(holder, 36, "<dark_gray>Création : " + (s.name() == null ? "?" : s.name()));

        // Ligne 1 : réglages
        inv.setItem(10, Items.build(Material.FILLED_MAP, "<aqua><bold>Taille : <white>" + s.size() + " × " + s.size(),
                "<gray>64, 128, 256, 512 ou 1024 blocs de côté",
                "<gray>(toute la hauteur : surface + cavernes)",
                s.region() != null ? "<gray>La zone est recalculée autour du même point" : "<gray>Choisis-la avant ou après le bâton",
                "<yellow>Clic : <white>taille suivante"));
        inv.setItem(11, Items.build(Material.WHITE_BANNER, "<yellow><bold>Équipes : <white>" + s.mode().label(),
                "<gray>Solo, Duo, Trio ou Quatuor", "<yellow>Clic : <white>changer"));
        inv.setItem(12, Items.build(s.pvp() ? Material.IRON_SWORD : Material.WOODEN_SWORD,
                "<yellow><bold>PvP : " + onOff(s.pvp()), "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(13, Items.build(s.format() == GameFormat.ROUNDS ? Material.CLOCK : Material.HAY_BLOCK,
                "<yellow><bold>Format : <white>" + s.format().label(),
                "<gray>Classique : le 1er poulet ramené gagne",
                "<gray>Manches : 1ère équipe à <white>" + plugin.getConfig().getInt("rounds.points-to-win", 3) + "<gray> poulets",
                "<yellow>Clic : <white>changer"));
        inv.setItem(14, Items.build(s.fox() ? Material.SWEET_BERRIES : Material.DEAD_BUSH,
                "<gold><bold>Mode Renard : " + onOff(s.fox()),
                "<gray>Un joueur tiré au sort élimine",
                "<gray>le porteur qu'il frappe (3 joueurs min.)",
                "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(15, Items.build(s.kit() ? Material.WOODEN_PICKAXE : Material.BARRIER,
                "<yellow><bold>Kit de départ : " + onOff(s.kit()),
                "<gray>Outils en bois + nourriture", "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(16, skinItem(s.skin()));

        // Ligne 2 : outils + validation
        inv.setItem(20, Items.build(Material.STICK, (s.region() != null ? "<green>✔ " : "<red>✘ ") + "<aqua><bold>Zone",
                s.region() != null ? "<gray>Zone définie (" + s.region().size() + "×" + s.region().size() + ")" : "<gray>Pas encore définie",
                "<yellow>Clic : <white>récupérer le bâton"));
        inv.setItem(22, Items.build(Material.IRON_HOE, (s.hasEnclosure() ? "<green>✔ " : "<red>✘ ") + "<yellow><bold>Enclos",
                s.hasEnclosure() ? "<gray>Enclos placé" : "<gray>Pas encore placé",
                "<yellow>Clic : <white>récupérer la faux"));
        boolean ok = s.region() != null && s.hasEnclosure();
        inv.setItem(24, Items.build(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "<green><bold>Valider l'arène",
                ok ? "<gray>L'arène deviendra jouable" : "<red>Zone et enclos requis"));
        inv.setItem(31, Items.build(Material.BARRIER, "<red><bold>Annuler la création"));
        fill(inv, 0, 35);
        p.openInventory(inv);
    }

    // ================================================================ outils admin

    public static void openAdmin(Player p) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.ADMIN, null);
        Inventory inv = create(holder, 27, "<dark_gray>Outils admin - Find The Poulet");
        inv.setItem(10, Items.build(Material.NETHER_STAR, "<aqua><bold>Créer une arène",
                "<gray>Nom → bâton (zone) → réglages", "<gray>→ faux (enclos) → valider"));
        inv.setItem(11, Items.build(Material.BLAZE_ROD, "<gold><bold>Outil zone lobby / spawn",
                "<gray>Sélectionne 2 coins d'une zone protégée", "<gray>(pas de monstres, invincible...)",
                "<yellow>Clic : <white>recevoir l'outil"));
        inv.setItem(12, Items.build(Material.SHIELD, "<gold><bold>Zones protégées",
                "<gray>" + plugin().zones().all().size() + " zone(s)", "<yellow>Clic : <white>gérer"));
        inv.setItem(13, Items.build(Material.GOLD_BLOCK, "<gold><bold>Hologrammes de classement",
                "<gray>Victoires, poulets attrapés...", "<gray>" + plugin().leaderboards().all().size() + " placé(s)",
                "<yellow>Clic : <white>gérer"));
        inv.setItem(14, Items.build(Material.RED_BED, "<yellow><bold>Spawn du serveur ici",
                "<gray>Retour des joueurs en fin de partie", "<gray>(et à la connexion, voir config)",
                "<yellow>Clic : <white>définir à ta position"));
        inv.setItem(15, Items.build(Material.OAK_SIGN, "<yellow><bold>Lobby d'attente ici",
                "<gray>Où les joueurs attendent avant une partie", "<gray>(sinon : devant l'enclos)",
                "<yellow>Clic : <white>définir à ta position"));
        inv.setItem(16, Items.build(Material.COMPARATOR, "<gray><bold>Recharger la config"));
        inv.setItem(22, Items.build(Material.ARROW, "<gray>Retour"));
        fill(inv, 0, 26);
        p.openInventory(inv);
    }

    private static void handleAdmin(Player p, int slot) {
        FindThePoulet plugin = plugin();
        if (!p.hasPermission(ADMIN_PERM)) { p.closeInventory(); return; }
        switch (slot) {
            case 10 -> plugin.setup().start(p);
            case 11 -> {
                p.closeInventory();
                Items.give(p, Items.lobbyTool());
                Msg.send(p, "<gold>Outil zone lobby : <white>clic gauche <gray>= coin 1, <white>clic droit <gray>= coin 2, "
                        + "<white>clic droit dans l'air <gray>= menu pour créer la zone.");
            }
            case 12 -> openZones(p);
            case 13 -> openHolograms(p);
            case 14, 15 -> {
                String path = slot == 14 ? "main-spawn" : "lobby";
                fr.simon.findthepoulet.util.Locs.save(plugin.getConfig(), path, p.getLocation());
                plugin.saveConfig();
                if (slot == 14) p.getWorld().setSpawnLocation(p.getLocation());
                Msg.send(p, slot == 14 ? "<green>Spawn du serveur défini ici." : "<green>Lobby d'attente défini ici.");
                Msg.sound(p, "block.note_block.pling", 1.5f);
            }
            case 16 -> {
                plugin.reload();
                Msg.send(p, "<green>Config rechargée.");
            }
            case 22 -> openMain(p);
            default -> { }
        }
    }

    // ================================================================ hologrammes de classement

    public static void openHolograms(Player p) {
        FindThePoulet plugin = plugin();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.HOLOGRAMS, null);
        Inventory inv = create(holder, 45, "<dark_gray>Hologrammes de classement");
        int[] slots = {10, 11, 12, 14, 15, 16};
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length && i < slots.length; i++) {
            inv.setItem(slots[i], Items.build(stats[i].icon(), "<gold><bold>" + stats[i].label(),
                    "<gray>Place un classement flottant (top 10)", "<gray>au-dessus de ta position",
                    "<yellow>Clic : <white>placer ici"));
            holder.arenaSlots().put(slots[i], "stat:" + stats[i].key());
        }
        int slot = 27;
        for (fr.simon.findthepoulet.stats.LeaderboardManager.Board b : plugin.leaderboards().all()) {
            if (slot > 35) break;
            org.bukkit.Location l = b.location();
            inv.setItem(slot, Items.build(Material.PAPER, "<yellow>" + b.stat().label(),
                    "<gray>" + (l.getWorld() != null ? l.getWorld().getName() : "?") + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ(),
                    "<yellow>Clic gauche : <white>s'y téléporter",
                    "<red>Shift + clic : <white>supprimer"));
            holder.arenaSlots().put(slot, "board:" + b.id());
            slot++;
        }
        inv.setItem(36, Items.build(Material.ARROW, "<gray>Retour"));
        fill(inv, 0, 44);
        p.openInventory(inv);
    }

    private static void handleHolograms(Player p, MenuHolder holder, int slot, ClickType click) {
        FindThePoulet plugin = plugin();
        if (!p.hasPermission(ADMIN_PERM)) { p.closeInventory(); return; }
        if (slot == 36) { openAdmin(p); return; }
        String v = holder.arenaSlots().get(slot);
        if (v == null) return;
        if (v.startsWith("stat:")) {
            Stat st = Stat.parse(v.substring(5));
            plugin.leaderboards().create(st, p.getLocation().add(0, 2.2, 0));
            p.closeInventory();
            Msg.send(p, "<green>Classement <gold>" + st.label() + "</gold> placé ici ! <gray>(mis à jour toutes les 20 s)");
            Msg.sound(p, "block.amethyst_block.chime", 1f);
            return;
        }
        var b = plugin.leaderboards().get(v.substring(6));
        if (b == null) { openHolograms(p); return; }
        if (click.isShiftClick()) {
            plugin.leaderboards().remove(b.id());
            Msg.send(p, "<green>Classement supprimé.");
            openHolograms(p);
        } else {
            p.closeInventory();
            p.teleport(b.location().clone().add(0, -2.2, 2));
        }
    }

    // ================================================================ zones protégées

    public static void openZones(Player p) {
        FindThePoulet plugin = plugin();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.ZONES, null);
        Inventory inv = create(holder, 45, "<dark_gray>Zones protégées (lobby / spawn)");
        org.bukkit.Location[] sel = plugin.zones().selection(p.getUniqueId());
        var region = plugin.zones().selectedRegion(p.getUniqueId());
        inv.setItem(4, Items.build(region != null ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                "<green><bold>Créer une zone avec ta sélection",
                "<gray>Coin 1 : " + (sel[0] == null ? "<red>non défini" : "<white>" + sel[0].getBlockX() + ", " + sel[0].getBlockZ()),
                "<gray>Coin 2 : " + (sel[1] == null ? "<red>non défini" : "<white>" + sel[1].getBlockX() + ", " + sel[1].getBlockZ()),
                region != null ? "<gray>Taille : <white>" + (region.maxX() - region.minX() + 1) + " × " + (region.maxZ() - region.minZ() + 1)
                        : "<gray>Utilise l'outil (bâton de blaze) pour choisir 2 coins",
                "<yellow>Clic : <white>créer"));
        int slot = 18;
        for (fr.simon.findthepoulet.zone.ProtectedZone z : plugin.zones().all()) {
            if (slot > 35) break;
            var r = z.region();
            inv.setItem(slot, Items.build(Material.SHIELD, "<gold><bold>" + z.name(),
                    "<gray>" + r.world() + " : " + r.minX() + ", " + r.minZ() + " → " + r.maxX() + ", " + r.maxZ(),
                    "<yellow>Clic : <white>réglages"));
            holder.arenaSlots().put(slot, z.name());
            slot++;
        }
        inv.setItem(40, Items.build(Material.BLAZE_ROD, "<gold>Recevoir l'outil de sélection"));
        inv.setItem(36, Items.build(Material.ARROW, "<gray>Retour"));
        fill(inv, 0, 44);
        p.openInventory(inv);
    }

    private static void handleZones(Player p, MenuHolder holder, int slot) {
        FindThePoulet plugin = plugin();
        if (!p.hasPermission(ADMIN_PERM)) { p.closeInventory(); return; }
        switch (slot) {
            case 4 -> {
                var region = plugin.zones().selectedRegion(p.getUniqueId());
                if (region == null) { Msg.send(p, "<red>Sélectionne d'abord 2 coins avec l'outil (clic gauche / clic droit)."); return; }
                Arena a = plugin.arenas().overlapping(region);
                if (a != null) { Msg.send(p, "<red>Cette zone chevauche l'arène <yellow>" + a.name() + "</yellow>."); return; }
                var z = plugin.zones().create(plugin.zones().nextName(), region);
                plugin.zones().clearSelection(p.getUniqueId());
                Msg.send(p, "<green>Zone protégée <gold>" + z.name() + "</gold> créée ! <gray>Tout est activé par défaut, règle-la ici.");
                Msg.sound(p, "ui.toast.challenge_complete", 1f);
                openZone(p, z);
            }
            case 40 -> {
                p.closeInventory();
                Items.give(p, Items.lobbyTool());
            }
            case 36 -> openAdmin(p);
            default -> {
                var z = plugin.zones().get(holder.arenaSlots().get(slot));
                if (z != null) openZone(p, z);
            }
        }
    }

    public static void openZone(Player p, fr.simon.findthepoulet.zone.ProtectedZone z) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.ZONE, z.name());
        Inventory inv = create(holder, 36, "<dark_gray>Zone : " + z.name());
        var flags = fr.simon.findthepoulet.zone.ProtectedZone.Flag.values();
        Material[] icons = {Material.ZOMBIE_HEAD, Material.TOTEM_OF_UNDYING, Material.COOKED_BEEF,
                Material.IRON_SWORD, Material.BRICKS, Material.TNT};
        for (int i = 0; i < flags.length; i++) {
            var f = flags[i];
            List<String> lore = new ArrayList<>();
            for (String line : f.description().split("\n")) lore.add("<gray>" + line);
            lore.add("<yellow>Clic : <white>activer / désactiver");
            inv.setItem(10 + i, Items.build(z.has(f) ? icons[i] : Material.GRAY_DYE,
                    (z.has(f) ? "<green>✔ " : "<red>✘ ") + "<white><bold>" + f.label(), lore.toArray(String[]::new)));
        }
        inv.setItem(16, Items.build(Material.ENDER_EYE, "<aqua><bold>Afficher la zone", "<gray>Contour en particules pendant 15 s"));
        inv.setItem(20, Items.build(Material.RED_BED, "<yellow><bold>Spawn du serveur ici",
                "<gray>Définit le spawn à ta position", "<gray>(retour de fin de partie, connexion)"));
        inv.setItem(22, Items.build(Material.BLAZE_ROD, "<yellow><bold>Redéfinir avec ta sélection",
                "<gray>Remplace les limites par tes 2 coins"));
        inv.setItem(24, Items.build(Material.TNT, "<dark_red><bold>Supprimer la zone", "<red>Shift + clic <gray>pour confirmer"));
        inv.setItem(27, Items.build(Material.ARROW, "<gray>Retour"));
        fill(inv, 0, 35);
        p.openInventory(inv);
    }

    private static void handleZone(Player p, MenuHolder holder, int slot, ClickType click) {
        FindThePoulet plugin = plugin();
        if (!p.hasPermission(ADMIN_PERM)) { p.closeInventory(); return; }
        var z = plugin.zones().get(holder.arenaName());
        if (z == null) { openZones(p); return; }
        var flags = fr.simon.findthepoulet.zone.ProtectedZone.Flag.values();
        if (slot >= 10 && slot < 10 + flags.length) {
            z.toggle(flags[slot - 10]);
            plugin.zones().save();
            Msg.sound(p, "ui.button.click", 1f);
            openZone(p, z);
            return;
        }
        switch (slot) {
            case 16 -> {
                plugin.zones().show(p, z);
                p.closeInventory();
            }
            case 20 -> {
                if (!z.region().contains(p.getLocation())) Msg.send(p, "<gold>Attention : tu n'es pas dans la zone " + z.name() + ".");
                fr.simon.findthepoulet.util.Locs.save(plugin.getConfig(), "main-spawn", p.getLocation());
                plugin.saveConfig();
                p.getWorld().setSpawnLocation(p.getLocation());
                Msg.send(p, "<green>Spawn du serveur défini ici.");
                Msg.sound(p, "block.note_block.pling", 1.5f);
            }
            case 22 -> {
                var region = plugin.zones().selectedRegion(p.getUniqueId());
                if (region == null) { Msg.send(p, "<red>Sélectionne d'abord 2 coins avec l'outil."); return; }
                Arena a = plugin.arenas().overlapping(region);
                if (a != null) { Msg.send(p, "<red>Cette zone chevauche l'arène <yellow>" + a.name() + "</yellow>."); return; }
                z.setRegion(region);
                plugin.zones().save();
                plugin.zones().clearSelection(p.getUniqueId());
                Msg.send(p, "<green>Limites de la zone mises à jour.");
                openZone(p, z);
            }
            case 24 -> {
                if (!click.isShiftClick()) { Msg.send(p, "<red>Fais <yellow>Shift + clic</yellow> pour confirmer."); return; }
                plugin.zones().remove(z.name());
                Msg.send(p, "<green>Zone supprimée.");
                openZones(p);
            }
            case 27 -> openZones(p);
            default -> { }
        }
    }

    // ================================================================ clics

    public static void handle(Player p, MenuHolder holder, int slot, ClickType click) {
        switch (holder.type()) {
            case MAIN -> handleMain(p, holder, slot, click);
            case ARENA_ADMIN -> handleArenaAdmin(p, holder, slot, click);
            case SETUP -> handleSetup(p, slot);
            case STATS -> {
                if (slot == 27) openMain(p);
            }
            case ADMIN -> handleAdmin(p, slot);
            case HOLOGRAMS -> handleHolograms(p, holder, slot, click);
            case ZONES -> handleZones(p, holder, slot);
            case ZONE -> handleZone(p, holder, slot, click);
        }
    }

    private static void handleMain(Player p, MenuHolder holder, int slot, ClickType click) {
        FindThePoulet plugin = plugin();
        Game current = plugin.games().of(p);
        String arenaName = holder.arenaSlots().get(slot);
        if (arenaName != null) {
            Arena a = plugin.arenas().get(arenaName);
            if (a == null) { openMain(p); return; }
            if (click.isRightClick() && p.hasPermission(ADMIN_PERM)) {
                openArenaAdmin(p, a);
                return;
            }
            Game g = plugin.games().get(a);
            if (current == g) { Msg.send(p, "<gray>Tu es déjà dans cette partie."); return; }
            if (current != null) { Msg.send(p, "<red>Quitte d'abord ta partie actuelle (<yellow>" + current.arena().name() + "</yellow>)."); return; }
            if (plugin.setup().get(p.getUniqueId()) != null) { Msg.send(p, "<red>Termine ou annule d'abord ta création d'arène."); return; }
            p.closeInventory();
            g.join(p);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (p.hasPermission(ADMIN_PERM)) openAdmin(p);
            }
            case 47 -> {
                if (current != null) {
                    current.toggleReady(p);
                    openMain(p);
                }
            }
            case 49 -> {
                p.closeInventory();
                Msg.send(p, "<yellow>Comment jouer : <gray>trouve <gold>le poulet</gold> (surface ou cavernes), "
                        + "<white>clic droit</white> pour l'attraper et ramène-le dans <gold>l'enclos</gold> du point de départ. "
                        + "Frappe le porteur pour lui faire lâcher le poulet. <red>Si le poulet meurt, tout le monde perd !");
            }
            case 53 -> openStats(p);
            case 46 -> {
                if (current != null && p.hasPermission(ADMIN_PERM) && current.forceStart()) p.closeInventory();
            }
            case 51 -> {
                if (current != null) {
                    p.closeInventory();
                    current.leave(p);
                }
            }
            default -> { }
        }
    }

    private static void handleArenaAdmin(Player p, MenuHolder holder, int slot, ClickType click) {
        FindThePoulet plugin = plugin();
        if (!p.hasPermission(ADMIN_PERM)) { p.closeInventory(); return; }
        Arena a = plugin.arenas().get(holder.arenaName());
        if (a == null) { openMain(p); return; }
        Game g = plugin.games().existing(a.name());
        boolean locked = g != null && g.state() != Game.State.WAITING;

        switch (slot) {
            case 10, 11, 12, 13, 14, 16 -> {
                if (locked) { Msg.send(p, "<red>Impossible de modifier l'arène pendant une partie."); return; }
                switch (slot) {
                    case 10 -> a.setMode(a.mode().next());
                    case 11 -> a.setPvp(!a.isPvp());
                    case 12 -> a.setFormat(a.format().next());
                    case 13 -> a.setFox(!a.isFox());
                    case 14 -> a.setKit(!a.isKit());
                    default -> {
                        a.setEnabled(!a.isEnabled());
                        if (!a.isEnabled() && g != null) plugin.games().removeArena(a); // renvoie les joueurs du lobby
                    }
                }
                plugin.arenas().save();
                Msg.sound(p, "ui.button.click", 1f);
                openArenaAdmin(p, a);
            }
            case 15 -> {
                if (click.isRightClick()) {
                    ChickenSkin s = plugin.skins().pick(a.skin());
                    if (s == null) { Msg.send(p, "<gray>Aucun déguisement à montrer."); return; }
                    p.closeInventory();
                    previewInFront(p, s);
                    return;
                }
                if (locked) { Msg.send(p, "<red>Impossible de modifier l'arène pendant une partie."); return; }
                a.setSkin(plugin.skins().nextChoice(a.skin()));
                plugin.arenas().save();
                Msg.sound(p, "ui.button.click", 1f);
                openArenaAdmin(p, a);
            }
            case 21 -> {
                if (g == null || !g.forceStart()) {
                    Msg.send(p, "<red>Personne dans le lobby de cette arène (ou partie déjà en cours). <gray>Rejoins-la d'abord.");
                    return;
                }
                p.closeInventory();
                Msg.send(p, "<green>Lancement forcé de <gold>" + a.name() + "</gold>.");
            }
            case 22 -> {
                p.closeInventory();
                if (a.world() == null) { Msg.send(p, "<red>Le monde de l'arène n'est pas chargé."); return; }
                p.teleport(a.spawnPoint());
            }
            case 23 -> {
                if (g == null || (g.state() == Game.State.WAITING && g.size() == 0)) {
                    Msg.send(p, "<gray>Aucune partie en cours sur cette arène.");
                    return;
                }
                g.forceStop();
                Msg.send(p, "<green>Partie de <gold>" + a.name() + "</gold> arrêtée.");
                openArenaAdmin(p, a);
            }
            case 24 -> {
                if (!click.isShiftClick()) { Msg.send(p, "<red>Fais <yellow>Shift + clic</yellow> pour confirmer la suppression."); return; }
                plugin.games().removeArena(a);
                plugin.arenas().remove(a.name());
                Msg.send(p, "<green>Arène <gold>" + a.name() + "</gold> supprimée.");
                openMain(p);
            }
            case 27 -> openMain(p);
            default -> { }
        }
    }

    // ================================================================ classements

    public static void openStats(Player p) {
        FindThePoulet plugin = plugin();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.STATS, null);
        Inventory inv = create(holder, 36, "<dark_gray>Classements - Find The Poulet");
        int[] slots = {10, 11, 12, 14, 15, 16};
        Stat[] stats = Stat.values();
        String[] medal = {"<gold>", "<white>", "<#cd7f32>"};
        for (int i = 0; i < stats.length && i < slots.length; i++) {
            Stat st = stats[i];
            List<String> lore = new ArrayList<>();
            List<java.util.Map.Entry<String, Integer>> top = plugin.stats().top(st, 10);
            if (top.isEmpty()) lore.add("<gray>Personne pour l'instant...");
            for (int k = 0; k < top.size(); k++) {
                String c = k < 3 ? medal[k] : "<yellow>";
                lore.add(c + "#" + (k + 1) + " <white>" + top.get(k).getKey() + " <dark_gray>- " + c + top.get(k).getValue());
            }
            lore.add("");
            lore.add("<gray>Toi : <white>" + plugin.stats().get(p.getUniqueId(), st));
            inv.setItem(slots[i], Items.build(st.icon(), "<gold><bold>" + st.label(), lore.toArray(String[]::new)));
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, m -> m.setOwningPlayer(p));
        List<net.kyori.adventure.text.Component> mine = new ArrayList<>();
        for (Stat st : Stat.values()) mine.add(Msg.item("<gray>" + st.label() + " : <white>" + plugin.stats().get(p.getUniqueId(), st)));
        head.editMeta(m -> {
            m.displayName(Msg.item("<yellow><bold>Tes statistiques"));
            m.lore(mine);
        });
        inv.setItem(22, head);
        inv.setItem(27, Items.build(Material.ARROW, "<gray>Retour"));
        fill(inv, 0, 35);
        p.openInventory(inv);
    }

    /** Fait apparaître un poulet déguisé 2 blocs devant le joueur, pendant 20 secondes. */
    public static void previewInFront(Player p, ChickenSkin s) {
        org.bukkit.Location l = p.getLocation();
        org.bukkit.util.Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new org.bukkit.util.Vector(0, 0, 1);
        org.bukkit.Location at = l.clone().add(dir.normalize().multiply(2));
        at.setPitch(0f);
        plugin().skins().preview(at, s);
        Msg.send(p, "<gray>Aperçu de " + s.name() + " <gray>pendant 20 s. <dark_gray>(réglages : chicken-skin dans config.yml, puis /poulet reload)");
    }

    private static void handleSetup(Player p, int slot) {
        FindThePoulet plugin = plugin();
        SetupSession s = plugin.setup().get(p.getUniqueId());
        if (s == null) { p.closeInventory(); return; }
        switch (slot) {
            case 10 -> { plugin.setup().cycleSize(p); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 11 -> { s.cycleMode(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 12 -> { s.togglePvp(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 13 -> { s.cycleFormat(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 14 -> { s.toggleFox(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 15 -> { s.toggleKit(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 16 -> { s.setSkin(plugin.skins().nextChoice(s.skin())); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 20 -> { Items.give(p, Items.zoneTool()); p.closeInventory(); }
            case 22 -> {
                if (s.region() == null) { Msg.send(p, "<red>Définis d'abord la zone avec le bâton."); return; }
                Items.give(p, Items.enclosTool());
                p.closeInventory();
            }
            case 24 -> plugin.setup().validate(p);
            case 31 -> plugin.setup().cancel(p, true);
            default -> { }
        }
    }
}
