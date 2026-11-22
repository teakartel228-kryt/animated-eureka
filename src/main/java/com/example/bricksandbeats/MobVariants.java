package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Special zombies / skeletons (about 12% of natural ones, plus 1% of zombies are necromancers).
 * Zombies: brick, dynamite, necro, breaker, miner, ram, digger, bulwark, hooker, hammer, acid, runner, plague.
 * Skeletons: bard, amethyst, ink, frost, fire, hooker, venom, sniper, storm.
 * Power tiers (tags ba_tier_2 / ba_tier_3, tier 1 = no tag): more health, damage, armor and faster / stronger abilities.
 * Helper tags: ba_rise (rising from the ground), ba_life (timed life), ba_minion (friendly to players).
 */
public final class MobVariants {
	private static final String NECRO = "ba_mob_necro";
	private static final String[] ZOMBIE = {"ba_mob_brick", "ba_mob_dynamite", "ba_mob_breaker", "ba_mob_miner",
			"ba_mob_ram", "ba_mob_digger", "ba_mob_bulwark", "ba_mob_hookz", "ba_mob_hammer", "ba_mob_acid", "ba_mob_runner", "ba_mob_plague",
			"ba_mob_big", "ba_mob_smart", "ba_mob_giant", "ba_mob_shop", "ba_mob_fisher"};
	private static final String[] SKELETON = {"ba_mob_bard", "ba_mob_amethyst", "ba_mob_ink", "ba_mob_frost", "ba_mob_fire", "ba_mob_hooks", "ba_mob_venom", "ba_mob_sniper", "ba_mob_storm"};

	private static final Map<Mob, String> VARIANT = new IdentityHashMap<>();
	/** Sub-types a stage zombie can roll, with the stage that unlocks each. */
	private static final Object[][] UNLOCK = {{"ba_mob_runner", 2}, {"ba_mob_brick", 2}, {"ba_mob_digger", 3}, {"ba_mob_plague", 3},
			{"ba_mob_acid", 4}, {"ba_mob_dynamite", 4}, {"ba_mob_breaker", 5}, {"ba_mob_fisher", 5}, {"ba_mob_hookz", 5},
			{"ba_mob_bulwark", 6}, {"ba_mob_miner", 6}, {"ba_mob_smart", 6}, {"ba_mob_hammer", 7}, {"ba_mob_ram", 8},
			{"ba_mob_big", 8}, {"ba_mob_necro", 9}};
	private static final Map<Mob, ItemStack[]> SMART = new IdentityHashMap<>();
	private static final Map<Mob, Integer> FUSES = new IdentityHashMap<>();
	private static final Map<Mob, Integer> RISE = new IdentityHashMap<>();
	private static final Map<Mob, Integer> LIFE = new IdentityHashMap<>();
	private static final Set<Mob> MINIONS = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Acid zombies: the block each one is currently dissolving and how far along it is. */
	private static final Map<Mob, BlockPos> ACID_POS = new IdentityHashMap<>();
	private static final Map<Mob, Float> ACID_PROGRESS = new IdentityHashMap<>();

	/** Power tier of a variant mob: 1 (weak, no tag), 2 (strong), 3 (elite). */
	public static int tierOf(Mob m) {
		int st = Stages.stageOf(m);
		if (st > 0) return st <= 5 ? 1 : st <= 10 ? 2 : 3;
		return TagUtil.has(m, "ba_tier_3") ? 3 : TagUtil.has(m, "ba_tier_2") ? 2 : 1;
	}

	/** Natural spawns: 70 % tier 1, 24 % tier 2, 6 % tier 3. */
	private static void rollTier(Mob mob, net.minecraft.util.RandomSource r) {
		float x = r.nextFloat();
		if (x < 0.06F) mob.addTag("ba_tier_3");
		else if (x < 0.30F) mob.addTag("ba_tier_2");
	}

	/** Ability cooldown shrinks with the tier: 100 % / 75 % / 60 % of the base. */
	private static int cd(int base, int tier) {
		return Math.max(10, base * 3 / (tier + 2));
	}

	/** Sub-type of a freshly spawned stage zombie (null = plain stage zombie). */
	private static String rollStageVariant(int stage, net.minecraft.util.RandomSource r) {
		if (stage >= Stages.MAX && r.nextFloat() < 0.01F) return "ba_mob_giant";
		if (r.nextFloat() >= 0.10F + 0.02F * stage) return null;
		List<String> pool = new ArrayList<>();
		for (Object[] u : UNLOCK) if ((Integer) u[1] <= stage) pool.add((String) u[0]);
		return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
	}

