package fr.simon.findthepoulet.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Zone carrée de l'arène (toute la hauteur du monde : surface + cavernes). */
public record Region(String world, int minX, int minZ, int maxX, int maxZ) {

    public static Region around(Location center, int size) {
        int half = size / 2;
        int minX = center.getBlockX() - half;
        int minZ = center.getBlockZ() - half;
        return new Region(center.getWorld().getName(), minX, minZ, minX + size - 1, minZ + size - 1);
    }

    public boolean contains(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                && containsXZ(l.getBlockX(), l.getBlockZ());
    }

    public boolean contains(Block b) {
        return b != null && b.getWorld().getName().equals(world) && containsXZ(b.getX(), b.getZ());
    }

    public boolean containsXZ(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(Region o) {
        return world.equals(o.world) && minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }
    public int size() { return maxX - minX + 1; }
}
