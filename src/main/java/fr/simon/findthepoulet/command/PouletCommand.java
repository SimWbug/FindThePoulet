package fr.simon.findthepoulet.command;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.Arena;
import fr.simon.findthepoulet.game.Game;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.skin.ChickenSkin;
import fr.simon.findthepoulet.stats.LeaderboardManager;
import fr.simon.findthepoulet.stats.Stat;
import java.util.UUID;
import fr.simon.findthepoulet.util.Locs;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PouletCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("pret", "quitter", "rejoindre", "liste", "stats", "classement");
    private static final List<String> ADMIN_SUBS = List.of("creer", "annuler", "setlobby", "setspawn", "stop", "supprimer", "skin", "reload", "flute", "boussole", "plume", "leaderboard", "admin", "lobby", "forcer", "tppoulet");

    private final FindThePoulet plugin;

    public PouletCommand(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        if (args.length == 0) {
            if (p == null) return help(sender);
            Menus.openMain(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(Menus.ADMIN_PERM);

        switch (sub) {
            case "pret", "prêt", "ready" -> {
                if (p == null) return playerOnly(sender);
                Game g = plugin.games().of(p);
                if (g == null) Msg.send(p, "<red>Tu n'es dans aucune partie. <gray>Utilise la tête de poulet !");
                else g.toggleReady(p);
            }
            case "quitter", "leave" -> {
                if (p == null) return playerOnly(sender);
                Game g = plugin.games().of(p);
                if (g != null) g.leave(p);
                else if (plugin.setup().get(p.getUniqueId()) != null) plugin.setup().cancel(p, true);
                else Msg.send(p, "<red>Tu n'es dans aucune partie.");
            }
            case "rejoindre", "join" -> {
                if (p == null) return playerOnly(sender);
                if (args.length < 2) { Msg.send(p, "<red>Usage : /poulet rejoindre <arène>"); return true; }
                Arena a = plugin.arenas().get(args[1]);
                if (a == null) { Msg.send(p, "<red>Arène inconnue."); return true; }
                Game current = plugin.games().of(p);
                if (current != null) { Msg.send(p, "<red>Tu es déjà dans une partie (<yellow>" + current.arena().name() + "</yellow>)."); return true; }
                if (plugin.setup().get(p.getUniqueId()) != null) { Msg.send(p, "<red>Termine d'abord ta création d'arène."); return true; }
                plugin.games().get(a).join(p);
            }
            case "liste", "list" -> {
                if (plugin.arenas().all().isEmpty()) { Msg.send(sender, "<gray>Aucune arène."); return true; }
                Msg.send(sender, "<yellow>Arènes :");
                for (Arena a : plugin.arenas().all()) {
                    Game g = plugin.games().existing(a.name());
                    String state = !a.isEnabled() ? "<gray>désactivée"
                            : g == null ? "<green>libre" : g.state() == Game.State.WAITING ? "<green>attente" : "<red>" + g.state().name().toLowerCase(Locale.ROOT);
                    sender.sendMessage(Msg.mm(" <dark_gray>- <gold>" + a.name() + " <dark_gray>| " + state
                            + " <dark_gray>| <white>" + (g == null ? 0 : g.size()) + " joueur(s) <dark_gray>| <white>"
                            + a.mode().label() + " <dark_gray>| <white>PvP " + a.pvpMode().hud()));
                }
            }
            case "creer", "créer", "create" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                plugin.setup().start(p);
                if (args.length >= 2) plugin.setup().handleName(p, args[1]);
            }
            case "annuler", "cancel" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                if (plugin.setup().get(p.getUniqueId()) == null) Msg.send(p, "<gray>Aucune création en cours.");
                else plugin.setup().cancel(p, true);
            }
            case "setlobby", "setspawn" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                String path = sub.equals("setlobby") ? "lobby" : "main-spawn";
                Locs.save(plugin.getConfig(), path, p.getLocation());
                plugin.saveConfig();
                Msg.send(p, sub.equals("setlobby")
                        ? "<green>Lobby d'attente défini ici."
                        : "<green>Spawn de retour (fin de partie) défini ici.");
            }
            case "stop" -> {
                if (!admin) return noPerm(sender);
                Game g = args.length >= 2 ? plugin.games().existing(args[1]) : null;
                if (g == null) { Msg.send(sender, "<red>Aucune partie sur cette arène. <gray>Usage : /poulet stop <arène>"); return true; }
                g.forceStop();
                Msg.send(sender, "<green>Partie arrêtée.");
            }
            case "supprimer", "delete" -> {
                if (!admin) return noPerm(sender);
                Arena a = args.length >= 2 ? plugin.arenas().get(args[1]) : null;
                if (a == null) { Msg.send(sender, "<red>Arène inconnue. <gray>Usage : /poulet supprimer <arène>"); return true; }
                plugin.games().removeArena(a);
                plugin.arenas().remove(a.name());
                Msg.send(sender, "<green>Arène <gold>" + a.name() + "</gold> supprimée.");
            }
            case "skin" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                if (plugin.skins().keys().isEmpty()) { Msg.send(p, "<red>Aucun skin dans la config (chicken-skin.skins)."); return true; }
                if (args.length < 2) {
                    Msg.send(p, "<yellow>Skins : <white>" + String.join(", ", plugin.skins().keys())
                            + " <gray>- /poulet skin <nom> pour un aperçu");
                    return true;
                }
                ChickenSkin s = plugin.skins().get(args[1]);
                if (s == null) { Msg.send(p, "<red>Skin inconnu."); return true; }
                Menus.previewInFront(p, s);
            }
            case "flute" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                p.getInventory().addItem(fr.simon.findthepoulet.game.Flute.create(plugin.getConfig()));
                Msg.send(p, "<green>Flûte à poulet ajoutée à ton inventaire (elle ne marche qu'en partie).");
            }
            case "boussole", "plume" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                p.getInventory().addItem(sub.equals("plume")
                        ? fr.simon.findthepoulet.game.Gadgets.feather(plugin.getConfig())
                        : fr.simon.findthepoulet.game.Gadgets.compass(plugin.getConfig()));
                Msg.send(p, "<green>Objet ajouté à ton inventaire (il ne marche qu'en partie).");
            }
            case "stats" -> {
                UUID target;
                String name;
                if (args.length >= 2) {
                    target = plugin.stats().find(args[1]);
                    name = args[1];
                } else if (p != null) {
                    target = p.getUniqueId();
                    name = p.getName();
                } else {
                    Msg.send(sender, "<red>Usage : /poulet stats <joueur>");
                    return true;
                }
                if (target == null) { Msg.send(sender, "<red>Aucune statistique pour " + name + "."); return true; }
                Msg.send(sender, "<yellow>Statistiques de <gold>" + plugin.stats().name(target) + "</gold> :");
                for (Stat st : Stat.values()) {
                    sender.sendMessage(Msg.mm(" <dark_gray>- <gray>" + st.label() + " : <white>" + plugin.stats().get(target, st)));
                }
            }
            case "classement" -> {
                if (p != null) Menus.openStats(p);
                else Msg.send(sender, "<red>Commande réservée aux joueurs.");
            }
            case "leaderboard", "lb" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                String what = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (what.equals("supprimer") || what.equals("remove")) {
                    LeaderboardManager.Board b = plugin.leaderboards().removeNear(p.getLocation(), 6);
                    Msg.send(p, b == null ? "<red>Aucun classement à moins de 6 blocs."
                            : "<green>Classement <gold>" + b.stat().label() + "</gold> supprimé.");
                    return true;
                }
                if (what.equals("liste") || what.equals("list")) {
                    if (plugin.leaderboards().all().isEmpty()) { Msg.send(p, "<gray>Aucun classement placé."); return true; }
                    for (LeaderboardManager.Board b : plugin.leaderboards().all()) {
                        p.sendMessage(Msg.mm(" <dark_gray>- <gold>" + b.stat().label() + " <gray>en " + b.location().getWorld().getName()
                                + " " + b.location().getBlockX() + ", " + b.location().getBlockY() + ", " + b.location().getBlockZ()));
                    }
                    return true;
                }
                Stat stat = Stat.parse(what);
                if (stat == null) {
                    Msg.send(p, "<red>Usage : /poulet leaderboard <victoires|parties|attrapes|livraisons|tues|eliminations> "
                            + "<gray>(ou supprimer / liste)");
                    return true;
                }
                plugin.leaderboards().create(stat, p.getLocation().add(0, 2.2, 0));
                Msg.send(p, "<green>Classement <gold>" + stat.label() + "</gold> placé ici ! <gray>(mis à jour toutes les 20 s)");
            }
            case "forcer", "forcestart", "start" -> {
                if (!admin) return noPerm(sender);
                Game g;
                if (args.length >= 2) {
                    g = plugin.games().existing(args[1]);
                } else {
                    g = p != null ? plugin.games().of(p) : null;
                }
                if (g == null) {
                    Msg.send(sender, "<red>Aucun joueur dans cette arène. <gray>Usage : /poulet forcer <arène> (ou rejoins-la puis /poulet forcer)");
                    return true;
                }
                if (!g.forceStart()) {
                    Msg.send(sender, "<red>Impossible : le lobby est vide ou la partie est déjà en cours.");
                    return true;
                }
                Msg.send(sender, "<green>Lancement forcé de <gold>" + g.arena().name() + "</gold> (" + g.size() + " joueur(s)).");
            }
            case "tppoulet", "tpchicken" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                Game g = args.length >= 2 ? plugin.games().existing(args[1]) : plugin.games().of(p);
                if (g == null) {
                    // Pas précisé et pas en partie : on prend la seule partie en cours s'il n'y en a qu'une
                    List<Game> running = new ArrayList<>();
                    for (Arena a : plugin.arenas().all()) {
                        Game ga = plugin.games().existing(a.name());
                        if (ga != null && ga.isRunning()) running.add(ga);
                    }
                    if (running.size() == 1) g = running.get(0);
                }
                if (g == null || !g.isRunning()) {
                    Msg.send(p, "<red>Aucune partie en cours. <gray>Usage : /poulet tppoulet [arène]");
                    return true;
                }
                org.bukkit.Location l = g.chickenLocation();
                if (l == null) { Msg.send(p, "<red>Le poulet n'est pas là pour l'instant (entre deux manches ?)."); return true; }
                p.teleport(l.clone().add(0, 1, 0));
                Msg.send(p, "<green>Téléporté au poulet de <gold>" + g.arena().name() + "</gold>."
                        + (plugin.games().of(p) == null ? " <gray>(astuce : passe en spectateur pour ne pas gêner)" : ""));
            }
            case "admin" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                Menus.openAdmin(p);
            }
            case "lobby" -> {
                if (!admin) return noPerm(sender);
                if (p == null) return playerOnly(sender);
                fr.simon.findthepoulet.util.Items.give(p, fr.simon.findthepoulet.util.Items.lobbyTool());
                Msg.send(p, "<gold>Outil zone lobby : <white>clic gauche <gray>= coin 1, <white>clic droit <gray>= coin 2, "
                        + "<white>clic droit dans l'air <gray>= menu.");
            }
            case "reload" -> {
                if (!admin) return noPerm(sender);
                plugin.reload();
                Msg.send(sender, "<green>Config rechargée (" + plugin.skins().keys().size() + " déguisement(s)).");
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean help(CommandSender s) {
        Msg.send(s, "<gold>Find The Poulet");
        s.sendMessage(Msg.mm("<yellow>/poulet <gray>- ouvrir le menu"));
        s.sendMessage(Msg.mm("<yellow>/poulet pret <gray>- se mettre prêt"));
        s.sendMessage(Msg.mm("<yellow>/poulet quitter <gray>- quitter la partie"));
        s.sendMessage(Msg.mm("<yellow>/poulet rejoindre <arène> <gray>- rejoindre une arène"));
        s.sendMessage(Msg.mm("<yellow>/poulet liste <gray>- liste des arènes"));
        s.sendMessage(Msg.mm("<yellow>/poulet stats [joueur] <gray>- statistiques"));
        s.sendMessage(Msg.mm("<yellow>/poulet classement <gray>- classements"));
        if (s.hasPermission(Menus.ADMIN_PERM)) {
            s.sendMessage(Msg.mm("<aqua>/poulet admin <gray>- menu des outils admin"));
            s.sendMessage(Msg.mm("<aqua>/poulet lobby <gray>- outil de zone lobby / spawn protégée"));
            s.sendMessage(Msg.mm("<aqua>/poulet creer [nom] <gray>- créer une arène"));
            s.sendMessage(Msg.mm("<aqua>/poulet annuler <gray>- annuler la création"));
            s.sendMessage(Msg.mm("<aqua>/poulet setlobby <gray>- lobby d'attente"));
            s.sendMessage(Msg.mm("<aqua>/poulet setspawn <gray>- spawn de fin de partie"));
            s.sendMessage(Msg.mm("<aqua>/poulet forcer [arène] <gray>- lancer la partie même seul / sans les prêts"));
            s.sendMessage(Msg.mm("<aqua>/poulet tppoulet [arène] <gray>- se téléporter au poulet"));
            s.sendMessage(Msg.mm("<aqua>/poulet stop <arène> <gray>- arrêter une partie"));
            s.sendMessage(Msg.mm("<aqua>/poulet supprimer <arène> <gray>- supprimer une arène"));
            s.sendMessage(Msg.mm("<aqua>/poulet skin [nom] <gray>- aperçu d'un déguisement"));
            s.sendMessage(Msg.mm("<aqua>/poulet reload <gray>- recharger la config"));
            s.sendMessage(Msg.mm("<aqua>/poulet flute | boussole | plume <gray>- se donner un objet"));
            s.sendMessage(Msg.mm("<aqua>/poulet leaderboard <stat> <gray>- placer un hologramme de classement"));
            s.sendMessage(Msg.mm("<aqua>/poulet leaderboard supprimer | liste"));
        }
        return true;
    }

    private boolean playerOnly(CommandSender s) {
        Msg.send(s, "<red>Commande réservée aux joueurs.");
        return true;
    }

    private boolean noPerm(CommandSender s) {
        Msg.send(s, "<red>Tu n'as pas la permission.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (sender.hasPermission(Menus.ADMIN_PERM)) subs.addAll(ADMIN_SUBS);
            for (String s : subs) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("leaderboard") || sub.equals("lb")) {
                List<String> opts = new ArrayList<>(List.of("supprimer", "liste"));
                for (Stat st : Stat.values()) opts.add(st.key());
                for (String o : opts) if (o.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(o);
            } else if (sub.equals("skin")) {
                for (String k : plugin.skins().keys()) if (k.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k);
            } else if (sub.equals("rejoindre") || sub.equals("stop") || sub.equals("supprimer") || sub.equals("forcer") || sub.equals("tppoulet")) {
                for (Arena a : plugin.arenas().all()) {
                    if (a.name().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(a.name());
                }
            }
        }
        return out;
    }
}
