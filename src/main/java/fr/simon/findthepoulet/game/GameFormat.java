package fr.simon.findthepoulet.game;

/** Classique : le premier poulet ramené gagne. Manches : la première équipe à N poulets ramenés gagne. */
public enum GameFormat {
    CLASSIC("Classique"),
    ROUNDS("Manches");

    private final String label;

    GameFormat(String label) {
        this.label = label;
    }

    public String label() { return label; }

    public GameFormat next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static GameFormat parse(String s) {
        if (s == null) return CLASSIC;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CLASSIC;
        }
    }
}
