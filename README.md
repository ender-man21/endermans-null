# Enderman's Null

Fabric mod for Minecraft 26.3 (client + server) that adds the **Null warriors** from the Unstable SMP lore:
an army bound to a **Totem of Null** and commanded with the **Banner of Null**.

## Features
- **Null Warrior**: a black-skinned humanoid with enchanted netherite gear (Flow trim), a random gibberish name,
  30 HP. It follows its owner, defends them and regenerates when left alone (faster near the owner).
- **Totem of Null** is a vanilla Totem of Undying enchanted with Curse of Vanishing (canon).
  Hold it and Shift + hold Right Click for 3 seconds to summon a warrior (5 levels, army limit 20).
  Admin command: `/null totem give [player]`.
- **The army is bound to the totem.** Pass the totem to another player and the army obeys the new owner.
  If the totem disappears from the world (used, cursed away, destroyed), the army perishes.
- **Banner of Null** (black banner + ender eye + stick): hold Right Click for the radial menu, release to confirm.
  - Formation: circle, square, wedge. Behavior: passive, neutral, aggressive, raid.
  - Follow, hold position, wander, and distance (scroll, 3-16 blocks).
  - **Call Null**: ender pearls fall where you look and the army teleports there (30 s cooldown).
  - Shift + Right Click plants a standing flag: the army guards that point; only the owner can take it back.
  - Left click on an entity orders the army to attack it.
- Commands: `/null follow|hold|guard`; admin: `/null summon`, `/null totem give`, `/null debug [reset]`.

## Planned
Army squads and several flags, ranks, a boss structure in the Nether, config.

## Building
Java 25, see `gradle.properties`. `./gradlew build`.

## License
All Rights Reserved, see the `LICENSE` file.
