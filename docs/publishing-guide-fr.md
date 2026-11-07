# Publier Polycore sur CurseForge et Modrinth

## Pourquoi l'app CurseForge ne « détecte » pas encore le mod

L'app CurseForge reconnaît un mod en comparant l'empreinte du fichier `.jar` avec les fichiers publiés sur CurseForge. Un jar compilé chez toi n'est donc pas reconnu tant que **ce fichier exact** n'est pas publié sur ton projet CurseForge. Une fois publié, l'app l'affiche avec le logo, la description et les mises à jour. Modrinth fonctionne pareil (empreinte du fichier).

Dans le jeu, la liste des mods affiche déjà le logo, l'auteur et la description : ils sont dans le jar.

## Fichiers prêts

| Fichier | Usage |
|---|---|
| `branding/icon/polycore-icon-400.png` | Icône du projet CurseForge (carré, 400×400 minimum) |
| `branding/icon/polycore-icon-512.png` | Icône du projet Modrinth |
| `branding/banner/polycore-banner.png` | Bannière en haut de la description (1200 px de large, fond transparent) |
| `docs/curseforge-description.md` | Description du projet, en anglais, à coller |
| `neoforge/build/libs/polycore-neoforge-1.21.1-<version>.jar` | Le fichier à téléverser |

## CurseForge (authors.curseforge.com)

1. Crée un compte auteur, puis **Create Project** > Minecraft > **Mods**.
2. Nom : `Polycore`. Résumé : `Multicore optimization for Minecraft: parallel chunk generation with built-in benchmark and profiler.`
3. Catégorie principale : **Performance**. Secondaires possibles : Server Utility, Utility & QoL.
4. Icône : `branding/icon/polycore-icon-400.png`. Licence : All Rights Reserved (licence personnalisée, voir LICENSE).
5. Description : colle le contenu de `docs/curseforge-description.md`. Ajoute `branding/banner/polycore-banner.png` comme image en haut avec le bouton image de l'éditeur.
6. Une fois le projet validé par CurseForge (quelques heures à quelques jours), **Upload File** :
   - fichier : `neoforge/build/libs/polycore-neoforge-1.21.1-<version>.jar`
   - type : **Alpha** pour cette première version
   - versions de jeu : **1.21.1**, chargeur **NeoForge**, environnement **Client** et **Server**
   - changelog : `First alpha: dedicated worldgen pool, parallel pre-generation, stage profiler, A/B benchmark, parity hash, in-game menu (K).`

## Modrinth (modrinth.com)

1. **Create a project** > Mod, nom `Polycore`, URL `polycore` si libre.
2. Icône : `branding/icon/polycore-icon-512.png`, description : `docs/curseforge-description.md`, catégorie **Optimization**, licence personnalisée (Custom, texte de LICENSE).
3. Environnement : client **optionnel**, serveur **requis**.
4. **Upload version** : le même jar, chargeur NeoForge, version 1.21.1, canal **Alpha**.

## Captures d'écran (galerie CurseForge et Modrinth)

Les captures du menu sont dans `branding/screenshots/` : 1920×1080, sans métadonnées, une par page du menu
(`menu-server-dashboard.png`, `menu-server-lag.png`, `menu-server-profiler.png`, `menu-server-tools.png`,
`menu-server-guard.png`, `menu-server-optimizations.png`, `menu-server-reports.png`, `menu-server-settings.png`,
`menu-client-overview.png`, `menu-client-settings.png`, `menu-about.png`) et `hud-in-game.png` (HUD en jeu).
Elles conviennent telles quelles à la galerie CurseForge (onglet *Images*) et à celle de Modrinth (*Gallery*,
« featured » : `menu-server-dashboard.png`). `docs/curseforge-description.md` les affiche déjà.
Elles sont prises par un vrai client de jeu (NeoForge 1.21.1, serveur dédié, joueur opérateur, juste après un
benchmark A/B) : à refaire quand le menu change.

## Publication automatique (Gradle)

`gradle/publish.gradle` (Mod Publish Plugin) est appliqué à chaque loader. Il envoie le jar du loader avec :
nom « Polycore <version> (<Loader> <Minecraft>) », version Modrinth `<version>+<loader>-<minecraft>`, versions de
jeu `<loader>_publish_versions` et type `publish_release_type` (`gradle.properties`), Java `java_version`, client +
serveur, Fabric API requise pour Fabric, changelog = section `## <version>` de `CHANGELOG.md`.

- Voir sans rien envoyer : `gradlew :fabric:publishMods -PdryRun` (affiche fichier, nom, tags et changelog).
- Envoyer : `gradlew :fabric:publishCurseforge` puis `gradlew :fabric:publishModrinth` (une plateforme à la fois).
- Clés : variables `CURSEFORGE_TOKEN` et `MODRINTH_TOKEN`, sinon `DEV_FILES/curseforge.local.properties`
  (`curseforge.uploadToken` ou `curseforge.token`, `modrinth.token`, ids des projets). L'envoi CurseForge demande un
  **jeton d'auteur** (authors.curseforge.com > API Tokens, format 8-4-4-4-12) : une clé « Core API »
  (console.curseforge.com, commence par `$2a$`) ne sert qu'à lire.
- Ne jamais renvoyer un fichier déjà publié (même nom ou même contenu) : vérifier d'abord la page Files du projet.

## Avant chaque nouvelle version

1. Monte `mod_version` dans `gradle.properties` (ex. `0.1.1`).
2. `gradlew.bat build`, teste le jar dans le jeu.
3. Publie le nouveau jar avec un changelog. Ne republie jamais un jar sous le même numéro de version.

Conseil : avant de publier, lance au moins un benchmark sur ta machine. Mets le résultat dans la description (avec ton processeur), ça donne confiance aux joueurs.
