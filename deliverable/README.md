# Polycore 0.5.0 — livraison

Ce dossier contient les **43 jars Polycore 0.5.0** publiés sur CurseForge et Modrinth : un jar par loader et par plage
de versions de Minecraft, de 1.18.2 à 26.3. Ce sont les fichiers téléchargés depuis Modrinth, octet pour octet
(sha1 contrôlé par l'API, liste dans `SHA1SUMS.txt`). Les jars ne sont pas suivis par git : on les retrouve sur
<https://modrinth.com/mod/polycore/versions> et <https://www.curseforge.com/minecraft/mc-mods/polycore/files>.

Installation : copier **le seul** jar qui correspond au loader et à la version de Minecraft dans le dossier `mods/`
du serveur (toutes les optimisations) et, si l'on veut le menu (touche K) et le HUD, dans celui du client. Fabric
demande aussi **Fabric API**. Tester à la main : voir `../TEST_GUIDE.md`.

| Minecraft | NeoForge | Fabric (+ Fabric API) | Forge | Java |
|---|---|---|---|---|
| 26.3 | `polycore-neoforge-26.3-0.5.0.jar` | `polycore-fabric-26.3-0.5.0.jar` | `polycore-forge-26.3-0.5.0.jar` | 25 |
| 26.2 | `polycore-neoforge-26.2-0.5.0.jar` | `polycore-fabric-26.2-0.5.0.jar` | `polycore-forge-26.2-0.5.0.jar` | 25 |
| 26.1 – 26.1.2 | `polycore-neoforge-26.1-26.1.2-0.5.0.jar` | `polycore-fabric-26.1-26.1.2-0.5.0.jar` | `polycore-forge-26.1-26.1.2-0.5.0.jar` | 25 |
| 1.21.11 | `polycore-neoforge-1.21.11-0.5.0.jar` | `polycore-fabric-1.21.11-0.5.0.jar` | `polycore-forge-1.21.11-0.5.0.jar` | 21 |
| 1.21.9 – 1.21.10 | `polycore-neoforge-1.21.9-1.21.10-0.5.0.jar` | `polycore-fabric-1.21.9-1.21.10-0.5.0.jar` | `polycore-forge-1.21.10-0.5.0.jar` (1.21.10) | 21 |
| 1.21.6 – 1.21.8 | `polycore-neoforge-1.21.7-1.21.8-0.5.0.jar` (1.21.7 – 1.21.8) | `polycore-fabric-1.21.6-1.21.8-0.5.0.jar` | `polycore-forge-1.21.8-0.5.0.jar` (1.21.8) | 21 |
| 1.21.5 | `polycore-neoforge-1.21.5-0.5.0.jar` | `polycore-fabric-1.21.5-0.5.0.jar` | `polycore-forge-1.21.5-0.5.0.jar` | 21 |
| 1.21.2 – 1.21.4 | `polycore-neoforge-1.21.4-0.5.0.jar` | `polycore-fabric-1.21.4-0.5.0.jar` | `polycore-forge-1.21.4-0.5.0.jar` (1.21.3 – 1.21.4) | 21 |
| 1.21 – 1.21.1 | `polycore-neoforge-1.21-1.21.1-0.5.0.jar` | `polycore-fabric-1.21.1-0.5.0.jar` | `polycore-forge-1.21.1-0.5.0.jar` (1.21.1) | 21 |
| 1.20.5 – 1.20.6 | `polycore-neoforge-1.20.5-1.20.6-0.5.0.jar` | `polycore-fabric-1.20.5-1.20.6-0.5.0.jar` | `polycore-forge-1.20.6-0.5.0.jar` (1.20.6) | 21 |
| 1.20.2 – 1.20.4 | `polycore-neoforge-1.20.4-0.5.0.jar` (1.20.4) | `polycore-fabric-1.20.2-1.20.4-0.5.0.jar` | `polycore-forge-1.20.2-1.20.4-0.5.0.jar` (1.20.2, 1.20.4) | 17 |
| 1.20 – 1.20.1 | — | `polycore-fabric-1.20.1-0.5.0.jar` | `polycore-forge-1.20.1-0.5.0.jar` | 17 |
| 1.19.4 | — | `polycore-fabric-1.19.4-0.5.0.jar` | `polycore-forge-1.19.4-0.5.0.jar` | 17 |
| 1.19.3 | — | `polycore-fabric-1.19.3-0.5.0.jar` | `polycore-forge-1.19.3-0.5.0.jar` | 17 |
| 1.19 – 1.19.2 | — | `polycore-fabric-1.19-1.19.2-0.5.0.jar` | `polycore-forge-1.19.1-1.19.2-0.5.0.jar` (1.19.1 – 1.19.2) | 17 |
| 1.18.2 | — | `polycore-fabric-1.18.2-0.5.0.jar` | `polycore-forge-1.18.2-0.5.0.jar` | 17 |

Une version entre parenthèses est la partie de la ligne couverte par ce jar ; « — » : NeoForge n'existe pas pour ces
versions. Les versions absentes (1.18, 1.18.1, Forge 1.19, et certaines versions d'un loader) et leur raison sont dans la
section *Versions and branches* du `README.md` principal. Fabric Loader minimum : 0.14.21 jusqu'à 1.20.4, 0.16.0
ensuite.

Contrôler les fichiers (Git Bash ou Linux) : `cd deliverable && sha1sum -c SHA1SUMS.txt`.
