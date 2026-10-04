package fr.simon.findthepoulet.listener;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.setup.SetupSession;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SetupListener implements Listener {

    private final FindThePoulet plugin;

    public SetupListener(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    /** Le nom de l'arène est tapé dans le chat. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        SetupSession s = plugin.setup().get(p.getUniqueId());
        if (s == null || s.stage() != SetupSession.Stage.NAME) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.setup().handleName(p, text);
        });
    }

    /** En créatif, le clic gauche casse le bloc : on l'empêche quand on tient un outil de sélection. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreakWithTool(org.bukkit.event.block.BlockBreakEvent e) {
        String tag = Items.tagOf(e.getPlayer().getInventory().getItemInMainHand());
        if (Items.ZONE.equals(tag) || Items.LOBBY.equals(tag) || Items.ENCLOS.equals(tag)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTool(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String tag = Items.tagOf(e.getItem());
        if (!Items.ZONE.equals(tag) && !Items.ENCLOS.equals(tag)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        SetupSession s = plugin.setup().get(p.getUniqueId());
        if (s == null || !p.hasPermission(Menus.ADMIN_PERM)) {
            Items.removeTagged(p, Items.ZONE, Items.ENCLOS);
            Msg.send(p, "<gray>Aucune création d'arène en cours, l'outil a été retiré.");
            return;
        }
        Action action = e.getAction();
        boolean zone = Items.ZONE.equals(tag);
        if (zone && s.cornerMode() && e.getClickedBlock() != null
                && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {
            plugin.setup().onZoneCorner(p, e.getClickedBlock(), action == Action.LEFT_CLICK_BLOCK ? 0 : 1);
        } else if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if (zone) plugin.setup().onZoneClick(p, e.getClickedBlock());
            else plugin.setup().onEnclosClick(p, e.getClickedBlock());
        } else if (action == Action.RIGHT_CLICK_AIR) {
            if (s.stage() == SetupSession.Stage.NAME) Msg.send(p, "<red>Écris d'abord le nom de l'arène dans le chat.");
            else Menus.openSetup(p, s);
        }
    }
}
