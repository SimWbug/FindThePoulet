package fr.simon.findthepoulet.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import fr.simon.findthepoulet.FindThePoulet;
import fr.simon.findthepoulet.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Déguisements du poulet, 100 % vanilla : une "item display" qui chevauche le poulet. */
public final class SkinManager {

    public static final String RANDOM = "aleatoire";
    public static final String NONE = "aucun";

    /** Hauteur par défaut où se pose un passager sur un poulet (corrigée à l'exécution). */
    private static final float DEFAULT_ATTACH = 0.7f;

    private final FindThePoulet plugin;
    private final Map<String, ChickenSkin> skins = new LinkedHashMap<>();
    private boolean enabled;
    private String variant;
    private Particle particle;

    public SkinManager(FindThePoulet plugin) {
        this.plugin = plugin;
    }

    // ================================================================ configuration

    public void load() {
        skins.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("chicken-skin");
        if (root == null) {
            enabled = false;
            return;
        }
        enabled = root.getBoolean("enabled", true);
        variant = root.getString("variant", "random").toLowerCase(Locale.ROOT);
        particle = parseParticle(root.getString("particle", ""));

        ConfigurationSection list = root.getConfigurationSection("skins");
        if (list == null) return;
        for (String key : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(key);
            if (s == null) continue;
            ItemStack item = buildItem(s);
            if (item == null) {
                plugin.getLogger().warning("Skin de poulet '" + key + "' ignoré : indique material, head-owner ou head-texture.");
                continue;
            }
            boolean head = item.getType() == Material.PLAYER_HEAD;
            float scale = (float) s.getDouble("scale", root.getDouble(head ? "head-scale" : "scale", head ? 0.75 : 0.42));
            float offsetY = (float) s.getDouble("offset-y", root.getDouble("offset-y", 0.75));
            float forward = (float) s.getDouble("offset-forward", root.getDouble("offset-forward", 0.28));
            float rotation = (float) s.getDouble("rotation", root.getDouble(head ? "head-rotation" : "rotation", 0));
            String name = s.getString("name", "<gold><bold>LE POULET");
            String id = key.toLowerCase(Locale.ROOT);
            skins.put(id, new ChickenSkin(id, name, item, scale, offsetY, forward, rotation, head ? -0.25f : 0f));
        }
    }

    private static ItemStack buildItem(ConfigurationSection s) {
        String texture = s.getString("head-texture", "");
        String owner = s.getString("head-owner", "");
        if (texture != null && !texture.isBlank()) {
            UUID id = UUID.nameUUIDFromBytes(("ftp-skin-" + texture).getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(id, "FTPSkin");
            profile.setProperty(new ProfileProperty("textures", texture));
            return head(profile);
        }
        if (owner != null && !owner.isBlank()) {
            return head(Bukkit.createProfile(owner));
        }
        Material m = Material.matchMaterial(s.getString("material", ""));
        return m == null || !m.isItem() || m.isAir() ? null : new ItemStack(m);
    }

    private static ItemStack head(PlayerProfile profile) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return head;
    }

