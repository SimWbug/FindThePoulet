package fr.simon.findthepoulet.util;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class Players {

    private Players() {}

    public static double maxHealth(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    public static void clearEffects(Player p) {
        for (PotionEffect effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
    }

    /** Inventaire vidé, vie/faim au max, mode de jeu donné. */
    public static void reset(Player p, GameMode mode) {
        p.getInventory().clear();
        p.setItemOnCursor(null);
        clearEffects(p);
        p.setGameMode(mode);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(5f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }
}
