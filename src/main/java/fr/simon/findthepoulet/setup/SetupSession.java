package fr.simon.findthepoulet.setup;

import fr.simon.findthepoulet.arena.Region;
import fr.simon.findthepoulet.game.GameFormat;
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
    /** Par défaut : on ne se bat que pour le poulet. */
    fr.simon.findthepoulet.game.PvpMode pvpMode = fr.simon.findthepoulet.game.PvpMode.CARRIER;
    int size;
    /** Point cliqué avec le bâton (centre de la zone), pour recalculer la zone si la taille change. */
    Location zoneCenter;
    /** true : zone définie par 2 coins opposés (clic gauche / clic droit) ; false : taille fixe autour d'un point. */
    boolean cornerMode = true;
    /** Mode coins : true = la hauteur entre les 2 coins limite aussi la zone (pavé), false = toute la hauteur. */
    boolean withHeight;
    final Location[] corners = new Location[2];
    GameFormat format = GameFormat.CLASSIC;
    boolean fox;
    boolean kit = true;
    String skin = "aleatoire";

    Map<Location, BlockState> enclosureOriginal;
    int ex, ey, ez, n;

    public Stage stage() { return stage; }
    public String name() { return name; }
    public Region region() { return region; }
    public TeamMode mode() { return mode; }
    public boolean pvp() { return pvp; }
    public fr.simon.findthepoulet.game.PvpMode pvpMode() { return pvpMode; }
    public boolean hasEnclosure() { return enclosureOriginal != null; }

    public int size() { return size; }
    public boolean cornerMode() { return cornerMode; }
    public boolean withHeight() { return withHeight; }
    public Location corner(int i) { return corners[i]; }
    public GameFormat format() { return format; }
    public boolean fox() { return fox; }
    public boolean kit() { return kit; }
    public String skin() { return skin; }

    public void cycleMode() { mode = mode.next(); }
    public void togglePvp() {
        pvpMode = pvpMode.next();
        pvp = pvpMode != fr.simon.findthepoulet.game.PvpMode.OFF;
    }
    public void cycleFormat() { format = format.next(); }
    public void toggleFox() { fox = !fox; }
    public void toggleKit() { kit = !kit; }
    public void setSkin(String skin) { this.skin = skin; }
}