	private static String roman(int tier) {
		return tier == 3 ? "III" : tier == 2 ? "II" : "I";
	}

	/** Hook level a hooker mob carries: tier 1 plain hook, tier 2 level 2, tier 3 level 4. */
	private static ItemStack hookFor(int tier) {
		return new ItemStack(HookRegistry.HOOKS[tier == 3 ? 4 : tier == 2 ? 2 : 0]);
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (!(e instanceof Mob mob)) return;
			String type = typeName(mob);
			boolean zombie = type.equals("zombie");
			boolean skeleton = type.equals("skeleton");
			if (!zombie && !skeleton) return;
			if (!TagUtil.has(mob, "ba_rolled")) {
				mob.addTag("ba_rolled");
				if (zombie) { // natural zombies get the world stage and maybe a sub-type
					int stage = Stages.naturalStage(level);
					mob.addTag("ba_stage_" + stage);
					String v = rollStageVariant(stage, level.getRandom());
					if (v != null) mob.addTag(v);
				} else if (level.getRandom().nextFloat() < 0.12F) {
					mob.addTag(SKELETON[level.getRandom().nextInt(SKELETON.length)]);
					rollTier(mob, level.getRandom());
				}
			}
			// Variant tag comes from the random roll above or from a spawn egg (see VariantEggs).
			int stage = zombie ? Stages.read(mob) : 0;
			if (stage > 0) Stages.register(mob, stage);
			String variant = variantTag(mob);
			if (variant == null && stage > 0 && !TagUtil.has(mob, "ba_setup")) {
				mob.addTag("ba_setup");
				Stages.setup(mob, stage);
			}
			if (variant != null) {
				if (!TagUtil.has(mob, "ba_setup")) {
					mob.addTag("ba_setup");
					setup(mob, variant);
				}
				VARIANT.put(mob, variant);
			}
			if (zombie) {
				if (TagUtil.has(mob, "ba_rise") && !TagUtil.has(mob, "ba_rose")) {
					mob.noPhysics = true;
					RISE.put(mob, 0);
				}
				if (TagUtil.has(mob, "ba_minion")) MINIONS.add(mob);
				if (TagUtil.has(mob, "ba_life")) LIFE.put(mob, MINIONS.contains(mob) ? 2400 : 1200);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				VARIANT.remove(m);
				FUSES.remove(m);
				RISE.remove(m);
				LIFE.remove(m);
				MINIONS.remove(m);
				ACID_POS.remove(m);
				ACID_PROGRESS.remove(m);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());

		// The necromancer's staff drops with a 33.3% chance (instead of vanilla hand-item drop chance).
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Mob mob && NECRO.equals(VARIANT.get(mob)) && mob.level() instanceof ServerLevel level) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				if (level.getRandom().nextFloat() < 1.0F / 3.0F) {
					ItemEntity drop = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(ModItems.NECROMANCER_STAFF));
					level.addFreshEntity(drop);
				}
			}
			return true;
		});
		// The boss giant drops treasure.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Mob mob && "ba_mob_giant".equals(VARIANT.get(mob)) && mob.level() instanceof ServerLevel level) {
				dropStack(level, mob, new ItemStack(Items.NETHERITE_INGOT, 3));
				dropStack(level, mob, new ItemStack(Items.DIAMOND, 12));
				dropStack(level, mob, new ItemStack(Items.GOLDEN_APPLE, 3));
				dropStack(level, mob, new ItemStack(ModItems.SCYTHES[2], 1));
			}
			return true;
		});
		// Zombie shop: right click with an emerald to buy something.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Mob m) || !"ba_mob_shop".equals(VARIANT.get(m))) return InteractionResult.PASS;
			if (!(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(Items.EMERALD)) {
				player.displayClientMessage(Component.translatable("message.bricks_and_beats.shop_need_emerald"), true);
				return InteractionResult.SUCCESS;
			}
			held.consume(1, player);
			ItemStack reward = shopGoods(level.getRandom());
			if (!player.getInventory().add(reward)) player.drop(reward, false);
			level.playSound(null, m.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.6F);
			return InteractionResult.SUCCESS;
		});
		// Staff-summoned zombies never hurt players.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof Player && source.getEntity() instanceof Mob m && MINIONS.contains(m)));
	}

	/** True for the zombies that already have their own block-breaking logic (breaker / miner / ram / digger / hammer / acid). */
	public static boolean isBlockBreaker(Mob m) {
		String v = VARIANT.get(m);
		return v != null && (v.equals("ba_mob_breaker") || v.equals("ba_mob_miner") || v.equals("ba_mob_ram") || v.equals("ba_mob_digger")
				|| v.equals("ba_mob_hammer") || v.equals("ba_mob_acid"));
	}

	public static boolean isMinion(net.minecraft.world.entity.Entity e) {
		return e instanceof Mob m && MINIONS.contains(m);
	}

	/** Plain registry path of the mob's type ("zombie", "skeleton"...), independent of EntityType constants. */
	private static String typeName(Mob mob) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : "";
	}

	private static String variantTag(Mob mob) {
		if (TagUtil.has(mob, NECRO)) return NECRO;
		for (String t : ZOMBIE) if (TagUtil.has(mob, t)) return t;
		for (String t : SKELETON) if (TagUtil.has(mob, t)) return t;
		return null;
	}

	private static void name(Mob mob, String key) {
		int tier = tierOf(mob);
		Component n = Component.translatable("mob.bricks_and_beats." + key);
		int st = Stages.stageOf(mob);
		mob.setCustomName(st > 0 ? n.copy().append(" [" + st + "]") : tier > 1 ? n.copy().append(" " + roman(tier)) : n);
	}

	private static void setup(Mob mob, String tag) {
		mob.setCustomNameVisible(true);
		var hp = mob.getAttribute(Attributes.MAX_HEALTH);
		switch (tag) {
			case "ba_mob_brick" -> {
				name(mob, "brick_thrower");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			}
			case "ba_mob_dynamite" -> {
				name(mob, "dynamiter");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
			}
			case "ba_mob_bard" -> {
				name(mob, "bard");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NOTE_BLOCK));
				hp.setBaseValue(30);
			}
			case "ba_mob_amethyst" -> {
				name(mob, "amethyst_shooter");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.AMETHYST_BLOCK));
				hp.setBaseValue(26);
			}
			case "ba_mob_ink" -> {
				name(mob, "ink_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.INK_SAC));
				hp.setBaseValue(26);
			}
			case "ba_mob_frost" -> {
				name(mob, "frost_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.BLUE_ICE));
				hp.setBaseValue(26);
			}
			case "ba_mob_fire" -> {
				name(mob, "fire_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.MAGMA_BLOCK));
				mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1));
				hp.setBaseValue(26);
			}
			case NECRO -> {
				name(mob, "necromancer");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.NECROMANCER_STAFF));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));
				hp.setBaseValue(40);
			}
			case "ba_mob_breaker" -> {
				name(mob, "breaker");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
				hp.setBaseValue(26);
			}
			case "ba_mob_miner" -> {
				name(mob, "miner");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				hp.setBaseValue(30);
			}
			case "ba_mob_ram" -> {
				name(mob, "ram");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ANVIL));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
				hp.setBaseValue(50);
			}
			case "ba_mob_digger" -> {
				name(mob, "digger");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
				hp.setBaseValue(24);
			}
			case "ba_mob_hookz" -> {
				name(mob, "hooker_zombie");
				mob.setItemSlot(EquipmentSlot.MAINHAND, hookFor(tierOf(mob)));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				hp.setBaseValue(24);
			}
			case "ba_mob_hooks" -> {
				name(mob, "hooker_skeleton");
				mob.setItemSlot(EquipmentSlot.MAINHAND, hookFor(tierOf(mob)));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
				hp.setBaseValue(24);
			}
			case "ba_mob_hammer" -> {
				name(mob, "hammer");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.4);
				hp.setBaseValue(36);
			}
			case "ba_mob_acid" -> {
				name(mob, "acid");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LIME_CONCRETE));
				hp.setBaseValue(26);
			}
			case "ba_mob_runner" -> {
				name(mob, "runner");
				mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
				hp.setBaseValue(16);
			}
			case "ba_mob_plague" -> {
				name(mob, "plague");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.BROWN_MUSHROOM_BLOCK));
				hp.setBaseValue(30);
			}
			case "ba_mob_venom" -> {
				name(mob, "venom_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SLIME_BLOCK));
				hp.setBaseValue(24);
			}
			case "ba_mob_sniper" -> {
				name(mob, "sniper_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TARGET));
				mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40);
				hp.setBaseValue(20);
			}
			case "ba_mob_storm" -> {
				name(mob, "storm_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LIGHTNING_ROD));
				hp.setBaseValue(28);
			}
			case "ba_mob_big" -> {
				name(mob, "big_zombie");
				mob.getAttribute(Attributes.SCALE).setBaseValue(1.8);
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
				mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
				hp.setBaseValue(70);
			}
			case "ba_mob_smart" -> {
				name(mob, "smart_zombie");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
				hp.setBaseValue(30);
			}
			case "ba_mob_giant" -> {
				name(mob, "giant_boss");
				mob.getAttribute(Attributes.SCALE).setBaseValue(5.0);
				mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16);
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.24);
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
				mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
				mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
				mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1));
				mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1));
				mob.setPersistenceRequired();
				hp.setBaseValue(600);
			}
			case "ba_mob_shop" -> {
				name(mob, "zombie_shop");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
				mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0);
				mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1));
				mob.setPersistenceRequired();
				hp.setBaseValue(30);
			}
			case "ba_mob_fisher" -> {
				name(mob, "kotorybka");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.COD));
				hp.setBaseValue(26);
			}
			default -> { // bulwark
				name(mob, "bulwark");
				mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
				hp.setBaseValue(40);
			}
		}
		int tier = tierOf(mob);
		if (tier > 1) {
			hp.setBaseValue(hp.getBaseValue() * (tier == 2 ? 1.6 : 2.4));
			var atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
			if (atk != null) atk.setBaseValue(atk.getBaseValue() * (tier == 2 ? 1.4 : 1.9));
			if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(tier == 2 ? Items.IRON_HELMET : Items.DIAMOND_HELMET));
			}
			if (tier == 3 && mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			}
		}
		mob.setHealth((float) mob.getAttributeValue(Attributes.MAX_HEALTH));
	}

	// ------------------------------------------------------------------ necromancer / staff

	private static boolean solid(ServerLevel level, BlockPos p) {
		return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	private static double groundY(ServerLevel level, double x, double y, double z) {
		BlockPos p = BlockPos.containing(x, y, z);
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos feet = p.offset(0, dy, 0);
			if (solid(level, feet.below()) && !solid(level, feet) && !solid(level, feet.above())) return feet.getY();
		}
		return Double.NaN;
	}

	/** Three zombies climb out of the ground in a triangle, 3.5 blocks ahead of (dx, dz). friendly = they fight for the player. */
	public static void summonTriangle(ServerLevel level, Vec3 origin, double dx, double dz, boolean friendly) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3) {
			dx = 1;
			dz = 0;
			len = 1;
		}
		dx /= len;
		dz /= len;
		double cx = origin.x + dx * 3.5;
		double cz = origin.z + dz * 3.5;
		double base = Math.atan2(dz, dx);
		String tags = "\"ba_rolled\",\"ba_rise\",\"ba_life\"" + (friendly ? ",\"ba_minion\"" : "");
		int spawned = 0;
		for (int i = 0; i < 3; i++) {
			double ang = base + i * (2.0 * Math.PI / 3.0);
			double x = cx + Math.cos(ang) * 2.0;
			double z = cz + Math.sin(ang) * 2.0;
			double gy = groundY(level, x, origin.y, z);
			if (Double.isNaN(gy)) continue;
			String cmd = String.format(Locale.ROOT,
					"summon minecraft:zombie %.2f %.2f %.2f {Tags:[%s],NoAI:1b,NoGravity:1b,Invulnerable:1b}", x, gy - 2.0, z, tags);
			var server = level.getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
					x, gy + 0.2, z, 14, 0.3, 0.1, 0.3, 0.05);
			spawned++;
		}
		if (spawned > 0) {
			level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.7F);
		}
	}

	// ------------------------------------------------------------------ block breakers

	private static void breakAhead(ServerLevel level, Mob mob, LivingEntity target, String v, int t) {
		float max;
		int interval;
		boolean tall = false;
		boolean down = false;
		switch (v) {
			case "ba_mob_breaker" -> {
				max = 2.5F;
				interval = 20;
			}
			case "ba_mob_miner" -> {
				max = 4.5F;
				interval = 12;
			}
			case "ba_mob_ram" -> {
				max = 6.0F;
				interval = 30;
				tall = true;
			}
			default -> { // digger
				max = 1.6F;
				interval = 8;
				down = true;
			}
		}
		int tier = tierOf(mob);
		max *= 1.0F + 0.3F * (tier - 1);
		interval = Math.max(4, cd(interval, tier));
		if (t % interval != 0) return;
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		BlockPos feet = mob.blockPosition();
		List<BlockPos> cand = new ArrayList<>();
		if (down && target.getY() < mob.getY() - 2.0) cand.add(feet.below()); // dig down towards the target
		if (dx * dx + dz * dz > 1.5) {
			Direction dir = Direction.getApproximateNearest(dx, 0, dz);
			cand.add(feet.relative(dir));
			cand.add(feet.above().relative(dir));
			if (tall) cand.add(feet.above(2).relative(dir));
		}
		for (BlockPos p : cand) {
			if (smash(level, mob, p, max) && !tall) break;
		}
	}

	private static boolean smash(ServerLevel level, Mob mob, BlockPos p, float maxHardness) {
		BlockState s = level.getBlockState(p);
		if (s.isAir() || !s.getFluidState().isEmpty()) return false;
		float h = s.getDestroySpeed(level, p);
		if (h < 0.0F || h > maxHardness) return false;
		mob.swing(InteractionHand.MAIN_HAND);
		level.destroyBlock(p, true, mob);
		return true;
	}

	/** Hammer zombie: crushes the weak blocks around it (hardness up to 2.5, more with the tier) and throws players into the air. */
	static void slam(ServerLevel level, Mob mob, int tier) {
		slam(level, mob, tier, 2);
	}

	static void slam(ServerLevel level, Mob mob, int tier, int r) {
		BlockPos c = mob.blockPosition();
		float max = 2.5F + 0.5F * (tier - 1);
		int broken = 0;
		for (int dx = -r; dx <= r && broken < 12 * r; dx++) {
			for (int dz = -r; dz <= r && broken < 12 * r; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					if (dx == 0 && dz == 0) continue;
					BlockPos p = c.offset(dx, dy, dz);
					BlockState st = level.getBlockState(p);
					if (st.isAir() || !st.getFluidState().isEmpty()) continue;
					float h = st.getDestroySpeed(level, p);
					if (h < 0.0F || h > max) continue;
					level.destroyBlock(p, true, mob);
					broken++;
				}
			}
		}
		mob.swing(InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.2, mob.getZ(), 25, 1.2, 0.1, 1.2, 0.05);
		level.playSound(null, c, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.6F);
		for (Player pl : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(r + 1.0, 1.0, r + 1.0))) {
			pl.hurtServer(level, level.damageSources().mobAttack(mob), 3.0F + 1.5F * tier);
			pl.push(0, 0.6 + 0.1 * tier, 0);
			pl.hurtMarked = true;
		}
	}

	/** Acid zombie: slowly dissolves the block in its way (crack animation, no drops). */
	private static void acid(ServerLevel level, Mob mob, LivingEntity target, int tier) {
		BlockPos cur = ACID_POS.get(mob);
		if (cur == null) {
			if (mob.tickCount % 10 != 0) return;
			double dx = target.getX() - mob.getX();
			double dz = target.getZ() - mob.getZ();
			if (dx * dx + dz * dz < 1.5) return;
			Direction dir = Direction.getApproximateNearest(dx, 0, dz);
			BlockPos feet = mob.blockPosition();
			for (BlockPos p : new BlockPos[]{feet.relative(dir), feet.above().relative(dir)}) {
				BlockState st = level.getBlockState(p);
				if (st.isAir() || !st.getFluidState().isEmpty()) continue;
				float h = st.getDestroySpeed(level, p);
				if (h < 0.0F || h > 3.5F) continue;
				cur = p;
				ACID_POS.put(mob, p);
				ACID_PROGRESS.put(mob, 0.0F);
				break;
			}
			if (cur == null) return;
		}
		BlockState st = level.getBlockState(cur);
		if (st.isAir() || mob.distanceToSqr(Vec3.atCenterOf(cur)) > 9.0) {
			level.destroyBlockProgress(mob.getId(), cur, -1);
			ACID_POS.remove(mob);
			ACID_PROGRESS.remove(mob);
			return;
		}
		float h = Math.max(0.3F, st.getDestroySpeed(level, cur));
		float prog = ACID_PROGRESS.getOrDefault(mob, 0.0F) + (0.35F * tier) / h / 20.0F;
		ACID_PROGRESS.put(mob, prog);
		if (mob.tickCount % 6 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()),
					cur.getX() + 0.5, cur.getY() + 0.5, cur.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
		}
		if (prog < 1.0F) {
			level.destroyBlockProgress(mob.getId(), cur, (int) (prog * 10.0F));
			return;
		}
		level.destroyBlockProgress(mob.getId(), cur, -1);
		level.destroyBlock(cur, false, mob);
		level.playSound(null, cur, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.4F);
		ACID_POS.remove(mob);
		ACID_PROGRESS.remove(mob);
	}

	// ------------------------------------------------------------------ tick

	private static void tick() {
		// Zombies climbing out of the ground (2 blocks over 1 second).
		for (Mob m : new ArrayList<>(RISE.keySet())) {
			if (m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				RISE.remove(m);
				continue;
			}
			int t = RISE.get(m) + 1;
			m.setPos(m.getX(), m.getY() + 0.1, m.getZ());
			m.setDeltaMovement(Vec3.ZERO);
			m.fallDistance = 0;
			if (t % 2 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
						m.getX(), m.getY() + (20 - t) * 0.1 + 0.2, m.getZ(), 5, 0.3, 0.1, 0.3, 0.05);
			}
			if (t >= 20) {
				RISE.remove(m);
				m.noPhysics = false;
				m.setNoAi(false);
				m.setNoGravity(false);
				m.setInvulnerable(false);
				m.removeTag("ba_rise");
				m.addTag("ba_rose");
			} else {
				RISE.put(m, t);
			}
		}

		// Timed zombies (summoned) crumble after a while.
		for (Mob m : new ArrayList<>(LIFE.keySet())) {
			if (m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				LIFE.remove(m);
				continue;
			}
			int left = LIFE.get(m) - 1;
			if (left <= 0) {
				level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1.0, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				LIFE.remove(m);
				m.discard();
			} else {
				LIFE.put(m, left);
			}
		}

		// Staff minions: never target players, hunt hostile mobs, ignore the sun.
		for (Mob m : new ArrayList<>(MINIONS)) {
			if (m.isRemoved() || !m.isAlive() || !(m.level() instanceof ServerLevel level)) continue;
			m.clearFire();
			if (m.getTarget() instanceof Player) m.setTarget(null);
			if (m.getTarget() == null && m.tickCount % 10 == 0 && !RISE.containsKey(m)) {
				Mob best = null;
				double bestD = Double.MAX_VALUE;
				for (Mob other : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(16),
						o -> o != m && o.isAlive() && o.getType().getCategory() == MobCategory.MONSTER && !MINIONS.contains(o))) {
					double d = m.distanceToSqr(other);
					if (d < bestD) {
						bestD = d;
						best = other;
					}
				}
				if (best != null) m.setTarget(best);
			}
		}

		for (Mob mob : new ArrayList<>(VARIANT.keySet())) {
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				VARIANT.remove(mob);
				FUSES.remove(mob);
				SMART.remove(mob);
				continue;
			}
			String v = VARIANT.get(mob);
			if ("ba_mob_shop".equals(v)) {
				mob.setTarget(null);
				mob.setAggressive(false);
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive() || RISE.containsKey(mob)) {
				FUSES.remove(mob);
				continue;
			}
			double dist = mob.distanceTo(target);
			int t = mob.tickCount;
			int tier = tierOf(mob);

			switch (v) {
				case "ba_mob_brick" -> {
					if (t % cd(40, tier) == 0 && dist > 3 && dist < 14) {
						ThrownBrick b = new ThrownBrick(level, mob, new ItemStack(Items.BRICK));
						Vec3 d = target.getEyePosition().subtract(b.position());
						b.shoot(d.x, d.y + dist * 0.12, d.z, 1.4F, 3.0F);
						level.addFreshEntity(b);
						level.playSound(null, mob.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0F, 0.6F);
					}
				}
				case "ba_mob_amethyst" -> {
					if (t % cd(70, tier) == 0 && dist < 12 && mob.hasLineOfSight(target)) {
						Vec3 from = mob.getEyePosition();
						AmethystPellet.spray(level, mob, from, target.getEyePosition().subtract(from), 6, 0.1, 1.6);
					}
				}
				case "ba_mob_ink" -> {
					if (t % cd(80, tier) == 0 && dist < 12 && mob.hasLineOfSight(target)) {
						target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40 + 20 * tier));
						level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getEyeY(), target.getZ(), 25, 0.3, 0.3, 0.3, 0.05);
					}
				}
				case "ba_mob_frost" -> {
					if (t % cd(60, tier) == 0 && dist < 12 && mob.hasLineOfSight(target)) {
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, tier > 1 ? 2 : 1));
						target.setTicksFrozen(200);
						level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getEyeY(), target.getZ(), 25, 0.4, 0.4, 0.4, 0.05);
						level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 1.8F);
					}
				}
				case "ba_mob_fire" -> {
					if (t % cd(80, tier) == 0 && dist < 14 && mob.hasLineOfSight(target)) {
						target.igniteForSeconds(2.0F + 2.0F * tier);
						level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.5, 0.3, 0.03);
						level.playSound(null, mob.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.0F);
					}
				}
				case "ba_mob_bard" -> {
					if (t % cd(60, tier) == 0 && dist < 8) {
						level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 2, mob.getZ(), 20, 3, 0.5, 3, 1.0);
						level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.7F);
						for (Player e : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(7))) {
							e.hurtServer(level, level.damageSources().sonicBoom(mob), 2.0F + tier);
							e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
						}
					}
				}
				case "ba_mob_dynamite" -> {
					Integer fuse = FUSES.get(mob);
					if (fuse == null && dist < 3) fuse = 0;
					if (fuse != null) {
						fuse++;
						FUSES.put(mob, fuse);
						level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 2.1, mob.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
						if (fuse == 1) level.playSound(null, mob.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5F, 0.8F);
						if (fuse >= 30) {
							level.explode(mob, mob.getX(), mob.getY(0.5), mob.getZ(), 2.5F + 0.8F * (tier - 1),
									tier >= 2 ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
							FUSES.remove(mob);
							mob.discard();
						}
					}
				}
				case NECRO -> {
					if (t % cd(240, tier) == cd(240, tier) / 2 && dist < 18 && mob.hasLineOfSight(target)) {
						summonTriangle(level, mob.position(), target.getX() - mob.getX(), target.getZ() - mob.getZ(), false);
					}
				}
				case "ba_mob_breaker", "ba_mob_miner", "ba_mob_ram", "ba_mob_digger" -> {
					if (dist < 32) breakAhead(level, mob, target, v, t);
				}
				case "ba_mob_hammer" -> {
					// ground slam: smashes the weak blocks around it and throws players up
					if (t % cd(60, tier) == 0 && dist < 5.0) slam(level, mob, tier);
				}
				case "ba_mob_acid" -> {
					if (dist < 32) acid(level, mob, target, tier);
				}
				case "ba_mob_runner" -> {
					// sudden leap at the target
					if (t % cd(80, tier) == 0 && dist > 4.0 && dist < 10.0 && mob.onGround()) {
						Vec3 d = target.position().subtract(mob.position());
						Vec3 f = new Vec3(d.x, 0, d.z).normalize().scale(0.55 + 0.1 * tier);
						mob.setDeltaMovement(f.x, 0.45, f.z);
						mob.hurtMarked = true;
					}
				}
				case "ba_mob_plague" -> {
					// rotten aura: hunger and weakness for players close by
					if (t % 40 == 0) {
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState()),
								mob.getX(), mob.getY() + 1.2, mob.getZ(), 18, 1.5, 0.6, 1.5, 0.05);
						for (Player pl : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(4 + tier))) {
							pl.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100 + 40 * tier, 1));
							pl.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100 + 40 * tier, tier > 1 ? 1 : 0));
						}
					}
				}
				case "ba_mob_venom" -> {
					if (t % cd(50, tier) == 0 && dist < 14 && mob.hasLineOfSight(target)) {
						target.addEffect(new MobEffectInstance(MobEffects.POISON, 80 + 40 * tier, tier > 1 ? 1 : 0));
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()),
								target.getX(), target.getEyeY(), target.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
						level.playSound(null, target.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 1.2F);
					}
				}
				case "ba_mob_sniper" -> {
					// slow, telegraphed, very accurate shot with a long range
					int period = cd(100, tier);
					if (dist > 6 && dist < 40 && mob.hasLineOfSight(target)) {
						if (t % period == period - 20) {
							level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 2.0F);
							level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
						} else if (t % period == 0) {
							Vec3 from = mob.getEyePosition();
							Vec3 to = target.getEyePosition();
							Vec3 step = to.subtract(from);
							int n = (int) Math.min(40, Math.ceil(step.length() * 1.5));
							for (int i = 1; i < n; i++) {
								Vec3 q = from.add(step.scale(i / (double) n));
								level.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
							}
							target.hurtServer(level, level.damageSources().mobAttack(mob), 6.0F + 3.0F * tier);
							level.playSound(null, mob.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 2.0F, 0.5F);
						}
					}
				}
				case "ba_mob_storm" -> {
					if (t % cd(90, tier) == 0 && dist < 20 && mob.hasLineOfSight(target)) {
						var server = level.getServer();
						String cmd = String.format(Locale.ROOT, "summon minecraft:lightning_bolt %.2f %.2f %.2f", target.getX(), target.getY(), target.getZ());
						server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
					}
				}
				case "ba_mob_big" -> {
					if (t % cd(100, tier) == 0 && dist < 6.0) slam(level, mob, tier);
				}
				case "ba_mob_giant" -> {
					if (t % 80 == 0 && dist < 9.0) slam(level, mob, 3, 5);
					if (t % 400 == 200 && dist < 24.0 && mob.hasLineOfSight(target)) {
						summonTriangle(level, mob.position(), target.getX() - mob.getX(), target.getZ() - mob.getZ(), false);
					}
					if (mob.getHealth() < mob.getMaxHealth() * 0.5F && t % 100 == 0) {
						mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, 1));
					}
				}
				case "ba_mob_smart" -> smartTool(level, mob, target);
				case "ba_mob_fisher" -> {
					if (t % cd(100, tier) == 0 && dist > 4.0 && dist < 16.0 && mob.hasLineOfSight(target)) {
						Vec3 from = mob.getEyePosition();
						Vec3 to = target.getEyePosition();
						Vec3 step = to.subtract(from);
						int n = (int) Math.min(30, Math.ceil(step.length() * 2.0));
						for (int i = 1; i < n; i++) {
							Vec3 q = from.add(step.scale(i / (double) n));
							level.sendParticles(ParticleTypes.FISHING, q.x, q.y, q.z, 1, 0, 0, 0, 0);
						}
						Vec3 pull = mob.position().subtract(target.position()).normalize().scale(0.9 + 0.1 * tier);
						target.push(pull.x, 0.35, pull.z);
						target.hurtMarked = true;
						level.playSound(null, mob.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.0F, 0.8F);
					}
				}
				default -> { // bulwark: plain tank, nothing to tick
				}
			}
		}
	}

	private static void dropStack(ServerLevel level, Mob mob, ItemStack stack) {
		level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack));
	}

	/** What the zombie shop sells for one emerald. */
	private static ItemStack shopGoods(net.minecraft.util.RandomSource r) {
		return switch (r.nextInt(14)) {
			case 0, 1 -> new ItemStack(Items.IRON_INGOT, 3);
			case 2 -> new ItemStack(Items.GOLD_INGOT, 2);
			case 3, 4 -> new ItemStack(Items.BREAD, 4);
			case 5 -> new ItemStack(Items.COAL, 8);
			case 6 -> new ItemStack(Items.ARROW, 8);
			case 7, 8 -> new ItemStack(ModItems.COPPER_ARROW, 6);
			case 9 -> new ItemStack(ModItems.PHANTOM_ARROW, 3);
			case 10 -> new ItemStack(ModItems.DAGGERS[0]);
			case 11 -> new ItemStack(Items.GOLDEN_APPLE);
			case 12 -> new ItemStack(Items.ENDER_PEARL);
			default -> new ItemStack(Items.DIAMOND);
		};
	}

	/** Smart zombie: carries a pickaxe, an axe and a shovel and swaps to the best one for the block in its way. */
	private static void smartTool(ServerLevel level, Mob mob, LivingEntity target) {
		if (mob.tickCount % 10 != 0 || !mob.horizontalCollision) return;
		ItemStack[] tools = SMART.computeIfAbsent(mob, k -> new ItemStack[]{mob.getMainHandItem().isEmpty() ? new ItemStack(Items.IRON_PICKAXE) : mob.getMainHandItem(),
				new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL)});
		Direction dir = Direction.getApproximateNearest(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
		BlockState s = level.getBlockState(mob.blockPosition().above().relative(dir));
		if (s.isAir()) s = level.getBlockState(mob.blockPosition().relative(dir));
		if (s.isAir()) return;
		ItemStack best = tools[0];
		for (ItemStack t : tools) {
			if (t.getDestroySpeed(s) > best.getDestroySpeed(s)) best = t;
		}
		if (best != mob.getMainHandItem()) mob.setItemSlot(EquipmentSlot.MAINHAND, best);
	}

	private MobVariants() {}
}
