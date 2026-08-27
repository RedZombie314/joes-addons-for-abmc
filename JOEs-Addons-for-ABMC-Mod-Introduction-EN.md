# Joe's Addons for ABMC — Mod Introduction

> One-sentence summary: A mod whose end goal is to faithfully recreate the entire world of ABMC (Alan Becker's Minecraft). Its main currently-implemented gameplay is the Staff weapon, with an initial integration with Touhou Little Maid.

- Platform: **NeoForge**
- Minecraft: **1.21.1**
- Current version: **v3.12.6**
- Positioning: Gameplay enhancement / fun integration pack for the ABMC environment

---

## I. Core Gameplay at a Glance

Only the **Staff** is currently fairly complete — everything else is **WIP (work in progress)** content.

### The Staff — One Staff to Rule Them All

The Staff is the soul item of this mod. Hold the Staff in your main hand together with the corresponding block, then **middle-click** to switch its form (the block is installed into the Staff with a particle effect), activating a different set of abilities. After switching, the Staff locks in a fixed attribute profile (attack damage / speed, movement speed, reach, etc.).

The Staff can be obtained by **completing all vanilla achievements**. More acquisition methods will be added later.

#### Universal Hotkeys

If you are unsure how to use a particular Staff, try these keys — more tutorials are planned (potentially via Patchouli integration):

> **Left-click · Middle-click · Right-click · Left Alt · R key · Mouse wheel**

#### Staff Forms (currently working)

- **Gold Block Staff** — a heavy-hitting hammer-type staff that can shatter obsidian.
- **Netherite Block Staff** — a hammer-type staff that can shatter most survival-breakable blocks, with extremely long knockback and massive melee damage.
- **Obsidian Staff** *(WIP)* — a terrain shaper: right-click terrain to push it in a direction.
- **Bedrock Staff** — a terrain shaper: right-click terrain to make it fly! (Burrowing underground works too.)
- **Lapis Block Staff** — consumes experience to grant the player flight, or to "enchant" blocks with experience in order to move them.
- **Barrier Staff** — absolute defense; the barrier shield you create can break through anything!
- **Anvil Staff** — two words: a burden. You do *not* want to be carrying this.
- **Bell Staff** — a hammer-type staff that applies a series of debuffs when hitting enemies; players hit also hear ringing in their ears.
- **Ice Staff** — creates frosted ice to freeze enemies, robbing them of movement; breaking the ice early damages the enemy.
- **Magma Staff** — fires blaze fireballs or ghast fireballs, which carry Fire Aspect X.
- **Enchanting Table Staff** — can enchant items held by mobs, or enchant the mobs themselves.
- **HIM Staff** — melee attacks deal damage equal to the target's current health; has a ranged thrown-head mode.
- **Command Block Staff** *(WIP)* — grab mode pulls targets with left-click; can record command history, save/import presets, and display commands as text.
- **Portal Staff** — generates a portal at the targeted position with a preview entity, transporting the player there.
- **Redstone Block Staff** — fires a laser that can strongly power/light up redstone components and blocks at range, and can also damage mobs.
- **Cobweb Staff** — fires webs to become "the failed man", can seal entities with cobwebs, and can even temporarily nullify other Staves.
- **Chain Staff** — disarms enemies, or pulls targets toward you.
- **TNT Staff** — throws lit TNT or creepers forward.
- **Furnace Staff** — smelts dropped items, and can also charge furnace blocks.
- **Omega Staff** *(WIP)* — can "code-kill" mobs via absorption.
- All other forms have no function yet and remain **WIP**.

---

## II. 🌌 Three New Dimensions

### 1. Lucky Dimension
- A gentle "Lucky Plains" noise-terrain world with a relaxed atmosphere — a safe, free space to explore.

### 2. Physics Dimension
- A nearly empty flat void, built for "physics rules". Experience unique gravity/movement rules here (great for experiments or custom physics gameplay).

### 3. Note Block Universe
- **Identical terrain to the vanilla Overworld**, but tree trunks are **replaced with note blocks** during generation, creating a music-themed parallel world.
- Has its own music contexts: high altitude, villages, underwater, and near wandering traders switch to different themed background music.
- The AI of villagers/iron golems/creepers etc. is specially adjusted here, and monsters are forced to be "neutralized" — better suited for relaxed sightseeing and building.
- Travel between the Overworld and the Note Block Universe via the **Note Portal** (coordinates converted at an 8:1 ratio, following vanilla portal logic).

---

## III. Transmutation Potions & Antidote *(WIP)*

#### Brewing Recipes

- Water Bottle + Nether Wart + Soul Sand (or Soul Sand + Nether Wart) → **Pre-Transmutation Potion**
- Pre-Transmutation Potion + any item/block/spawn egg/named name tag → **Transmutation Potion** (with splash and lingering variants)
- Water Bottle + Soul Sand + Fermented Spider Eye → **Transmutation Antidote** (with splash variant)

#### Effects

- **Transmutation Potion**: turns splashed mobs into items / blocks / mobs / player shells. (Potions from the Creative inventory turn the target into a random item/block.)
- **Transmutation Antidote**: restores transmuted blocks / items / mobs / player shells back into living mobs.

---

## IV. New Weapons *(WIP)*

- **Giant Netherite Sword** — extremely high damage, 7100 durability, swings like splitting mountains.
- **Giant Netherite Axe** — the same heavyweight axe; high damage and durability.
- **Prismarine Bow + Prismarine Arrow** — a bow with more draw strength.
- **Glistering Melon Knife** — very low durability, but can be used like a Totem of Undying.
- **Netherite Core** — an intermediate material used as a core component in crafting.

---

## V. World Enhancement: Shipwreck Drowned Spawning *(WIP)*

A timed mob-spawning mechanic added to shipwreck structures to make ocean exploration more challenging:

- Drowned periodically spawn around shipwrecks. *(Currently buggy — being fixed.)*

---

## VI. Touhou Little Maid Integration *(WIP)*

- Registers a dedicated job task — **"Staff Attack"** — for **Touhou Little Maid**, letting maids fight with this mod's Staffs (currently only melee Staffs, plus the two special Redstone Block and HIM Head staffs).
- If Touhou Little Maid is not installed, this extension is simply not loaded and does not affect the mod's normal operation.

---

## VII. Notes & Acknowledgements

- Thanks to **Alan Becker's AVM (Animation vs. Minecraft)** series for inspiring this mod.
- This document is a concise overview of the mod's features; exact values and details are subject to in-game behavior.
- All gameplay ideas and requirements for this mod were proposed and decided by **RedZombie**; **DeepSeek** handled the code implementation, data organization, and documentation.

> For issues or feedback, feel free to contact the author.
