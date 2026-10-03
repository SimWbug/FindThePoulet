package fr.simon.findthepoulet.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.Set;

public final class Locs {

    private static final Set<Material> UNSAFE = EnumSet.of(
            Material.FIRE, Material.SOUL_FIRE, Material.COBWEB, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE);

    private Locs() {}

    public static void save(ConfigurationSection root, String path, Location l) {
        root.set(path + ".world", l.getWorld().getName());
        root.set(path + ".x", l.getX());
        root.set(path + ".y", l.getY());
        root.set(path + ".z", l.getZ());
        root.set(path + ".yaw", (double) l.getYaw());
        root.set(path + ".pitch", (double) l.getPitch());
    }

    public static Location load(ConfigurationSection root, String path) {
        String worldName = root.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                root.getDouble(path + ".x"), root.getDouble(path + ".y"), root.getDouble(path + ".z"),
                (float) root.getDouble(path + ".yaw"), (float) root.getDouble(path + ".pitch"));
    }

    /** Un joueur / poulet peut-il se tenir ici (pieds + tête libres, sol solide) ? */
    public static boolean standable(World w, int x, int y, int z) {
        if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 1) return false;
        Block feet = w.getBlockAt(x, y, z);
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);
        return free(feet) && free(head)
                && below.getType().isSolid() && !UNSAFE.contains(below.getType());
    }

    private static boolean free(Block b) {
        return b.isPassable() && !b.isLiquid() && !UNSAFE.contains(b.getType());
    }

    /** Cherche une position sûre sur la colonne (x, z) autour de la hauteur nearY. */
    public static Location safeNear(World w, int x, int z, int nearY) {
        for (int d = 0; d <= 16; d++) {
            if (standable(w, x, nearY + d, z)) return center(w, x, nearY + d, z);
            if (d > 0 && standable(w, x, nearY - d, z)) return center(w, x, nearY - d, z);
        }
        return center(w, x, w.getHighestBlockYAt(x, z) + 1, z);
    }

    public static Location center(World w, int x, int y, int z) {
        return new Location(w, x + 0.5, y, z + 0.5);
    }
}
