package fr.simon.findthepoulet.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enregistre l'état d'origine de chaque bloc modifié pendant une partie,
 * puis remet l'arène exactement comme avant (y compris le contenu des coffres).
 */
public final class BlockRestorer {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final Region region;
    private final Map<Location, BlockState> original = new LinkedHashMap<>();

    public BlockRestorer(Region region) {
        this.region = region;
    }

    public void record(Block b) {
        if (b == null || !region.contains(b)) return;
        Location l = b.getLocation();
        if (!original.containsKey(l)) original.put(l, b.getState());
    }

    public void recordState(BlockState state) {
        if (state == null || !region.contains(state.getLocation())) return;
        original.putIfAbsent(state.getLocation(), state);
    }

    /** Le bloc + ses 6 voisins (torches, portes, plantes qui tombent avec le bloc...). */
    public void recordAround(Block b) {
        if (b == null) return;
        record(b);
        for (BlockFace f : FACES) record(b.getRelative(f));
    }

    public int size() {
        return original.size();
    }

    public void restore() {
        List<BlockState> states = new ArrayList<>(original.values());
        for (BlockState s : states) s.update(true, false);
        original.clear();
    }
}
