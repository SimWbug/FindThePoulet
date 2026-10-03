package fr.simon.findthepoulet.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Construit l'enclos : sol en paille, barrières en chêne, une petite porte au sud, lanternes aux coins. */
public final class EnclosureBuilder {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private EnclosureBuilder() {}

    /** @return l'état d'origine des blocs remplacés (pour annuler). */
    public static Map<Location, BlockState> build(World w, int ex, int ey, int ez, int n) {
        Map<Location, BlockState> original = new LinkedHashMap<>();
        for (int x = ex - 1; x <= ex + n; x++)
            for (int z = ez - 1; z <= ez + n; z++)
                for (int y = ey - 1; y <= ey + 2; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    original.put(b.getLocation(), b.getState());
                }

        int gateX = ex + n / 2;
        int gateZ = ez + n;
        for (int x = ex - 1; x <= ex + n; x++) {
            for (int z = ez - 1; z <= ez + n; z++) {
                boolean ring = onRing(x, z, ex, ez, n);
                w.getBlockAt(x, ey - 1, z).setType(ring ? Material.OAK_PLANKS : Material.HAY_BLOCK, false);
                for (int y = ey; y <= ey + 2; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
                if (!ring) continue;

                Block post = w.getBlockAt(x, ey, z);
                if (x == gateX && z == gateZ) {
                    Gate gate = (Gate) Material.OAK_FENCE_GATE.createBlockData();
                    gate.setFacing(BlockFace.SOUTH);
                    post.setBlockData(gate, false);
                } else {
                    Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
                    for (BlockFace f : SIDES) {
                        if (onRing(x + f.getModX(), z + f.getModZ(), ex, ez, n)) fence.setFace(f, true);
                    }
                    post.setBlockData(fence, false);
                }
            }
        }
        int[][] corners = {{ex - 1, ez - 1}, {ex + n, ez - 1}, {ex - 1, ez + n}, {ex + n, ez + n}};
        for (int[] c : corners) w.getBlockAt(c[0], ey + 1, c[1]).setType(Material.LANTERN, false);
        return original;
    }

    private static boolean onRing(int x, int z, int ex, int ez, int n) {
        boolean inBox = x >= ex - 1 && x <= ex + n && z >= ez - 1 && z <= ez + n;
        return inBox && (x == ex - 1 || x == ex + n || z == ez - 1 || z == ez + n);
    }

    public static void undo(Map<Location, BlockState> original) {
        for (BlockState s : original.values()) s.update(true, false);
    }
}