    private Particle parseParticle(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            Particle p = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            if (p.getDataType() != Void.class) {
                plugin.getLogger().warning("La particule " + name + " demande des données, choisis-en une autre (END_ROD, HEART, FLAME...).");
                return null;
            }
            return p;
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Particule inconnue : " + name);
            return null;
        }
    }

    // ================================================================ accès

    public boolean isEnabled() { return enabled; }

    public List<String> keys() { return new ArrayList<>(skins.keySet()); }

    public ChickenSkin get(String key) {
        return key == null ? null : skins.get(key.toLowerCase(Locale.ROOT));
    }

    /** Choix d'une arène : aléatoire, aucun ou un skin précis. */
    public ChickenSkin pick(String choice) {
        if (!enabled || skins.isEmpty() || NONE.equalsIgnoreCase(choice)) return null;
        ChickenSkin fixed = get(choice);
        if (fixed != null) return fixed;
        List<ChickenSkin> all = new ArrayList<>(skins.values());
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    /** Valeur suivante pour le menu admin : aléatoire → aucun → chaque skin → aléatoire... */
    public String nextChoice(String current) {
        List<String> cycle = new ArrayList<>();
        cycle.add(RANDOM);
        cycle.add(NONE);
        cycle.addAll(skins.keySet());
        int i = cycle.indexOf(current == null ? RANDOM : current.toLowerCase(Locale.ROOT));
        return cycle.get((i + 1) % cycle.size());
    }

    public String label(String choice) {
        if (choice == null || RANDOM.equalsIgnoreCase(choice)) return "Aléatoire";
        if (NONE.equalsIgnoreCase(choice)) return "Aucun";
        return get(choice) != null ? choice : choice + " (introuvable → aléatoire)";
    }

    // ================================================================ application

    /** Variante du poulet (tempéré / chaud / froid) : vraie texture vanilla. */
    public void applyVariant(Chicken chicken) {
        Chicken.Variant v = switch (variant == null ? "random" : variant) {
            case "temperate" -> Chicken.Variant.TEMPERATE;
            case "warm" -> Chicken.Variant.WARM;
            case "cold" -> Chicken.Variant.COLD;
            case "random" -> switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> Chicken.Variant.TEMPERATE;
                case 1 -> Chicken.Variant.WARM;
                default -> Chicken.Variant.COLD;
            };
            default -> null;
        };
        if (v != null) chicken.setVariant(v);
    }

    /** Pose le déguisement sur la tête du poulet. */
    public SkinHandle apply(Chicken chicken, ChickenSkin skin) {
        Location l = chicken.getLocation();
        ItemDisplay display = l.getWorld().spawn(l, ItemDisplay.class);
        display.setItemStack(skin.item());
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setBillboard(Display.Billboard.FIXED);
        display.setPersistent(false);
        display.setTeleportDuration(2);
        display.setViewRange(1.5f);
        display.getPersistentDataContainer().set(Items.chickenKey(), PersistentDataType.BYTE, (byte) 2);
        display.setTransformation(transformation(skin, DEFAULT_ATTACH));
        chicken.addPassenger(display);

        SkinHandle handle = new SkinHandle(skin, chicken, display);
        int[] age = {0};
        handle.setTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!chicken.isValid() || !display.isValid()) {
                handle.remove();
                return;
            }
            if (display.getVehicle() == null || !display.getVehicle().equals(chicken)) chicken.addPassenger(display);
            // L'objet suit la tête du poulet (getYaw = rotation de la tête pour un animal)
            display.setRotation(chicken.getYaw(), 0f);
            if (++age[0] == 3) {
                // Hauteur réelle où le jeu pose le passager : on corrige le placement une fois
                float attach = (float) (display.getLocation().getY() - chicken.getLocation().getY());
                if (attach > 0.1f && attach < 2f) display.setTransformation(transformation(skin, attach));
            }
        }, 1L, 1L));
        return handle;
    }

    private static Transformation transformation(ChickenSkin s, float attach) {
        float ty = s.offsetY() - attach - s.modelY() * s.scale();
        return new Transformation(
                new Vector3f(0f, ty, s.forward()),
                new AxisAngle4f((float) Math.toRadians(s.rotation()), 0f, 1f, 0f),
                new Vector3f(s.scale(), s.scale(), s.scale()),
                new AxisAngle4f());
    }

    /** Petite traînée de particules derrière le poulet. */
    public void trail(Chicken chicken) {
        if (particle == null || chicken == null || !chicken.isValid()) return;
        Location l = chicken.getLocation().add(0, 0.4, 0);
        try {
            l.getWorld().spawnParticle(particle, l, 2, 0.15, 0.15, 0.15, 0.01);
        } catch (IllegalArgumentException ignored) {
            particle = null;
        }
    }

    /** Aperçu pour les admins : un poulet déguisé immobile pendant 20 secondes. */
    public void preview(Location at, ChickenSkin skin) {
        Chicken c = at.getWorld().spawn(at, Chicken.class);
        c.setAI(false);
        c.setInvulnerable(true);
        c.setPersistent(false);
        c.setAdult();
        c.customName(fr.simon.findthepoulet.util.Msg.mm(skin.name()));
        c.setCustomNameVisible(true);
        c.setRotation(at.getYaw() + 180f, 0f); // face à l'admin
        applyVariant(c);
        SkinHandle h = apply(c, skin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            h.remove();
            if (c.isValid()) c.remove();
        }, 400L);
    }
}
