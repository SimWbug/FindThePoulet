package fr.simon.findthepoulet.game;

import fr.simon.findthepoulet.util.Players;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;

/** Inventaire + état du joueur avant la partie, rendu à la fin. */
public final class PlayerSnapshot {

    private final ItemStack[] contents;
    private final GameMode gameMode;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final Collection<PotionEffect> effects;
    private final boolean allowFlight;

    private PlayerSnapshot(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        contents = new ItemStack[c.length];
        for (int i = 0; i < c.length; i++) contents[i] = c[i] == null ? null : c[i].clone();
        gameMode = p.getGameMode();
        health = p.getHealth();
        food = p.getFoodLevel();
        saturation = p.getSaturation();
        level = p.getLevel();
        exp = p.getExp();
        effects = new ArrayList<>(p.getActivePotionEffects());
        allowFlight = p.getAllowFlight();
    }

    public static PlayerSnapshot capture(Player p) {
        return new PlayerSnapshot(p);
    }

    public void restore(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contents);
        p.setGameMode(gameMode);
        p.setAllowFlight(allowFlight);
        Players.clearEffects(p);
        p.addPotionEffects(effects);
        p.setHealth(Math.max(1, Math.min(health, Players.maxHealth(p))));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
    }
}
