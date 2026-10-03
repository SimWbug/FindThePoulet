package fr.simon.findthepoulet.listener;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.gui.MenuHolder;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {

    private final FindThePoulet plugin;

    public MenuListener(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize() || Items.isEmpty(e.getCurrentItem())) return;
        ClickType click = e.getClick();
        // On agit au tick suivant : ouvrir/fermer un inventaire pendant un clic est risqué
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) Menus.handle(p, holder, raw, click);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof MenuHolder) e.setCancelled(true);
    }
}
