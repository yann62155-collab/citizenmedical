# Citizen Médical (citizenmedical)

Mod NeoForge **21.1.215** / Minecraft **1.21.1** / Java 21 pour le serveur RP Citizen. Fonctionne avec **Citizen Economy** (optionnel au démarrage, nécessaire pour les médecins).

## Fonctionnement
- **Blessure** : un coup d'au moins 2 ❤ (joueur, monstre, flèche, explosion) ou une chute d'au moins 2 ❤ a 50 % de chances de blesser. Le joueur **boite immédiatement** (-35 % de vitesse, ne peut plus courir).
- **Sans bandage** : -1 ❤ toutes les 5 minutes.
- **Avec bandage** : -½ ❤ toutes les 10 minutes. Bandage = 1 laine + 1 ficelle → 2 bandages. Clic droit pour se bander, clic droit sur un joueur pour le bander.
- **Médecin** : tout patron / gérant / employé d'une entreprise **ACTIVE** dont l'emblème est **HOP** dans Citizen Economy (ou dont le matricule est listé dans la config). Touche **Alt** → curseur → **clic droit sur un joueur** = fiche d'examen avec le bouton **Soigner** (à moins de 6 blocs). Joueurs console : **accroupi + clic droit main vide** sur le joueur (fiche dans le chat avec [Soigner]).
- **Soin** : le saignement s'arrête. La boiterie s'arrête seulement quand le joueur a été soigné **et** que 10 minutes se sont écoulées depuis la blessure.
- **À terre** : à la mort, le joueur ne meurt pas : il reste allongé 1 min 30 (`secondesATerre`). Un joueur peut le **porter** (accroupi + clic droit main vide) puis le **déposer sur un lit d'hôpital** (clic droit sur le lit) ; rester accroupi 1 s ou `/medical poser` le pose au sol. Le compte à rebours est en pause pendant le portage. À la fin du compte à rebours (ou s'il se déconnecte), il est transporté sur le lit d'hôpital le plus proche. Il se relève à 100 % de vie mais **boite** (blessure grave bandée) jusqu'au soin d'un médecin. `/kill` et le vide tuent vraiment.
- **Lit d'hôpital** (2 laines blanches + 1 bandage + 3 lingots de fer) : clic droit = santé à 100 %. Seuls les médecins (et OP) peuvent le poser. `/medical hopital` (OP) définit un point de secours si aucun lit n'est posé. Les compteurs n'avancent que si le joueur est connecté.
- Seuls les médecins **embauchés** soignent (les OP aussi doivent être embauchés ; option `opsSontMedecins`). En `/cadmin on`, la touche Alt redevient le curseur admin.

## Commandes
| Commande | Qui |
|---|---|
| `/medical` | votre état |
| `/medical examiner <joueur>` | médecin |
| `/medical soigner <joueur>` | médecin embauché, à côté du joueur |
| `/medical blesser <joueur> [cause]` | OP |
| `/medical guerir <joueur>` | OP (efface tout) |
| `/medical relever <joueur>` | OP (relève un joueur à terre) |
| `/medical hopital` | OP (point d'hôpital de secours) |
| `/medical poser` | poser le blessé que l'on porte |
| `/medical recharger` | OP (relit la config) |

## Configuration
`config/citizenmedical.json` (créé au premier lancement) : chance de blessure, dégâts minimum, pertes et intervalles, saignement mortel ou non, durée minimum de boiterie, ralentissement, distances, emblèmes / matricules d'hôpital (et mots-clés optionnels, vides par défaut), OP = médecins.

## Compilation
GitHub Actions (`.github/workflows/build.yml`) : chaque push sur `main` compile et publie le `.jar` en Release. En local : `./gradlew build`.
