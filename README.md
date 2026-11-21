# Bricks & Beats (Fabric, Minecraft 26.2)

Build: Java 25 + Gradle 9.x (`gradle wrapper` once if there is no gradlew), then `./gradlew build`
-> `build/libs/bricks_and_beats-1.0.0.jar`. Needs Fabric Loader >= 0.19.1 and Fabric API 0.152.1+26.2.

* Music discs: 5 discs (recipe: note block + 2 black dye + iron/gold/lapis/blaze powder/diamond), or
  `/give @s bricks_and_beats:music_disc_big_shot`
* Dynamite in furnace: put TNT in the input slot of a plain furnace (with fuel). When it finishes cooking, the furnace explodes and gives nothing.
* Bricks: right-click with a brick / nether brick to throw it. 4 shards -> 1 brick (2x2 recipe).

If `ThrowableItemProjectile` fails to import, change the package in ThrownBrick.java / BrickShard.java to
`net.minecraft.world.entity.projectile.ThrowableItemProjectile` (the IDE can auto-import it).

* Enchantment "Eternal Arrow" (bow / crossbow): arrows fly with NoGravity:1b (straight, no drop).
  `/enchant @s bricks_and_beats:eternal_arrow`; also appears in the enchanting table.

## v1.1 additions
* Enchantments (all appear in the enchanting table): Eternal Arrow (arrow never drops, speed is boosted back when it slows, max 8 s), Ink Shot (blindness), Musical Weapon (25% area damage, all weapons incl. bows), Chaos Strike (melee) / Far Chaos (bow, crossbow) - 20% random effect, Shield Breaker (15%), Amethyst Spray (crossbow shotgun, slow reload, knockback follows aim up/down, recoil throws the shooter back (shoot down = boost up, sneak = no recoil); incompatible with Quick Charge, Multishot, Eternal Arrow, Ultra Power), Ultra Power (bow/crossbow: pierces mobs, smashes weak blocks and targets, cooldown; incompatible with Quick Charge, Multishot, Amethyst Spray).
* Variant mobs (~12% of zombies/skeletons): Brick Thrower, Dynamiter, Bard Skeleton, Amethyst Shooter, Ink Skeleton.
* Effects/potions: Rhythm, Brick Skin, Fragility, Chaos (brew on an Awkward Potion with note block / brick / nether brick / amethyst shard).
* Creative tab "Bricks & Beats". Effect icons: textures/mob_effect/*.png (placeholders, replace them).
* If AbstractArrow fails to import, change the package in ArrowEnchants.java (IDE auto-import).

## v1.2: Hook (merged from REMAKE: Hook)
Items hook, hook_1..hook_5 (smithing: template chain, base = previous hook, addition chest / copper block / iron block / diamond / netherite ingot), hook entity, enchantments Hook Hands, Redstone Hook, Defensive Mechanism (+ vanilla Multishot, Quick Charge, Power work on hooks). See Hooks.java for rules.

## Build fixes (Minecraft 26.2)
* Entity tags are read through `TagUtil` (Entity#getTags no longer exists under that name).
* Zombie/skeleton detection uses the entity registry id instead of EntityType constants.
* Ultra Power piercing is now the `minecraft:projectile_piercing` effect in enchantment/ultra_power.json (AbstractArrow#setPierceLevel is private).
* Brewing recipes are added by `mixin/PotionBrewingMixin` (no Fabric API brewing class needed).
* Creative tab builder is looked up at runtime, so a Fabric API class rename cannot break compilation.

## Hook visuals
Hooks are now a 3D grapple gun with chain and copper claw in hand / on the ground (models/item/hook_gun*.json, one texture per level);
the inventory and item frames keep the flat icons. The flying claw is the hidden item `hook_head`.
If the gun looks wrongly held, tweak the `display` block of models/item/hook_gun.json (or edit it in Blockbench).

## v1.3
* Amethyst Spray: pellets fly the instant the shot is fired, every pellet damages (invulnerability frames are reset), pellets also carry Ink Shot / Far Chaos / Musical Weapon of the weapon.
* Ultra Power: now looks ahead of the arrow and smashes blocks of hardness <= 3 (stone, wood, ores, bricks...), up to 12 per arrow; obsidian and bedrock stay.
* Thrown brick / nether brick have their own 3D model (assets/items/thrown_*.json, models/item/thrown_*.json). The renderer always faces the camera, so the model is shown from a fixed angle.
* Necromancer zombie (1 in 100 zombies): every 12 s calls 3 zombies out of the ground in a triangle. Drops the Necromancer's Staff with 33.3%. The staff does the same for the player (friendly zombies, 2 min life, 40 s cooldown).
* Block-breaking zombies: Breaker (hardness <= 2.5), Miner (<= 4.5), Ram (<= 6, 3 blocks high), Digger (<= 1.6, digs down). They ignore the mobGriefing gamerule.
* Other variants: Bulwark Zombie (heavy armor + shield), Frost Skeleton, Fire Skeleton. All have spawn eggs in the creative tab.
* Redrawn textures: shards, hook icons, hook gun sheets, effect icons (discs untouched). Hook gun model got extra parts (sights, vents, energy cell, stock).

## v1.4: tiered staffs
Wooden / stone / iron / golden / diamond / netherite staff (right click; recipe: 2 material + amethyst shard + 2 sticks). Attacks: thorn bolt (poison), thrown rock (knockback), shockwave (area), holy light (heals you, burns monsters), diamond beam (pierces), netherite wrath (lightning + area). Cooldown = the attack's: 1.5 / 2.5 / 4 / 5 / 6 / 20 s; a miss recharges twice as fast.

## v1.5
* Hook: gun orientation flipped (the muzzle no longer points at your face; if it is still wrong, change the Y rotation sign in `display` of models/item/hook_gun_*.json). Every upgrade level has its own claw/chain texture (hook_gun_N.png) and the flying claw uses it (hook_head_N). Real 3D claw and interlocking chain links are drawn between you and the claw (display entities, see Fx.java).
* Mobs (all zombies/skeletons) holding a hook shoot it at their target and pull it in. New variants: Hooker Zombie, Hooker Skeleton.
* Zombies holding a tool dig the blocks that tool is good at when those block their way (MobAbilities.java). Ignores mobGriefing like the other digging zombies.
* Staffs: new textures, the colored stripe on the shaft is gone.
* Thrown bricks are a real 3D spinning brick (item_display).
* Hand Piston (`bricks_and_beats:hand_piston`) + enchantments Piston Power, Sticky Piston, Jet Recoil. No recoil while sneaking.
* Spawn eggs for plain zombie / plain skeleton (no variant roll), hooker zombie / skeleton.
* Tipped arrows / splash / lingering potions of the mod's effects are translated.
