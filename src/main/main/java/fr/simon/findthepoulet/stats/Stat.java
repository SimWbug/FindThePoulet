package fr.simon.findthepoulet.stats;

import org.bukkit.Material;

import java.util.Locale;

public enum Stat {
    WINS("victoires", "Victoires", Material.GOLD_INGOT),
    GAMES("parties", "Parties jouées", Material.CLOCK),
    CATCHES("attrapes", "Poulets attrapés", Material.FEATHER),
    DELIVERIES("livraisons", "Poulets ramenés", Material.HAY_BLOCK),
    KILLS("tues", "Poulets tués (oups)", Material.COOKED_CHICKEN),
    ELIMINATIONS("eliminations", "Éliminations (Renard)", Material.SWEET_BERRIES);

    private final String key;
    private final String label;
    private final Material icon;

    Stat(String key, String label, Material icon) {
        this.key = key;
        this.label = label;
        this.icon = icon;
    }

    public String key() { return key; }
    public String label() { return label; }
    public Material icon() { return icon; }

    public static Stat parse(String s) {
        if (s == null || s.isBlank()) return null;
        String k = s.toLowerCase(Locale.ROOT);
        for (Stat st : values()) {
            if (st.key.equals(k) || st.name().equalsIgnoreCase(k) || (st.key + "s").equals(k) || st.key.equals(k + "s")) return st;
        }
        return null;
    }
}
