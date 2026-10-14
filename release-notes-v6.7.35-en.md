# Joes Addons for ABMC 6.7.35

**Minecraft 1.21.1 · NeoForge 21.1.x** · works on client and dedicated server

This build rolls up everything since 5.7.0 — the Lucky Block, Orb of Luck possession and Lucky Selector systems are all included here for the first time.

## New: Lucky Block event framework
- `Lucky Block` and `Support Lucky Block` blocks, plus a full weighted event table (`LuckyEvents` / `LuckyEvent` / `LuckyEventCategory`).
- Dozens of outcomes, including: Gold Nugget Fountain Pig, chicken-riding rabbits, bat-riding snow golems, a big gold ingot payout, Golden Armored Horse, lucky cats / wolves / creepers / elder guardians, a witch riding a ghast, anvil rain, firework volleys, a reflecting shield, skeleton knights, a wishing well, pig towers and a pig-bait carrot.
- Items drawn from a Lucky Block can be flagged **once-only**, tracked per item stack.

## New: Orb of Luck and the Player Shell
- `Orb of Luck` takes over its owner: the player becomes a spectator while a `Player Shell` (rendered as the player) does the fighting — health bar, armor, dodge, drawn-out attack wind-ups, flight, mace dives, mid-air water-bucket saves, assimilation and revival, and ally-wither control.
- Full boss-style shell health bar, head orb and elytra layers on the client.

## New: Lucky Dimension
- `Lucky Dimension Block` portal into a dedicated lucky dimension with its own chunk renderer.
- Staged spawning: chunks no longer dump their payload immediately. Content materializes within **50 blocks** of a player and is retracted beyond **56 blocks** (hysteresis keeps it from flickering), with 2 slots per chunk.
- Mobs you have interacted with, structure mobs (Orb of Luck) and items *you* threw are never handled this way.

## New: Support Lucky Block
- Right-click a selector with content to open a player-name dropdown: mouse to open or pick, ↑/↓ to move, **Space to confirm**, Esc to cancel. The send only happens after you confirm.
- A lucky block is placed within **3 blocks** of the chosen player (replacing air only) and **breaks itself after 5 seconds**, releasing the stored item or mob.
- When one appears, one random possessed Player Shell within **50 blocks** of that player loses its AI for **2 seconds**.
- Single-player gets `Technoblade` / `Dream` debug entries; multiplayer shows who actually needs support.

## New: Lucky Selector
- `Lucky Selector` can be piloted by a player: it flies where you look, seeks and delivers content, and can be sent across dimensions.
- Work slots, target claims and its own wander/collect goals.

## New mobs
- `Beeper`, `BeeBoss` (with ranged and target goals), `Golden Armored Horse` (rideable, flyable), Gold Nugget Fountain Pig, firework pigs, pig-bait carrot, and the Dark Clone family.

## New items and tools
- `Blaze Staff`, `Iron Hook`, chain grab, `Explosive Arrow`, `Fire Charge Launcher`, `Origami in a Bottle` / Paper Crane, `Wings`, `Crafting Table Hat`, `Wart on a Stick`, staff GUI and baked models, and spawn eggs for the Lucky Selector, Orb of Luck, Player Shell, BeeBoss and Beeper.

## New structures and data
- Lucky Temple (structure + structure set), the `luck_roller_mini` structure, new advancements, recipes and item/block tags.

## Fixes
- **Witch Boss** no longer mines blocks or uses flint and steel on items while playing its death animation.
- **Possessed Player Shell** no longer eats food while dying (this covers both the "0 HP always wants an apple" path and a bite that was already in progress).
- **Golden Armored Horse** is immune to fall damage, and its Jade / language name is no longer a raw translation key.
- Fixed new Lucky Dimension chunks stacking every item on a single block.
- Fixed a Lucky Dimension chunk-render crash (out-of-range 3×3 chunk read).

## Installing
1. Minecraft 1.21.1 with NeoForge 21.1.x.
2. Drop `joes_addons_for_abmc-6.7.35.jar` into your `mods/` folder.
3. Existing worlds keep working. Old lucky-dimension leftovers clean themselves up; if you still see piled-up items, run `/kill @e[type=joes_addons_for_abmc:lucky_item]`.

> Restart the game after updating — the mod is not hot-swappable.
