# Joe's Addons for ABMC — Mod Intro

> In a nutshell: a mod built to bring ABMC (Alan Becker's Minecraft) to life in your world. The heart of it is the **Staff** weapon system, plus new dimensions, a whole potion lineup, bosses and critters — with a bit of Touhou Little Maid integration on the side.

- Runs on: **NeoForge**
- Game version: **Minecraft 1.21.1**
- Current version: **v5.4.47**
- What it is: a gameplay-enhancement / fun-pack for the ABMC vibe

---

## 1. The Core Loop

### The Staff — One Stick, All the Tools

The Staff is where it all starts. Hold it in your main hand along with the matching block and press **middle-click** to swap its form (the block gets "installed" into the Staff with a poof of particles). Each form comes with its own fixed stat card — attack damage/speed, move speed, reach, and so on.

#### How to Get One

- **Beat every vanilla achievement** (hidden ones count; recipe-type ones don't) → you get one, once.
- Already had all achievements before installing? You'll get it handed to you on login.
- Or flip a config option to start with one right away.
- **Creative tab** "Joes Addons for ABMC" — grab the empty Staff or any form directly.
- **Commands**:

```
/give @s joes_addons_for_abmc:staff
/give @s joes_addons_for_abmc:staff[joes_addons_for_abmc:blocktype="gold_block"]
```

#### The Controls

Stuck on a Staff? Just start mashing these:

> **Left-click · Middle-click · Right-click · Left Alt · Mouse wheel** (each Staff uses them differently)

#### Durability

- The Staff and each installed block each carry **100,000** durability (turn on F3+H to see it).
- Durability always hits the **block's bar first**; only once that's empty does overflow chip at the Staff itself.
- Block durability plays nice with **Unbreaking**, and **Mending** repairs it with XP.
- Every form's block durability is its **own** — switching forms doesn't mix them up.

#### Enchanting

The Staff takes **any** enchantment, and the `/enchant` command lets you crank the level up to 99.

#### The 28 Forms

**Passive forms (do their thing just by holding them)**:

- **Gold Block Staff** — a heavy hammer that can shatter obsidian.
- **Diamond Block Staff** — big-melee hammer, hits like a truck.
- **Netherite Block Staff** — shatters most survival-breakable blocks, with silly-long knockback (ignores knockback resistance) and huge melee damage.
- **Bell Staff** — whack enemies to stack a bunch of debuffs; hit players get ringing ears (which mutes everything else — turn it off in config).
- **Anvil Staff** — two words: a burden. You really don't want to be carrying this.

**Active forms (hit a button, get an ability)**:

- **Obsidian Staff** — terrain sculptor: yank blocks up and fling them somewhere.
- **Bedrock Staff** — terrain sculptor: right-click the ground and make it fly! (or tunnel straight down).
- **Lapis Block Staff** — burn XP to fly, or "enchant" blocks so you can grab and move them (and mobs).
- **Barrier Staff** — the ultimate shield, and it can break through anything.
- **Ice Staff** — freeze enemies solid in frosted ice so they can't move; smash the ice early to hurt them.
- **Magma Block Staff** — lobs blaze/ghast fireballs packing Fire Aspect X; sneak+right-click lifts a mob up and drops magma under its feet.
- **Enchanting Table Staff** — slaps **enchantment status effects** on mobs (or on the tool in their hand), with a Crazy mode (level locked at 99).
- **HIM Staff** — melee hits for the target's current health; has a ranged mode that hucks heads.
- **Command Block Staff** — right-click for a two-choice menu (text commands / full-on custom graphic programming), plus grab & shield modes and more.
- **Portal Staff** — drops a pair of portals (two-way teleport); left-click to collapse them — and if two get too cozy together, whatever's in between gets booted into the Physics Dimension.
- **Redstone Block Staff** — fires a laser that powers up redstone at range and zaps mobs.
- **Cobweb Staff** — shoots webs to grab things, seals mobs in cobweb prisons, and can even nullify other Staffs for a bit.
- **Chain Staff** — disarm enemies, or reel them in.
- **TNT Staff** — tosses lit TNT or creepers (yes, a lit creeper can blow you up too).
- **Furnace Staff** — smelts dropped items, or charges and speeds up furnace blocks.
- **Omega Staff** — "code-kills" mobs by absorbing them.
- **Bone Block Staff** — super bonemeal that grows/duplicates tons of plants.
- **Spawner Staff** — summons, assembles, and bosses mobs around.
- **Dripstone Block Staff** — grows dripstone spikes into the world.
- **Cauldron Staff** — a potion shield system (soak up / throw potions, costs zero durability while it's active).
- **Lightning Rod Staff** — call down lightning (even indoors/clear skies) and de-rust nearby copper.
- **Brewing Stand Staff** — hurls potions / leaves lingering clouds.

### Enchantment Status Effects

These are the **enchantment status effects** the Enchanting Table Staff can apply to mobs — each one mimics a vanilla enchantment's special trick (Sharpness, Power, Breach, Channeling, Infinity… 41 in total). Bonus: a mob holding a tool can "pretend" it's enchanted — e.g. a mob with Power shoots arrows that hit harder.

---

## 2. 🌌 Three New Worlds

### 1. Lucky Dimension

A gentle "Lucky Plains" noise world with a chill vibe — safe, free, and made for exploring. Get there through the **Lucky Portal**: build a gold-block frame with a 2×2 water source in the middle, toss a **dropper** into the water, and after a beat lightning strikes to light the portal.

### 2. Physics Dimension

A near-empty flat void that exists to break the rules: entities **drift at a constant speed with no gravity**, you can't breathe, firing projectiles **kicks you backward**, and falling off the world doesn't hurt. There's no normal portal — enter via the **Portal Staff** (bring two portals too close, or collapse them), and there's a return portal waiting inside.

### 3. Note Block Universe

Same terrain as the vanilla Overworld, but trees grow **note blocks** instead of trunks — a whole music-themed parallel world. It has its own situational soundtrack (village / cave / wandering trader / mountaintop), and monsters are forced to play nice as "neutralized".

- Hop between worlds via the **Note Portal** (a note-block frame ignited with fire), coordinates at an **8:1** ratio.

---

## 3. The Potions

### Transmutation Potion

- **Step 1 – Pre-Transmutation Potion**: **Water bottle + Nether Wart + Soul Sand** (any order).
- **Step 2 – Transmutation Potion**: **Pre-Transmutation Potion + any item / block / spawn egg / named name tag** (comes in splash and lingering forms).
- Effect: a splash turns the target into an **item / block / other mob / player shell** (the Creative tab version turns the target into a random item/block).
- Morph into a mob and you inherit its **special tricks** (flight, slow fall, projectile attacks, bonus hit effects…).

### Transmutation Antidote

- Brew: **Water bottle + Soul Sand + Fermented Spider Eye** (splash variant available).
- Effect: reverts a transmuted block/item/mob/player shell **back into the original mob**; a transmuted player can also just drink it to uncringe.

### Awakening Potion

- Brew: **Haunted Potion (Water Bottle + Soul Sand) + Carved Pumpkin** → splash Awakening Potion; **splash Awakening Potion + Redstone** → splash extended Awakening Potion (no drinkable form).
- Effect: wakes up nearby whitelisted blocks (chests, brewing stands, note blocks, …) into **awakened blocks**, forming rigs that auto-brew and top up potions.

### Teleportation Potion

- First — **Haunted Potion**: **Water Bottle + Soul Sand**.
- Step 1 – **Pre-Teleportation Potion**: **Haunted Potion + Ender Pearl**.
- Step 2 – **Teleportation Potion**: **Pre-Teleportation Potion + Nether Wart** (the wart's **custom name** picks the type).
- Name it: three numbers → teleport to that spot; a valid UUID → to that entity; a single number "X" → X blocks along the throw; anything else → somewhere random.
- Effect: on impact it spawns a one-way portal pair that carts the hit target off to the destination.

> Heads-up: exact recipes follow the code (check the individual potion pages); this intro is just the gist.

---

## 4. Bosses & Critters

- **Witch Boss**: a gnarly boss that squats in a witch hut, has 10× health, and never despawns. It runs a **three-stage (mob → block → item)** fight built on the **Transmutation Potion** system. Kill it the violent way and you get **full transmutation immunity**; beat it the clever way and you unlock **unlimited brewing** (Redstone/Glowstone stacking with no cap).
- **Enchanted Origami Crane**: a hostile little paper crane born from **using shears on an enchanting table** (draining its charge). It picks a random melee or ranged mode and fires bullets that ignite or explode; you can also catch it in a glass bottle.
- **HIM Head**: the projectile from **HIM Staff**'s ranged mode — lightning fast, deals **true damage**, and blows up on impact (yes, it can even bust through bedrock).

---

## 5. New Weapons *(WIP)*

- **Giant Netherite Sword** — huge damage, 7100 durability, swings like a mountain-cracker.
- **Giant Netherite Axe** — the same heavyweight axe; hard-hitting and tough.
- **Prismarine Bow + Prismarine Arrow** — a bow with a beefier draw.
- **Glistering Melon Knife** — barely any durability, but it doubles as a Totem of Undying.
- **Netherite Core** — a crafting material used as a core part.

---

## 6. World Touch: Shipwreck Drowned

Shipwrecks now run a **scheduled spawn** mechanic to spice up ocean exploring: drowned (including fully-geared drowned leaders) keep showing up around the wreck.

---

## 7. Touhou Little Maid Cross-over

Registers a dedicated job — **"Staff Attack"** — for **Touhou Little Maid**, so maids can swing your Staffs too.
No Touhou Little Maid installed? The add-on simply doesn't load and leaves the mod untouched.

---

## 8. Notes & Thanks

- Thanks to **Alan Becker's AVM (Animation vs. Minecraft)** for the inspiration.
- This doc is the short version; exact numbers and details are whatever the game actually does (the Chinese Wiki breaks down each feature).
- All the ideas and requirements come from **RedZombie**; **DeepSeek** did the code, the research, and the docs.
- Got a bug or an idea? Ping the author.