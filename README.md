# Bricks & Beats (Fabric, Minecraft 26.2)

Build: Java 25 + Gradle 9.x (`gradle wrapper` once if there is no gradlew), then `./gradlew build`
-> `build/libs/bricks_and_beats-1.0.0.jar`. Needs Fabric Loader >= 0.19.1 and Fabric API 0.152.1+26.2.

* Music discs: 5 discs (recipe: note block + 2 black dye + iron/gold/lapis/blaze powder/diamond), or
  `/give @s bricks_and_beats:music_disc_big_shot`
* Dynamite in furnace: Shift + right-click a plain furnace holding TNT.
* Bricks: right-click with a brick / nether brick to throw it. 4 shards -> 1 brick (2x2 recipe).

If `ThrowableItemProjectile` fails to import, change the package in ThrownBrick.java / BrickShard.java to
`net.minecraft.world.entity.projectile.ThrowableItemProjectile` (the IDE can auto-import it).
