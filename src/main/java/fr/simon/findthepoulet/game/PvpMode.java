package fr.simon.findthepoulet.game;

/** Règle du PvP dans une arène. */
public enum PvpMode {
    OFF("Désactivé", "<red>OFF"),
    ON("Activé", "<green>ON"),
    /** On ne peut frapper que le porteur du poulet, et le porteur peut riposter. */
    CARRIER("Porteur seulement", "<gold>Porteur");

    private final String label;
    private final String hud;

    PvpMode(String label, String hud) {
        this.label = label;
        this.hud = hud;
    }

    public String label() { return label; }
    public String hud() { return hud; }

    public PvpMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static PvpMode parse(String s, PvpMode def) {
        if (s == null) return def;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
