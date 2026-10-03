package fr.simon.findthepoulet.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.arena.BlockRestorer;
import fr.simon.findthepoulet.game.Flute;
import fr.simon.findthepoulet.game.Gadgets;
import fr.simon.findthepoulet.util.Items;
import fr.simon.findthepoulet.game.Game;
import fr.simon.findthepoulet.gui.Menus;
import fr.simon.findthepoulet.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Règles de la partie + enregistrement de toutes les modifications de blocs pour le reset de l'arène. */
public final class GameListener implements Listener {

    private final FindThePoulet plugin;

    public GameListener(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    // ================================================================ utilitaires

    private Game of(Player p) {
        return plugin.games().of(p);
    }

    private Game at(Location l) {
        return plugin.games().activeAt(l);
    }

    private Game at(Block b) {
        return b == null ? null : at(b.getLocation());
    }

    /** Admin en créatif / spectateur : peut entrer et modifier une arène en cours. */
    private static boolean bypass(Player p) {
        return p.hasPermission(Menus.ADMIN_PERM)
                && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    /** Ce joueur n'a pas le droit de modifier cette arène. */
    private boolean denied(Player p, Game g) {
        return !g.isRunning() || (of(p) != g && !bypass(p));
    }

    /** Enregistre un bloc qui va changer. @return true si le changement doit être annulé (fin de partie). */
    private boolean track(Block b) {
        Game g = at(b);
        if (g == null) return false;
        if (!g.isRunning()) return true;
        g.restorer().record(b);
        return false;
    }

    /** Pareil, mais l'enclos est protégé. */
    private boolean trackProtected(Block b) {
        Game g = at(b);
        if (g == null) return false;
        if (!g.isRunning() || g.arena().isEnclosureBlock(b)) return true;
        g.restorer().record(b);
        return false;
    }

    /** Bloc + voisins + colonne au-dessus (sable, cannes à sucre, bambous... qui tombent avec). */
    private static void recordBreak(BlockRestorer r, Block b) {
        r.recordAround(b);
        Block up = b.getRelative(BlockFace.UP);
        for (int i = 0; i < 32 && !up.getType().isAir(); i++) {
            r.recordAround(up);
            up = up.getRelative(BlockFace.UP);
        }
    }

    // ================================================================ le poulet

    @EventHandler(priority = EventPriority.HIGH)
    public void onChickenInteract(PlayerInteractEntityEvent e) {
        Game g = plugin.games().byChicken(e.getRightClicked());
        if (g == null) return;
        e.setCancelled(true); // pas de laisse, pas de graines, pas de nom
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (of(p) == g && g.isRunning()) g.tryGrab(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        Game chickenGame = plugin.games().byChicken(victim);
        if (chickenGame != null) {
            // Le poulet ne prend que les coups des joueurs de la partie (chute, lave, noyade... ignorés)
            Entity cause = e.getDamageSource().getCausingEntity();
            boolean byPlayer = cause instanceof Player p && of(p) == chickenGame;
            if (!chickenGame.isRunning() || !byPlayer) {
                e.setCancelled(true);
                victim.setFireTicks(0);
            }
            return;
        }
        if (victim instanceof Player p) {
            Game g = of(p);
            if (g != null && !g.isRunning()) e.setCancelled(true); // lobby / fin de partie : invincible
            else if (g != null && e.getCause() == EntityDamageEvent.DamageCause.FALL && g.hasNoFall(p)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamageSource().getCausingEntity() instanceof Player attacker) || attacker.equals(victim)) return;
        Game g = of(victim);
        Game ga = of(attacker);
        if (g == null && ga == null) return;
        if (g != ga || g == null || !g.isRunning()) {
            e.setCancelled(true); // joueurs d'une partie vs joueurs extérieurs
            return;
        }
        if (g.isEliminated(attacker) || g.isEliminated(victim) || g.sameTeam(attacker, victim)) {
            e.setCancelled(true);
            return;
        }
        if (g.isCarrierSafe(victim)) {
            // Porteur protégé près de l'enclos (anti-camping)
            e.setCancelled(true);
            attacker.sendActionBar(Msg.mm("<gray>Le porteur est protégé près de l'enclos !"));
            return;
        }
        if (g.isFox(attacker) && g.isCarrier(victim)) {
            g.foxCatch(attacker, victim);
            e.setCancelled(true);
            return;
        }
        if (g.isCarrier(victim) && plugin.dropOnHit()) g.dropChicken(victim, attacker);
        if (!g.arena().isPvp()) e.setCancelled(true);
    }

    @EventHandler
    public void onChickenDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Chicken chicken)) return;
        Game g = plugin.games().byChicken(chicken);
        if (g == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player killer = chicken.getKiller();
        Bukkit.getScheduler().runTask(plugin, () -> g.chickenKilled(killer));
    }

    /** Objets des coffres : flûte (fait caqueter le poulet), boussole (pointe vers lui), plume (bond). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGadget(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        String tag = Items.tagOf(item);
        if (!Items.isGadget(tag)) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        // Clic sur un coffre / une porte : on laisse l'action normale (sauf en s'accroupissant)
        Block clicked = e.getClickedBlock();
        if (a == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return;
        e.setCancelled(true);

        Game g = of(p);
        if (g == null || !g.isRunning()) {
            Msg.send(p, "<red>Cet objet ne fonctionne que pendant une partie.");
            return;
        }
        EquipmentSlot hand = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        if (Items.COMPASS.equals(tag)) {
            g.useCompass(p, item);
            p.getInventory().setItem(hand, item);
            return;
        }
        if (Items.FEATHER.equals(tag)) {
            if (!g.useFeather(p)) return;
            if (Gadgets.consumeFeather(item, Gadgets.featherUses(plugin.getConfig()))) {
                p.getInventory().setItem(hand, item);
            } else {
                p.getInventory().setItem(hand, null);
                p.sendActionBar(Msg.mm("<gray>Ta plume s'est envolée !"));
            }
            return;
        }
        if (!g.useFlute(p)) return;
        if (Flute.consume(item, Flute.maxUses(plugin.getConfig()))) {
            p.getInventory().setItem(hand, item);
        } else {
            p.getInventory().setItem(hand, null);
            Msg.sound(p, "entity.item.break", 1f);
            p.sendActionBar(Msg.mm("<gray>Ta flûte s'est cassée !"));
        }
    }

    /** Pas d'autres poulets dans une arène en cours : apparitions naturelles, œufs, jockeys... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChickenSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Chicken)) return;
        if (e.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) return; // le poulet du jeu
        if (at(e.getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onEggThrow(org.bukkit.event.player.PlayerEggThrowEvent e) {
        if (at(e.getEgg().getLocation()) != null || of(e.getPlayer()) != null) e.setHatching(false);
    }

    /** Le poulet du jeu ne pond pas d'œufs vanilla (les indices du plugin sont gérés à part). */
    @EventHandler(ignoreCancelled = true)
    public void onLay(org.bukkit.event.entity.EntityDropItemEvent e) {
        if (plugin.games().byChicken(e.getEntity()) != null) e.setCancelled(true);
    }

    /** Un chunk de l'arène se charge en pleine partie : on y traite les poulets "intrus". */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!(en instanceof Chicken)) continue;
            Game g = at(en.getLocation());
            if (g != null) g.handleOtherChicken(en);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (e.getDismounted() instanceof Player p && plugin.games().byChicken(e.getEntity()) != null) {
            p.setGlowing(false);
        }
    }

    // ================================================================ joueurs

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            Game g = of(p);
            if (g != null && !g.isRunning()) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Game g = of(p);
        if (g == null) return;
        g.onPlayerDeath(p);
        if (!g.isRunning() || plugin.keepInventory()) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = of(p);
        if (g == null) return;
        if (g.isActive()) {
            e.setRespawnLocation(g.respawnLocation());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && of(p) == g) g.onRespawn(p);
            });
        } else {
            e.setRespawnLocation(plugin.waitingLobby(g.arena()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Player p = e.getPlayer();
        Location to = e.getTo();
        Game g = of(p);
        if (g != null) {
            if (!g.isRunning() || g.arena().region().contains(to)) return;
            if (g.arena().region().contains(e.getFrom())) e.setCancelled(true);
            else p.teleport(g.respawnLocation());
            return;
        }
        Game other = at(to);
        if (other != null && !other.arena().region().contains(e.getFrom()) && !bypass(p)) {
            e.setCancelled(true);
            p.sendActionBar(Msg.mm("<red>Une partie de Find The Poulet est en cours ici !"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN || cause == PlayerTeleportEvent.TeleportCause.COMMAND
                || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        Player p = e.getPlayer();
        Game g = of(p);
        if (g != null) {
            if (g.isRunning() && !g.arena().region().contains(e.getTo())) e.setCancelled(true);
        } else if (at(e.getTo()) != null && !bypass(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        // Pas d'autre dimension pendant une partie
        if (of(e.getPlayer()) != null || at(e.getFrom()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (at(e.getFrom()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        for (BlockState s : e.getBlocks()) {
            if (at(s.getLocation()) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("block-commands-in-game", true)) return;
        Player p = e.getPlayer();
        if (of(p) == null || p.hasPermission(Menus.ADMIN_PERM)) return;
        String msg = e.getMessage();
        String label = (msg.startsWith("/") ? msg.substring(1) : msg).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        if (label.equals("poulet") || allowed.stream().anyMatch(label::equalsIgnoreCase)) return;
        e.setCancelled(true);
        Msg.send(p, "<red>Cette commande est bloquée pendant une partie. <gray>Utilise <yellow>/poulet quitter</yellow> pour partir.");
    }

    // ================================================================ blocs posés / cassés par les joueurs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Game own = of(p);
        if (own != null && own.isRunning() && !own.arena().region().contains(b)) {
            e.setCancelled(true);
            return;
        }
        Game g = at(b);
        if (g == null) return;
        if (denied(p, g)) {
            e.setCancelled(true);
            return;
        }
        if (g.arena().isEnclosureBlock(b)) {
            e.setCancelled(true);
            p.sendActionBar(Msg.mm("<red>L'enclos est protégé !"));
            return;
        }
        recordBreak(g.restorer(), b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Game own = of(p);
        if (own != null && own.isRunning() && !own.arena().region().contains(b)) {
            e.setCancelled(true);
            return;
        }
        Game g = at(b);
        if (g == null) return;
        if (denied(p, g)) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState s : multi.getReplacedBlockStates()) {
                if (g.arena().isEnclosureBlock(s.getBlock()) || !g.arena().region().contains(s.getLocation())) {
                    e.setCancelled(true);
                    return;
                }
            }
            multi.getReplacedBlockStates().forEach(g.restorer()::recordState);
        } else {
            if (g.arena().isEnclosureBlock(b)) {
                e.setCancelled(true);
                p.sendActionBar(Msg.mm("<red>Impossible de construire dans l'enclos !"));
                return;
            }
            g.restorer().recordState(e.getBlockReplacedState());
        }
        g.restorer().record(e.getBlockAgainst());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (bucket(e.getPlayer(), e.getBlock(), e.getBlockClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (bucket(e.getPlayer(), e.getBlock(), e.getBlockClicked())) e.setCancelled(true);
    }

    private boolean bucket(Player p, Block target, Block clicked) {
        Game own = of(p);
        if (own != null && own.isRunning() && !own.arena().region().contains(target)) return true;
        Game g = at(target);
        if (g == null) return false;
        if (denied(p, g) || g.arena().isEnclosureBlock(target)) return true;
        g.restorer().recordAround(target);
        g.restorer().record(clicked);
        return false;
    }

    /** Interdit les interactions (portes, coffres...) aux joueurs extérieurs et en fin de partie. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractDeny(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        Game g = at(e.getClickedBlock());
        if (g != null && denied(e.getPlayer(), g)) e.setCancelled(true);
    }

    /** Enregistre ce qu'une interaction peut changer : portes, leviers, terre labourée, gâteau, briquet... */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractRecord(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.useInteractedBlock() == Event.Result.DENY && e.useItemInHand() == Event.Result.DENY) return;
        Game g = at(b);
        if (g == null || !g.isRunning()) return;
        g.restorer().recordAround(b);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) g.restorer().recordAround(b.getRelative(e.getBlockFace()));
    }

    // ================================================================ coffres, fours... (contenu restauré)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        Location l = e.getInventory().getLocation();
        if (l == null || !(e.getPlayer() instanceof Player p)) return;
        Game g = at(l);
        if (g == null) return;
        if (denied(p, g)) {
            e.setCancelled(true);
            return;
        }
        recordContainer(g, e.getInventory());
        g.chestOpened(l);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        for (Inventory inv : new Inventory[]{e.getSource(), e.getDestination()}) {
            Location l = inv.getLocation();
            if (l == null) continue;
            Game g = at(l);
            if (g != null && g.isRunning()) recordContainer(g, inv);
        }
    }

    private static void recordContainer(Game g, Inventory inv) {
        InventoryHolder holder = inv.getHolder(false);
        if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof BlockState left) g.restorer().record(left.getBlock());
            if (dc.getRightSide() instanceof BlockState right) g.restorer().record(right.getBlock());
        } else if (holder instanceof BlockState state) {
            g.restorer().record(state.getBlock());
        } else if (inv.getLocation() != null) {
            g.restorer().record(inv.getLocation().getBlock());
        }
    }

    // ================================================================ explosions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explosion(e.getLocation(), e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explosion(e.getBlock().getLocation(), e.blockList());
    }

    private void explosion(Location origin, List<Block> blocks) {
        Game source = at(origin);
        blocks.removeIf(b -> {
            // Une explosion dans une arène n'abîme pas l'extérieur
            if (source != null && !source.arena().region().contains(b)) return true;
            Game g = at(b);
            if (g == null) return false;
            if (!g.isRunning() || g.arena().isEnclosureBlock(b)) return true;
            recordBreak(g.restorer(), b);
            return false;
        });
    }

    // ================================================================ changements "naturels" des blocs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Game from = at(e.getBlock());
        Game to = at(e.getToBlock());
        if (from != null && to != from) { // l'eau / la lave ne sort pas de l'arène
            e.setCancelled(true);
            return;
        }
        if (to != null && (!to.isRunning() || to.arena().isEnclosureBlock(e.getToBlock()))) {
            e.setCancelled(true);
            return;
        }
        if (to != null) to.restorer().record(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (trackProtected(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        if (track(e.getBlock()) || trackStates(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        if (track(e.getLocation().getBlock()) || trackStates(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent e) {
        if (track(e.getBlock()) || trackStates(e.getBlocks())) e.setCancelled(true);
    }

    /** Arbres, champignons géants, éponges : n'importe quel bloc touché peut annuler (enclos, fin de partie). */
    private boolean trackStates(List<BlockState> states) {
        for (BlockState s : states) if (trackProtected(s.getBlock())) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        Block b = e.getBlock();
        if (track(b)) {
            e.setCancelled(true);
            return;
        }
        BlockData data = b.getBlockData();
        if (data instanceof Directional dir && trackProtected(b.getRelative(dir.getFacing()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent e) {
        if (track(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (trackProtected(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (piston(e.getBlock(), e.getBlocks(), true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (piston(e.getBlock(), e.getBlocks(), false)) e.setCancelled(true);
    }

    private boolean piston(Block piston, List<Block> moved, boolean extend) {
        Game g = at(piston);
        if (g == null) {
            for (Block b : moved) if (at(b) != null) return true; // piston extérieur qui pousse dans une arène
            return false;
        }
        if (!g.isRunning()) return true;
        if (!(piston.getBlockData() instanceof Directional dir)) return false;
        BlockFace facing = dir.getFacing();
        BlockFace motion = extend ? facing : facing.getOppositeFace();
        g.restorer().recordAround(piston);
        g.restorer().record(piston.getRelative(facing));
        for (Block b : moved) {
            Block dest = b.getRelative(motion);
            if (g.arena().isEnclosureBlock(b) || g.arena().isEnclosureBlock(dest)
                    || !g.arena().region().contains(dest)) return true;
            g.restorer().recordAround(b);
            g.restorer().record(dest);
        }
        return false;
    }

    // ================================================================ entités (nettoyées en fin de partie)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        Game g = at(e.getEntity().getLocation());
        if (g == null) return;
        if (!g.isRunning()) e.setCancelled(true);
        else g.trackEntity(e.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        Game g = at(e.getEntity().getLocation());
        // Les tableaux / cadres d'origine de la map sont protégés
        if (g != null && !g.isTracked(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        if (e.getEntity() instanceof Player) return;
        Game g = at(e.getLocation());
        if (g != null) g.trackEntity(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        Game g = at(e.getEntity().getLocation());
        if (g != null) g.trackEntity(e.getEntity());
    }
}
