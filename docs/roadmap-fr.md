# Polycore : feuille de route

Objectif : **ne jamais descendre sous 20 TPS** (un tick ≤ 50 ms), sans dégrader le jeu, pour remplacer
Adaptive Performance Tweaks (APT) sur le serveur « ALLER LA PITIE ».

## Constat

Ce qui consomme les 50 ms d'un tick sur un pack comme ALLER LA PITIE :

| Source | Couvert par Polycore aujourd'hui ? |
|---|---|
| Génération et chargement des chunks (exploration) | ✅ Oui : pool worldgen, surface/carvers en parallèle, pré-génération. Mesuré : MSPT 30 → 10 ms pendant la génération. |
| Machines Create, contraptions, physique (Create Aeronautics, Sable) | ❌ Non |
| Entités : mobs, IA, items au sol, apparitions | ❌ Non |
| Redstone, fermes | ❌ Non |

**APT** gagne des TPS en **faisant moins** : distance de vue et de simulation réduites, IA des mobs lointains
ralentie, apparitions limitées, chargement des chunks freiné (1 tick sur 2/4/8 sous charge). Efficace, mais
visible en jeu, et son frein sur les chunks va à l'encontre de Polycore.

**Polycore** gagne des TPS en **faisant la même chose plus vite** sur plusieurs cœurs.

Limite honnête : Minecraft fait tourner le monde (entités, machines) sur un seul thread. Paralléliser ça avec
Create corrompt les mondes (cf. MCMT). Aucun mod ne peut *garantir* 20 TPS dans tous les cas ; le but est de
s'en approcher au maximum et de réagir proprement quand ça ne suffit pas.

## Étape 1 : Analyseur de lag (priorité)

Mesurer avant d'optimiser. Le pack n'a même pas spark.

- Temps par tick décomposé : entités (par type), blocs actifs / block entities (par mod et par type),
  chunks (génération, chargement, sauvegarde), éclairage, réseau, reste.
- Classement « top 10 » des coupables, avec coordonnées (ex. : « contraption Create en 120, 64, -340 : 8 ms »).
- Historique : pics de lag horodatés, pour savoir ce qui s'est passé pendant une chute de TPS.
- Nouvelle page dans le menu (Serveur → Analyseur de lag), lisible par les non-op, rapport exportable.
- Coût de mesure minimal (échantillonnage), désactivable.

## Étape 2 : Sortir du thread principal ce qui peut l'être sans risque

- ~~Éclairage en parallèle~~ : abandonné (≈ 4 % du temps de génération, hors du tick, réécriture du moteur de lumière trop risquée).
- Sauvegarde / sérialisation des chunks hors du tick.
- Préparation des paquets de chunks envoyés aux joueurs.
- Bruit SIMD (SIMD_NOISE, prévu) pour accélérer encore la génération.
- Chaque optimisation : activable/désactivable dans le menu, vérifiée par le hash de parité et un benchmark.

## Étape 3 : TPS Guard (remplaçant doux d'APT)

S'active seulement quand le tick dépasse un seuil (ex. 45 ms), et revient à la normale tout seul.
Agit du moins visible au plus visible :

1. Ralentir la pré-génération et la génération de fond (déjà fait : mode « Keep TPS stable »).
2. Réduire la fréquence de l'IA des mobs **loin de tous les joueurs** (jamais à proximité).
3. Limiter les apparitions de mobs sous forte charge.
4. Fusionner items et orbes d'XP au sol.
5. Distance de simulation : **désactivé par défaut**, seulement si l'admin l'active.

**Ne touche jamais à la distance de vue** (ce qui gênait avec APT), sauf choix explicite de l'admin.
Tout ce que fait le Guard est affiché en direct dans le menu (quelle mesure est active, pourquoi, depuis quand).

## Étape 4 : Retirer APT

1. Mesurer le serveur avec l'analyseur (avec APT).
2. Retirer APT du client **et du serveur dédié** (APT agit côté serveur).
3. Activer le TPS Guard, re-mesurer dans les mêmes conditions, comparer les rapports.

## Plus tard

- Compatibilité multi-loader : Fabric, Forge (voir `docs/architecture.md`).
- Autres versions de Minecraft (une branche par version).
- Publication CurseForge / Modrinth automatique depuis les releases GitHub.
