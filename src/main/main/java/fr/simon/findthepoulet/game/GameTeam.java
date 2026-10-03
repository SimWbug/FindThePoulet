package fr.simon.findthepoulet.game;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class GameTeam {

    private static final String[] COLORS = {"red", "blue", "green", "yellow", "aqua", "light_purple", "gold",
            "dark_purple", "dark_green", "dark_aqua", "dark_red", "dark_blue", "white", "gray", "black"};
    private static final String[] NAMES = {"Rouge", "Bleue", "Verte", "Jaune", "Cyan", "Rose", "Orange",
            "Violette", "Émeraude", "Turquoise", "Bordeaux", "Marine", "Blanche", "Grise", "Noire"};

    public static final int MAX = COLORS.length;
    /** Index spécial : le Renard. */
    private static final int FOX = -1;

    private final int index;
    private final Set<UUID> members = new LinkedHashSet<>();

    public GameTeam(int index) {
        this.index = index;
    }

    public static GameTeam fox() {
        return new GameTeam(FOX);
    }

    public int index() { return index; }
    public boolean isFox() { return index == FOX; }
    public Set<UUID> members() { return members; }
    public boolean has(UUID id) { return members.contains(id); }

    public String colorTag() { return isFox() ? "gold" : COLORS[index]; }
    public String name() { return isFox() ? "Renard" : NAMES[index]; }
    public String scoreboardId() { return isFox() ? "ftpfox" : "ftp" + index; }

    public NamedTextColor color() {
        NamedTextColor c = NamedTextColor.NAMES.value(colorTag());
        return c != null ? c : NamedTextColor.WHITE;
    }

    /** "Équipe Rouge" en couleur (MiniMessage). */
    public String mini() {
        if (isFox()) return "<gold>Le Renard</gold>";
        return "<" + colorTag() + ">Équipe " + name() + "</" + colorTag() + ">";
    }
}
