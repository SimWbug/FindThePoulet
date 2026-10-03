package fr.simon.findthepoulet.listener;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.game.Game;
import fr.simon.findthepoulet.game.PlayerSnapshot;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Tête de poulet dans la barre, lit "quitter", retour des joueurs. */
public final class HubListener implements Listener {

    private final FindThePoulet plugin;

    public HubListener(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    private static boolean isLobbyItem(ItemStack item) {
        String tag = Items.tagOf(item);
        return Items.MENU.equals(tag) || Items.LEAVE.equals(tag);
    }

    /** Objets du plugin qu'on ne peut ni jeter ni perdre (la flûte, elle, est un objet de jeu normal). */
    private static boolean isProtected(ItemStack item) {
        String tag = Items.tagOf(item);
        return tag != null && !Items.isGadget(tag);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Items.removeTagged(p, Items.LEAVE, Items.ZONE, Items.ENCLOS);
        PlayerSnapshot pending = p.isDead() ? null : plugin.games().takePending(p.getUniqueId());
        if (pending != null) {
            Players.reset(p, GameMode.ADVENTURE);
            pending.restore(p);
            p.teleport(plugin.mainSpawn());
        }
        plugin.giveMenuItem(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = plugin.games().of(p);
        if (g != null) g.leave(p);
        plugin.setup().cancel(p, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerSnapshot pending = plugin.games().takePending(p.getUniqueId());
        if (pending != null) e.setRespawnLocation(plugin.mainSpawn());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (pending != null) {
                Players.reset(p, GameMode.ADVENTURE);
                pending.restore(p);
            }
            plugin.giveMenuItem(p);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (!isLobbyItem(item)) return;
        e.setCancelled(true); // ne pas poser la tête / le lit
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (Items.MENU.equals(Items.tagOf(item))) {
            Menus.openMain(p);
        } else {
            Game g = plugin.games().of(p);
            if (g != null) g.leave(p);
            else Items.removeTagged(p, Items.LEAVE);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isProtected(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isProtected(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isProtected(e.getMainHandItem()) || isProtected(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack hotbar = e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton()) : null;
        if (isLobbyItem(e.getCurrentItem()) || isLobbyItem(e.getCursor()) || isLobbyItem(hotbar)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(HubListener::isProtected);
    }
}
