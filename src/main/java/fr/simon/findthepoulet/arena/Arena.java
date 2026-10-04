package fr.simon.findthepoulet.arena;

import fr.simon.findthepoulet.game.GameFormat;
import fr.simon.findthepoulet.game.TeamMode;
import fr.simon.findthepoulet.util.Locs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class Arena {

    private final String name;
    private final Region region;
    private TeamMode mode;
    private boolean pvp;
    private boolean enabled;
    /** Déguisement du poulet : "aleatoire", "aucun" ou le nom d'un skin de la config. */
    private String skin = "aleatoire";
    private GameFormat format = GameFormat.CLASSIC;
    private boolean fox;
    private boolean kit = true;
    private fr.simon.findthepoulet.game.PvpMode pvpMode;

    // Coin intérieur de l'enclos (x, z) et hauteur où l'on marche (y)
    private final int ex, ey, ez, size;

    public Arena(String name, Region region, TeamMode mode, boolean pvp, boolean enabled,
                 int ex, int ey, int ez, int size) {
        this.name = name;
        this.region = region;
        this.mode = mode;
        this.pvp = pvp;
        this.pvpMode = pvp ? fr.simon.findthepoulet.game.PvpMode.ON : fr.simon.findthepoulet.game.PvpMode.OFF;
        this.enabled = enabled;
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
        this.size = size;
    }

    public String name() { return name; }
    public Region region() { return region; }
    public TeamMode mode() { return mode; }
    /** PvP possible d'une façon ou d'une autre (activé ou "porteur seulement"). */
    public boolean isPvp() { return pvpMode != fr.simon.findthepoulet.game.PvpMode.OFF; }
    public fr.simon.findthepoulet.game.PvpMode pvpMode() { return pvpMode; }
    public void setPvpMode(fr.simon.findthepoulet.game.PvpMode mode) {
        this.pvpMode = mode == null ? fr.simon.findthepoulet.game.PvpMode.ON : mode;
        this.pvp = isPvp();
    }
    public boolean isEnabled() { return enabled; }
    public int ex() { return ex; }
    public int ey() { return ey; }
    public int ez() { return ez; }
    public int enclosureSize() { return size; }

    public void setMode(TeamMode mode) { this.mode = mode; }
    public void setPvp(boolean pvp) { setPvpMode(pvp ? fr.simon.findthepoulet.game.PvpMode.ON : fr.simon.findthepoulet.game.PvpMode.OFF); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String skin() { return skin; }
    public GameFormat format() { return format; }
    public void setFormat(GameFormat format) { this.format = format == null ? GameFormat.CLASSIC : format; }
    /** Mode Renard : un joueur tiré au sort chasse les porteurs. */
    public boolean isFox() { return fox; }
    public void setFox(boolean fox) { this.fox = fox; }
    /** Kit de départ (outils en bois, nourriture...). */
    public boolean isKit() { return kit; }
    public void setKit(boolean kit) { this.kit = kit; }
    public void setSkin(String skin) { this.skin = skin == null ? "aleatoire" : skin; }

    public World world() {
        return Bukkit.getWorld(region.world());
    }

    /** Est-ce que cette position est à l'intérieur de l'enclos ? */
    public boolean isInEnclosure(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(region.world())) return false;
        double x = l.getX(), y = l.getY(), z = l.getZ();
        return x >= ex && x < ex + size && z >= ez && z < ez + size && y >= ey - 1 && y <= ey + 3;
    }

    /** Bloc faisant partie de l'enclos (sol, barrières, porte, intérieur) : protégé pendant la partie. */
    public boolean isEnclosureBlock(Block b) {
        if (!b.getWorld().getName().equals(region.world())) return false;
        int x = b.getX(), y = b.getY(), z = b.getZ();
        return x >= ex - 1 && x <= ex + size && z >= ez - 1 && z <= ez + size && y >= ey - 1 && y <= ey + 2;
    }

    public Location enclosureCenter() {
        return new Location(world(), ex + size / 2.0, ey, ez + size / 2.0);
    }

    /** Point de départ : devant la porte de l'enclos (côté sud). */
    public Location spawnPoint() {
        Location l = Locs.safeNear(world(), ex + size / 2, ez + size + 2, ey);
        l.setYaw(180f);
        return l;
    }
}
