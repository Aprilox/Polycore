# Guide de test — Polycore 0.5.0

Polycore 0.5.0 existe pour Minecraft 1.18.2 → 26.3, sur NeoForge, Fabric et Forge : 43 jars, un par loader et par
plage de versions. Le tableau jar → versions est dans `deliverable/README.md`, les jars eux-mêmes dans
`deliverable/` (ou sur CurseForge / Modrinth).

## Lancer

1. Installer Minecraft et le loader voulu pour la version testée (NeoForge, Forge, ou Fabric Loader + **Fabric API**).
   Java : 17 jusqu'à 1.20.4, 21 de 1.20.5 à 1.21.11, 25 pour 26.x.
2. Copier le jar Polycore de cette version dans `mods/` :
   - du **serveur** (serveur dédié, ou rien de plus en solo) : toutes les optimisations ;
   - du **client** : menu (touche **K**), HUD et notifications en plus.
3. Démarrer. Le journal du serveur ne montre aucune exception Polycore (la plupart des jars écrivent aussi une ligne
   `Hooks applied: N`, le nombre de points du jeu où Polycore s'est accroché).

## Commandes et touches

| Touche / commande | Effet |
|---|---|
| **K** | Ouvre le menu Polycore (client avec le mod) |
| `/polycore` (`/pc`) | Menu ; menu en coffre si le client n'a pas le mod ; résumé texte dans la console |
| `/polycore bench 8` | Banc A/B : chunks/s sans puis avec Polycore (monde de test) |
| `/polycore hash 4` | Empreinte de parité des blocs autour du joueur |
| `/polycore pregen start 32` · `stop` · `status` | Prégénération autour du joueur |
| `/polycore lag analyze 30` | Analyse détaillée du lag |
| `/polycore toggle <id> false` · `/polycore reload` | Coupe une optimisation ; relit `config/polycore.json` |

Liste complète : section *Commands* du `README.md`.

## À tester à la main

Cocher pour chaque loader au moins une version (idéalement la plus ancienne et la plus récente de chaque jar).

- [ ] **Serveur seul** : le serveur démarre et s'arrête (`/stop`) sans erreur ; `/polycore` dans la console affiche le
      résumé ; un joueur **sans** Polycore se connecte, et `/polycore` (opérateur) ouvre le menu en coffre.
- [ ] **Client + serveur** : la touche K ouvre le menu ; pages *This game* et *Server* (tableau de bord, Optimizations,
      TPS Guard, Tools, Reports, Settings) lisibles en 1920×1080 **et** dans une petite fenêtre ; un réglage changé dans
      le menu est gardé après redémarrage.
- [ ] **HUD** : activé dans les préférences, il affiche TPS, chunks/s et temps de tick en haut à gauche.
- [ ] **Client Polycore sur un serveur sans Polycore** : connexion normale, les pages serveur expliquent que le serveur
      n'a pas le mod.
- [ ] **Solo** : nouveau monde, menu complet modifiable sans triches.
- [ ] **Parité vanilla** (procédure *Checking vanilla parity* du README) : même graine, optimisations allumées puis
      éteintes, `/polycore hash 4` au même endroit → les empreintes *terrain* sont identiques.
- [ ] **Banc** : `/polycore bench 8` dans un monde de test → rapport dans `polycore-reports/`, gain affiché.
- [ ] **Prégénération** : `/polycore pregen start 32` va au bout ; le mode « Keep TPS stable » ralentit quand le
      serveur rame.
- [ ] **Analyseur de lag et TPS Guard** : beaucoup d'entités (ex. 500 poulets) → l'analyse détaillée les liste avec
      leurs coordonnées, la téléportation fonctionne ; le TPS Guard active ses mesures puis les retire quand ça va mieux.
- [ ] **Mauvaise version** : un jar posé sur une version hors de sa plage est refusé proprement par le loader
      (message clair, pas de crash au milieu du jeu).
- [ ] **Fabric sans Fabric API** : le loader refuse de démarrer et nomme la dépendance manquante.
- [ ] **Autre mod d'optimisation** (ex. un mod de génération de chunks) : l'optimisation concernée reste éteinte et
      le menu nomme le mod et la méthode en conflit.
- [ ] **Pages CurseForge et Modrinth** : chaque fichier affiche le bon loader, les bonnes versions et le type Release.

## Point connu

Sur **Forge 1.18.2**, un client *vanilla* lancé directement sur l'adresse du serveur (`--server`, connexion pendant le
chargement des modèles) peut planter : c'est un bug du client vanilla 1.18.2, reproduit sans Polycore. Se connecter
depuis le menu Multijoueur l'évite.

## Tests automatiques (développeurs)

Dans le dossier d'une branche : `./gradlew build` puis les game tests (`:neoforge:runGameTestServer`,
`:forge:runGameTestServer`, `:fabric:runGametest`) : au moins 11 tests doivent passer sur chaque loader. La CI GitHub
(`.github/workflows/build.yml`) construit chaque loader à chaque push.
