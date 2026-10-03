package fr.simon.findthepoulet.skin;

import org.bukkit.inventory.ItemStack;

/**
 * Un déguisement : un objet affiché sur la tête du poulet.
 *
 * @param scale     taille de l'objet (1 = un bloc)
 * @param offsetY   hauteur du centre de l'objet, depuis les pattes du poulet
 * @param forward   décalage vers l'avant (la tête du poulet est devant son corps)
 * @param rotation  rotation en degrés autour de l'axe vertical (180 pour retourner l'objet)
 * @param modelY    centre vertical du modèle (0 pour un bloc, -0.25 pour une tête de joueur)
 */
public record ChickenSkin(String key, String name, ItemStack item,
                          float scale, float offsetY, float forward, float rotation, float modelY) {

    public ItemStack item() {
        return item.clone();
    }
}
