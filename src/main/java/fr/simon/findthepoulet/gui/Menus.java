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
            inv.setItem(45, Items.build(Material.NETHER_STAR, "<aqua><bold>Créer une arène",
                    "<gray>Nom → bâton (zone) → réglages",
                    "<gray>→ faux (enclos) → valider"));
        }
        if (current != null) {
            boolean ready = current.isReady(p);
            inv.setItem(47, Items.build(ready ? Material.LIME_DYE : Material.GRAY_DYE,
                    ready ? "<green><bold>Tu es prêt !" : "<yellow><bold>Se mettre prêt",
                    ready ? "<gray>Clique pour ne plus être prêt" : "<gray>Clique quand tu es prêt à jouer"));
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
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SETUP, s.name());
        Inventory inv = create(holder, 27, "<dark_gray>Création : " + (s.name() == null ? "?" : s.name()));

        inv.setItem(10, Items.build(Material.WHITE_BANNER, "<yellow><bold>Équipes : <white>" + s.mode().label(),
                "<gray>Solo, Duo, Trio ou Quatuor", "<yellow>Clic : <white>changer"));
        inv.setItem(11, Items.build(s.pvp() ? Material.IRON_SWORD : Material.WOODEN_SWORD,
                "<yellow><bold>PvP : " + onOff(s.pvp()), "<yellow>Clic : <white>activer / désactiver"));
        inv.setItem(13, Items.build(Material.STICK, (s.region() != null ? "<green>✔ " : "<red>✘ ") + "<aqua><bold>Zone",
                s.region() != null ? "<gray>Zone définie (" + s.region().size() + "×" + s.region().size() + ")" : "<gray>Pas encore définie",
                "<yellow>Clic : <white>récupérer le bâton"));
        inv.setItem(14, Items.build(Material.IRON_HOE, (s.hasEnclosure() ? "<green>✔ " : "<red>✘ ") + "<yellow><bold>Enclos",
                s.hasEnclosure() ? "<gray>Enclos placé" : "<gray>Pas encore placé",
                "<yellow>Clic : <white>récupérer la faux"));
        boolean ok = s.region() != null && s.hasEnclosure();
        inv.setItem(16, Items.build(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "<green><bold>Valider l'arène",
                ok ? "<gray>L'arène deviendra jouable" : "<red>Zone et enclos requis"));
        inv.setItem(22, Items.build(Material.BARRIER, "<red><bold>Annuler la création"));
        fill(inv, 0, 26);
        p.openInventory(inv);
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
                if (p.hasPermission(ADMIN_PERM)) plugin.setup().start(p);
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
            case 10 -> { s.cycleMode(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 11 -> { s.togglePvp(); Msg.sound(p, "ui.button.click", 1f); openSetup(p, s); }
            case 13 -> { Items.give(p, Items.zoneTool()); p.closeInventory(); }
            case 14 -> {
                if (s.region() == null) { Msg.send(p, "<red>Définis d'abord la zone avec le bâton."); return; }
                Items.give(p, Items.enclosTool());
                p.closeInventory();
            }
            case 16 -> plugin.setup().validate(p);
            case 22 -> plugin.setup().cancel(p, true);
            default -> { }
        }
    }
}
