package fr.simon.findthepoulet.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Dessine le contour d'une zone en particules, visible uniquement par un joueur (seulement la partie proche de lui). */
public final class Outline {

    private Outline() {}

    public static void draw(Player p, String world, int minX, int minZ, int maxX, int maxZ, Particle particle) {
        if (!p.getWorld().getName().equals(world)) return;
        Location pl = p.getLocation();
        double px = pl.getX(), pz = pl.getZ(), y = pl.getY() + 1;
        double x0 = minX, x1 = maxX + 1, z0 = minZ, z1 = maxZ + 1;
        double range = 48;
        // Bords nord / sud
        for (double x = Math.max(x0, px - range); x <= Math.min(x1, px + range); x += 1.5) {
            if (Math.abs(z0 - pz) < range) dot(p, particle, x, y, z0);
            if (Math.abs(z1 - pz) < range) dot(p, particle, x, y, z1);
        }
        // Bords ouest / est
        for (double z = Math.max(z0, pz - range); z <= Math.min(z1, pz + range); z += 1.5) {
            if (Math.abs(x0 - px) < range) dot(p, particle, x0, y, z);
            if (Math.abs(x1 - px) < range) dot(p, particle, x1, y, z);
        }
    }

    private static void dot(Player p, Particle particle, double x, double y, double z) {
        p.spawnParticle(particle, x, y - 1, z, 1, 0, 0, 0, 0);
        p.spawnParticle(particle, x, y + 1.5, z, 1, 0, 0, 0, 0);
    }
}
