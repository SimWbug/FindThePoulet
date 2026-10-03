package fr.simon.findthepoulet.listener;

import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.util.Msg;
import fr.simon.findthepoulet.zone.ProtectedZone.Flag;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Zones protégées (lobby / spawn) + outil de sélection des admins. */
public final class ZoneListener implements Listener {

    private final FindThePoulet plugin;

    public ZoneListener(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    private boolean in(Location l, Flag f) {
        return plugin.zones().is(l, f);
    }

    /** Les joueurs en partie ne sont pas concernés (l'arène a ses propres règles). */
    private boolean inGame(Player p) {
        return plugin.games().of(p) != null;
    }

    private static boolean isAdmin(Player p) {
        return p.hasPermission(Menus.ADMIN_PERM);
    }

    // ================================================================ outil de sélection

    @EventHandler(priority = EventPriority.LOW)
    public void onTool(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !Items.LOBBY.equals(Items.tagOf(e.getItem()))) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!isAdmin(p)) {
            Items.removeTagged(p, Items.LOBBY);
            return;
        }
        Block b = e.getClickedBlock();
        Action a = e.getAction();
        if (b != null && (a == Action.LEFT_CLICK_BLOCK || a == Action.RIGHT_CLICK_BLOCK)) {
            int idx = a == Action.LEFT_CLICK_BLOCK ? 0 : 1;
            Location[] sel = plugin.zones().selection(p.getUniqueId());
            sel[idx] = b.getLocation();
            Msg.send(p, "<green>Coin " + (idx + 1) + " : <white>" + b.getX() + ", " + b.getY() + ", " + b.getZ());
            var r = plugin.zones().selectedRegion(p.getUniqueId());
            if (r != null) {
                Msg.send(p, "<gray>Zone de <white>" + (r.maxX() - r.minX() + 1) + " × " + (r.maxZ() - r.minZ() + 1)
                        + "</white> blocs. <yellow>Clic droit dans l'air <gray>pour ouvrir le menu et la créer.");
            }
            Msg.sound(p, "block.note_block.pling", idx == 0 ? 1.2f : 1.6f);
        } else if (a == Action.RIGHT_CLICK_AIR) {
            Menus.openZones(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("spawn.teleport-on-join", true)) return;
        Player p = e.getPlayer();
        if (plugin.getConfig().isConfigurationSection("main-spawn") && !p.isDead()) {
            p.teleport(plugin.mainSpawn());
        }
    }

    // ================================================================ monstres

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Enemy)) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.CUSTOM || r == CreatureSpawnEvent.SpawnReason.COMMAND) return;
        if (in(e.getLocation(), Flag.NO_MOBS)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Enemy && e.getTarget() instanceof Player p && !inGame(p)
                && in(p.getLocation(), Flag.NO_MOBS)) {
            e.setCancelled(true);
        }
    }

    // ================================================================ joueurs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || inGame(p)) return;
        if (!in(p.getLocation(), Flag.INVINCIBLE)) return;
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            p.teleport(plugin.mainSpawn());
        }
        p.setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || inGame(victim)) return;
        Entity cause = e.getDamageSource().getCausingEntity();
        if (cause instanceof Player attacker && !attacker.equals(victim)
                && (in(victim.getLocation(), Flag.NO_PVP) || in(attacker.getLocation(), Flag.NO_PVP))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && !inGame(p) && in(p.getLocation(), Flag.NO_HUNGER)
                && e.getFoodLevel() < p.getFoodLevel()) {
            e.setCancelled(true);
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
    }

    // ================================================================ blocs

    private boolean buildDenied(Player p, Location l) {
        return !isAdmin(p) && !inGame(p) && in(l, Flag.PROTECT_BUILD);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (buildDenied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Msg.mm("<red>Zone protégée"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (buildDenied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Msg.mm("<red>Zone protégée"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (buildDenied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (buildDenied(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTrample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null
                && buildDenied(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true); // terre labourée, plaques de pression...
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p ? buildDenied(p, e.getEntity().getLocation())
                : in(e.getEntity().getLocation(), Flag.PROTECT_BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> in(b.getLocation(), Flag.NO_EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> in(b.getLocation(), Flag.NO_EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (in(e.getBlock().getLocation(), Flag.NO_EXPLOSIONS)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getPlayer() != null && isAdmin(e.getPlayer())) return;
        if (in(e.getBlock().getLocation(), Flag.NO_EXPLOSIONS)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (e.getSource().getType() == org.bukkit.Material.FIRE && in(e.getBlock().getLocation(), Flag.NO_EXPLOSIONS)) {
            e.setCancelled(true);
        }
    }
}
