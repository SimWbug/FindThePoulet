package fr.simon.findthepoulet.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Zone rectangulaire de l'arène. Par défaut toute la hauteur du monde (surface + cavernes) ;
 * peut aussi être limitée en hauteur (zone "cube" entre deux coins).
 */
public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static final int NO_MIN_Y = Integer.MIN_VALUE;
    public static final int NO_MAX_Y = Integer.MAX_VALUE;

    /** Rectangle sur toute la hauteur du monde. */
    public Region(String world, int minX, int minZ, int maxX, int maxZ) {
        this(world, minX, NO_MIN_Y, minZ, maxX, NO_MAX_Y, maxZ);
    }

    /** Carré de "size" blocs autour d'un point (création à taille fixe). */
    public static Region around(Location center, int size) {
        int half = size / 2;
        int minX = center.getBlockX() - half;
        int minZ = center.getBlockZ() - half;
        return new Region(center.getWorld().getName(), minX, minZ, minX + size - 1, minZ + size - 1);
    }

    /** Rectangle (ou pavé si withHeight) entre deux coins opposés. */
    public static Region between(Location a, Location b, boolean withHeight) {
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        if (!withHeight) return new Region(a.getWorld().getName(), minX, minZ, maxX, maxZ);
        return new Region(a.getWorld().getName(), minX, Math.min(a.getBlockY(), b.getBlockY()), minZ,
                maxX, Math.max(a.getBlockY(), b.getBlockY()), maxZ);
    }

    public boolean fullHeight() {
        return minY == NO_MIN_Y && maxY == NO_MAX_Y;
    }

    public boolean containsY(int y) {
        return y >= minY && y <= maxY;
    }

    public boolean contains(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                && containsXZ(l.getBlockX(), l.getBlockZ()) && containsY(l.getBlockY());
    }

    public boolean contains(Block b) {
        return b != null && b.getWorld().getName().equals(world) && containsXZ(b.getX(), b.getZ()) && containsY(b.getY());
    }

    public boolean containsXZ(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(Region o) {
        return world.equals(o.world) && minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ
                && minY <= o.maxY && maxY >= o.minY;
    }

    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }
    public int width() { return maxX - minX + 1; }
    public int depth() { return maxZ - minZ + 1; }
    /** Plus grand côté (bordure de monde carrée, seuils de taille). */
    public int size() { return Math.max(width(), depth()); }
    public long area() { return (long) width() * depth(); }

    /** "64 × 40" ou "64 × 40 × 30 (Y 20 → 49)". */
    public String describe() {
        String s = width() + " × " + depth();
        if (!fullHeight()) s += " × " + (maxY - minY + 1) + " (Y " + minY + " → " + maxY + ")";
        return s;
    }
}
