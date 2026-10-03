package fr.simon.findthepoulet.setup;

import fr.simon.findthepoulet.arena.Region;
import fr.simon.findthepoulet.game.TeamMode;
import org.bukkit.Location;
import org.bukkit.block.BlockState;

import java.util.Map;

/** Création d'arène en cours par un admin. */
public final class SetupSession {

    public enum Stage { NAME, ZONE, SETTINGS }

    volatile Stage stage = Stage.NAME;
    String name;
    Region region;
    TeamMode mode = TeamMode.SOLO;
    boolean pvp = true;

    Map<Location, BlockState> enclosureOriginal;
    int ex, ey, ez, n;

    public Stage stage() { return stage; }
    public String name() { return name; }
    public Region region() { return region; }
    public TeamMode mode() { return mode; }
    public boolean pvp() { return pvp; }
    public boolean hasEnclosure() { return enclosureOriginal != null; }

    public void cycleMode() { mode = mode.next(); }
    public void togglePvp() { pvp = !pvp; }
}
