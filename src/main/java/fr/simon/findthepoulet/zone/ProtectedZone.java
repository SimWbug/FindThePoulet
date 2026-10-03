package fr.simon.findthepoulet.zone;

import fr.simon.findthepoulet.arena.Region;

/** Zone protégée (lobby / spawn du serveur) : toute la hauteur du monde entre deux coins. */
public final class ProtectedZone {

    public enum Flag {
        NO_MOBS("Monstres bloqués", "Pas d'apparition, retirés s'ils entrent,\nils ne ciblent pas les joueurs"),
        INVINCIBLE("Joueurs invincibles", "Aucun dégât (chute, lave, monstres...)"),
        NO_HUNGER("Pas de faim", "La barre de faim reste pleine"),
        NO_PVP("PvP désactivé", "Les joueurs ne peuvent pas se frapper"),
        PROTECT_BUILD("Construction protégée", "Seuls les admins cassent / posent des blocs"),
        NO_EXPLOSIONS("Pas d'explosions ni de feu", "TNT, creepers, feu sans effet sur les blocs");

        private final String label;
        private final String description;

        Flag(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() { return label; }
        public String description() { return description; }
        public String key() { return name().toLowerCase(); }
    }

    private final String name;
    private Region region;
    private final java.util.EnumSet<Flag> flags = java.util.EnumSet.allOf(Flag.class);

    public ProtectedZone(String name, Region region) {
        this.name = name;
        this.region = region;
    }

    public String name() { return name; }
    public Region region() { return region; }
    public void setRegion(Region region) { this.region = region; }

    public boolean has(Flag f) { return flags.contains(f); }

    public void set(Flag f, boolean on) {
        if (on) flags.add(f);
        else flags.remove(f);
    }

    public void toggle(Flag f) { set(f, !has(f)); }
}
