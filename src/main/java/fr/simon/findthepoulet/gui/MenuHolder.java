package fr.simon.findthepoulet.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Identifie nos menus (les clics y sont bloqués et redirigés vers {@link Menus#handle}). */
public final class MenuHolder implements InventoryHolder {

    public enum Type { MAIN, ARENA_ADMIN, SETUP, STATS }

    private final Type type;
    private final String arenaName;
    private final Map<Integer, String> arenaSlots = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Type type, String arenaName) {
        this.type = type;
        this.arenaName = arenaName;
    }

    public Type type() { return type; }
    public String arenaName() { return arenaName; }
    public Map<Integer, String> arenaSlots() { return arenaSlots; }

    void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        if (inventory == null) inventory = Bukkit.createInventory(this, 9);
        return inventory;
    }
}
