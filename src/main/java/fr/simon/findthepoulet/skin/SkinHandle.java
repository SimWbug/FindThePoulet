package fr.simon.findthepoulet.skin;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitTask;

/** Déguisement posé sur un poulet : l'entité d'affichage + la tâche qui la garde alignée. */
public final class SkinHandle {

    private final ChickenSkin skin;
    private final Chicken chicken;
    private final ItemDisplay display;
    private BukkitTask task;

    SkinHandle(ChickenSkin skin, Chicken chicken, ItemDisplay display) {
        this.skin = skin;
        this.chicken = chicken;
        this.display = display;
    }

    void setTask(BukkitTask task) { this.task = task; }

    public ChickenSkin skin() { return skin; }
    public Chicken chicken() { return chicken; }
    public ItemDisplay display() { return display; }

    public void remove() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (display.isValid()) {
            display.leaveVehicle();
            display.remove();
        }
    }
}
