package fr.simon.findthepoulet.game;

public enum TeamMode {
    SOLO(1, "Solo"),
    DUO(2, "Duo"),
    TRIO(3, "Trio"),
    QUATUOR(4, "Quatuor");

    private final int size;
    private final String label;

    TeamMode(int size, String label) {
        this.size = size;
        this.label = label;
    }

    public int size() { return size; }
    public String label() { return label; }

    public TeamMode next() {
        TeamMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static TeamMode parse(String s) {
        if (s == null) return SOLO;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SOLO;
        }
    }
}
