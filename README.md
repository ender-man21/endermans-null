# Enderman's Null

Fabric mod for Minecraft 26.3 (client + server) that adds the **Null warriors** from the Unstable SMP lore:
an army bound to a **Totem of Null** and commanded with the **Banner of Null**.

## Features
- **Null Warrior**: a black-skinned humanoid with enchanted netherite gear (Flow trim), a random gibberish name,
  30 HP. It follows its owner, defends them and regenerates when left alone (faster near the owner).
- **Totem of Null** is a vanilla Totem of Undying enchanted with Curse of Vanishing (canon).
  Hold it and Shift + hold Right Click for 3 seconds to summon a warrior (5 levels, army limit 20).
  Admin command: `/null totem give [player]`.
- **Ranks (from the lore):** Soldier (netherite trim), General (redstone trim, name like `_9eKbKoC7tiq_`; every 10th
  warrior is a General) and a single Commander per totem (quartz trim, name `________`, summoned with its own button
  in the banner menu, the first summon costs 15 levels, then he can be toggled on and off for free anywhere; if he dies,
  the next summon costs levels again; he does not count towards the army limit). The commander has three modes, chosen from the banner menu (crown icon,
  bottom left): **Lead** (fights with the army, wields a mace, blinks above targets and smashes down), **Survey**
  (invisible and vulnerable, highlights enemies only for you) and **Protect** (guards you on his own and takes a would-be fatal blow
  for you, before a totem would pop).
- **The army is bound to the totem.** Pass the totem to another player and the army obeys the new owner.
  If the totem disappears from the world (used, cursed away, destroyed), the army perishes.
- **Banner of Null** (black banner + ender eye + stick): hold Right Click for the radial menu, release to confirm.
  - Formation: circle, square, wedge, line, column. Behavior: passive, neutral, aggressive, raid, pacifist
    (pacifist warriors take damage but never fight back, handy for duels).
  - Follow, hold position, wander, **follow cursor** (the army runs to where you look, up to 100 blocks), and distance (scroll, 3-16 blocks).
  - **Call Null**: ender pearls fall where you look and the army teleports there (30 s cooldown).
  - Shift + Right Click plants a standing flag: the army guards that point; only the owner can take it back.
  - Left click on an entity orders the army to attack it.
- Commands: `/null follow|hold|guard|cursor`; admin: `/null summon`, `/null totem give`, `/null debug [reset]`.

## Config
`config/endermans-null.json` is created on the first launch (restart to apply changes): army limit, summon costs,
General quota, cursor range, Call Null cooldown, whether Raid attacks players, target search range, Protect and
Survey ranges, whether a surveying commander is invulnerable, and the commander smash cooldown.

## Planned
Army squads and several flags, a boss structure in the Nether.

## Building
Java 25, see `gradle.properties`. `./gradlew build`.

## License
All Rights Reserved, see the `LICENSE` file.
