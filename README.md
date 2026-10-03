# Find The Poulet 🐔

Mini-jeu pour **Paper 26.2** (Java 25) : un seul poulet apparaît quelque part dans l'arène (surface ou cavernes).
Clic droit pour l'attraper, ramène-le dans l'enclos du point de départ → victoire. 2 à 15 joueurs, parties de 20 min.

## Compiler
- **IntelliJ** : ouvrir le dossier (projet Gradle), JDK 25, puis tâche `build` → `build/libs/FindThePoulet-1.0.0.jar`
- **Ligne de commande** : `gradle build` (Gradle 9.1+, JDK 25)
- **GitHub** : pousser le projet sur un dépôt, l'action `Build` produit le jar (onglet Actions → Artifacts)

Mettre le jar dans `plugins/` et redémarrer le serveur.

## Outils admin
`/poulet admin` (ou bouton « Outils admin » de la tête de poulet) : créer une arène, zone lobby / spawn,
zones protégées, hologrammes de classement, spawn du serveur, lobby d'attente, recharger la config.

### Zone lobby / spawn protégée
Outil (bâton de blaze, `/poulet lobby`) : clic gauche = coin 1, clic droit = coin 2, clic droit dans l'air = menu → « Créer ».
Réglages par zone : monstres bloqués (pas d'apparition, retirés, ne ciblent pas), joueurs invincibles, pas de faim,
PvP désactivé, construction réservée aux admins, pas d'explosions ni de feu. Bouton « Spawn du serveur ici ».
`spawn.teleport-on-join` (config) : téléporte au spawn à la connexion.

## Créer une arène (op)
1. Tête de poulet → étoile **Créer une arène** (ou `/poulet creer`)
2. Écrire le nom dans le chat
3. **Bâton** : clic droit au sol → zone autour du point, contour en particules
4. Menu (clic droit dans l'air) : **taille 64 / 128 / 256 / 512 / 1024**, équipes, PvP, format (Classique / Manches), mode Renard, kit, déguisement
   (au-delà de 512, pré-génère la zone avec un plugin comme Chunky)
5. **Faux** : clic droit au sol → enclos 4×4 avec porte (à 4 blocs mini du bord)
6. **Valider l'arène** dans le menu (clic droit dans l'air avec un outil)

Gestion : clic droit sur une arène dans le menu (équipes, PvP, activer, tp, arrêter, supprimer).

## Déguisements du poulet 🎃
Sans pack de textures : un objet (bloc ou tête de joueur) est affiché sur la tête du poulet,
et sa variante vanilla (tempéré / chaud / froid) est tirée au hasard.
- Liste des skins : section `chicken-skin` de `config.yml` (citrouille, lanterne, pastèque, TNT, diamant, ruche + têtes de joueurs / têtes custom)
- Par arène (menu admin, bouton citrouille) : **Aléatoire**, **Aucun** ou un skin précis — clic droit pour un aperçu
- `/poulet skin <nom>` : poulet d'aperçu devant toi pendant 20 s
- Si un objet est mal placé ou à l'envers : ajuste `scale`, `offset-y`, `offset-forward` ou `rotation` (180) puis `/poulet reload`

## Flûte à poulet 🎶
Au début de chaque partie, 2 à 3 coffres par joueur sont cachés au hasard dans l'arène (surface ou cavernes),
chacun avec une **flûte à poulet** (apparence de bambou via `item_model`, sans pack de textures).
Clic droit : un petit air de flûte, puis le poulet caquette très fort **là où il est** → on entend d'où vient le son.
2 utilisations (barre de durabilité), puis elle casse. Les coffres disparaissent au reset de l'arène.
Réglages : section `flute` de `config.yml` · test : `/poulet flute` (admin).

## Gameplay
- **Le poulet fuit** les joueurs proches (sauf s'ils sont accroupis), puis s'essouffle quelques secondes.
- **Il pond des œufs** (non ramassables) toutes les 45 s : des indices pour le pister.
- **Traînée de plumes** derrière le porteur (particules + plumes au sol), sans ralentissement.
- **Anti-camping** : un joueur sans poulet qui reste 10 s près de l'enclos est repoussé et ralenti ;
  le porteur est intouchable 3 s en arrivant près de l'enclos.
- **Mort subite** : les 3 dernières minutes, le poulet brille à travers les murs et pond plus souvent.
- **Coffres cachés** : flûte à poulet, **boussole à poulet** (l'aiguille pointe vers lui 15 s, distance affichée)
  et **plume de saut** (bond en avant sans dégâts de chute, 3 utilisations).

## Modes (par arène, menu admin)
- **Format** : Classique (1er poulet ramené gagne) ou **Manches** (1ère équipe à 3 poulets, 15 min).
- **Mode Renard** (3 joueurs min.) : un joueur tiré au sort ne peut pas porter le poulet ; s'il frappe le porteur,
  celui-ci est éliminé (spectateur 45 s, ou jusqu'à la manche suivante). Le Renard gagne si personne ne ramène le poulet.
- **Kit de départ** : outils en bois, pain, torches (liste dans `config.yml`).

## Statistiques & classements
Victoires, parties, poulets attrapés, poulets ramenés, poulets tués, éliminations (Renard) → `stats.yml`.
- `/poulet stats [joueur]`, `/poulet classement` (ou bouton lingot d'or du menu)
- Hologrammes : `/poulet leaderboard victoires` (ou `parties`, `attrapes`, `livraisons`, `tues`, `eliminations`)
  à l'endroit où tu es ; `/poulet leaderboard supprimer` (le plus proche) ; `/poulet leaderboard liste`.

## Commandes
| Commande | Rôle |
|---|---|
| `/poulet` | menu |
| `/poulet pret` | se mettre prêt |
| `/poulet quitter` | quitter la partie |
| `/poulet rejoindre <arène>` / `liste` | rejoindre / lister |
| `/poulet creer [nom]` / `annuler` | création d'arène (admin) |
| `/poulet setlobby` / `setspawn` | lobby d'attente / spawn de fin de partie (admin) |
| `/poulet forcer [arène]` | lancer la partie même seul, sans attendre les « prêt » (admin) |
| `/poulet stop <arène>` / `supprimer <arène>` | admin |
| `/poulet stats [joueur]` / `classement` | statistiques / classements |
| `/poulet leaderboard <stat>` / `supprimer` / `liste` | hologrammes de classement (admin) |
| `/poulet flute` / `boussole` / `plume` | se donner un objet (admin) |
| `/poulet skin [nom]` / `reload` | aperçu d'un déguisement / recharger la config (admin) |

Permission admin : `findthepoulet.admin` (op par défaut). Réglages dans `config.yml`.
