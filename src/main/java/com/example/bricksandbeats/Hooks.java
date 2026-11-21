package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Container;

import java.util.*;

/**
 * Hook logic. Upgrade level (0..5) = which item: hook, hook_1..hook_5.
 *  0: damage 2, light mobs/players, falling blocks, projectiles   1: chests/barrels/shulkers
 *  2: players in copper/iron   3: breaks weak blocks, cracks bricks   4: longer, two hooks   5: heavy targets
 * Enchantments: Multishot (+1 hook), Quick Charge (shorter lockout), Power (pull speed),
 *  hook_hands, redstone_hook (1..5 charges), defensive_mechanism.
 */
public final class Hooks {
	static final class PS {
		final List<HookEntity> hooks = new ArrayList<>();
		long disabledUntil, emergencyUntil, lastFire;
		int chain, chainMax;
		boolean chainActive;
	}

	private record Temp(ServerLevel level, BlockPos pos, long expire) {}

	private static final Map<UUID, PS> STATE = new HashMap<>();
	private static final List<Temp> TEMP = new ArrayList<>();
	private static final Identifier HANDS_MOD = BricksAndBeats.id("hook_hands_reach");
	private static final Map<Block, Block> CRACK = new HashMap<>();

	static {
		CRACK.put(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
		CRACK.put(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS);
		CRACK.put(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
		CRACK.put(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
		CRACK.put(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Hooks::tick);
		// Defensive Mechanism: the chain blocks melee hits during the emergency mode and counterattacks.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Player p) || !(p.level() instanceof ServerLevel sl)) return true;
			PS ps = STATE.get(p.getUUID());
			if (ps == null || !ps.chainActive || sl.getGameTime() >= ps.emergencyUntil || ps.chain <= 0) return true;
			if (!(source.getDirectEntity() instanceof LivingEntity a)) return true;
			ps.chain -= Math.max(1, (int) amount);
			a.hurtServer(sl, sl.damageSources().thorns(p), 4.0F);
			Vec3 d = a.position().subtract(p.position()).normalize();
			a.push(d.x * 0.8, 0.3, d.z * 0.8);
			a.hurtMarked = true;
			sl.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 12, 0.5, 0.5, 0.5, 0.2);
			sl.playSound(null, p.blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.6F, 1.6F);
			return false;
		});
	}

	// ------------------------------------------------------------ helpers

	public static int levelOf(ItemStack s) {
		for (int i = 0; i < HookRegistry.HOOKS.length; i++) if (s.is(HookRegistry.HOOKS[i])) return i;
		return -1;
	}

	static ItemStack heldHook(LivingEntity e) {
		if (levelOf(e.getMainHandItem()) >= 0) return e.getMainHandItem();
		if (levelOf(e.getOffhandItem()) >= 0) return e.getOffhandItem();
		return ItemStack.EMPTY;
	}

	static int ench(ItemStack s, ResourceKey<Enchantment> key) {
		for (var e : s.getEnchantments().entrySet()) if (e.getKey().is(key)) return e.getIntValue();
		return 0;
	}

	static int ench(ItemStack s, String name) {
		Identifier id = BricksAndBeats.id(name);
		for (var e : s.getEnchantments().entrySet()) if (e.getKey().is(id)) return e.getIntValue();
		return 0;
	}

	/** 0 light, 1 copper/iron, 2 diamond/netherite. */
	static int armorTier(LivingEntity e) {
		int t = 0;
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack st = e.getItemBySlot(s);
			if (st.isEmpty()) continue;
			String n = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
			if (n.startsWith("diamond_") || n.startsWith("netherite_")) t = Math.max(t, 2);
			else if (n.startsWith("iron_") || n.startsWith("copper_")) t = Math.max(t, 1);
		}
		return t;
	}

	/** Hook level needed to pull this creature (6 = never possible). */
	static int requiredLevel(LivingEntity e) {
		if (e instanceof Player) {
			int t = armorTier(e);
			return t == 0 ? 0 : t == 1 ? 2 : 5;
		}
		float hp = e.getMaxHealth();
		return hp >= 200 ? 6 : hp > 40 ? 5 : 0;
	}

	/** Where the chain leaves the gun (right hip, a little in front of the owner). */
	static Vec3 handPos(LivingEntity o) {
		Vec3 look = o.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 fwd = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		return o.getEyePosition().add(0, -0.45, 0).add(fwd.scale(0.55)).add(right.scale(-0.25));
	}

	/** Real 3D claw + interlocking chain links drawn with display entities (model per hook level). */
	static void visuals(ServerLevel sl, HookEntity hook, LivingEntity owner) {
		Vec3 from = handPos(owner);
		Vec3 to = hook.position();
		Vec3 d = to.subtract(from);
		double len = d.length();
		int lvl = Math.max(0, Math.min(5, hook.lvl));
		Vec3 aim = hook.state == HookEntity.State.FLYING && hook.getDeltaMovement().lengthSqr() > 1.0E-4 ? hook.getDeltaMovement() : d;
		float hy = Fx.yawOf(aim), hp = Fx.pitchOf(aim);
		if (hook.fxHead == null || hook.fxHead.isRemoved()) {
			hook.fxHead = Fx.item(sl, "hook_head_" + lvl, to, hy, hp);
			Fx.follow(hook, hook.fxHead);
		} else {
			Fx.move(sl, hook.fxHead, to, hy, hp);
		}
		int n = len < 0.6 ? 0 : (int) Math.min(40, Math.ceil(len));
		float cy = Fx.yawOf(d), cp = Fx.pitchOf(d);
		while (hook.fxChain.size() < n) {
			Entity seg = Fx.item(sl, "chain_" + lvl, from, cy, cp);
			Fx.follow(hook, seg);
			hook.fxChain.add(seg);
		}
		for (int i = 0; i < hook.fxChain.size(); i++) {
			Entity seg = hook.fxChain.get(i);
			if (seg == null) continue;
			if (i >= n) { // chain got shorter: hide the extra links inside the hand
				Fx.move(sl, seg, from, cy, cp);
				continue;
			}
			Vec3 p = from.add(d.scale((i + 0.5) / n));
			Fx.move(sl, seg, p, cy, cp);
		}
	}

	private static BlockPos dest(ServerLevel sl, LivingEntity o) {
		BlockPos base = o.blockPosition().relative(o.getDirection());
		for (int dy = 0; dy <= 1; dy++) {
			BlockPos p = base.above(dy);
			if (sl.getBlockState(p).isAir()) return p;
		}
		return null;
	}

	private static void release(PS ps) {
		for (HookEntity h : ps.hooks) if (!h.isRemoved()) h.discard();
		ps.hooks.clear();
	}

	// ------------------------------------------------------------ firing

	static InteractionResult use(Player p, Level level, InteractionHand hand) {
		ItemStack stack = p.getItemInHand(hand);
		int lvl = levelOf(stack);
		if (lvl < 0) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		fire(sl, p, stack, p.getLookAngle(), true);
		return InteractionResult.SUCCESS;
	}

	/** Fires (or, with all hooks out, releases) the hook of {@code owner}. Used by players and by hook-wielding mobs. */
	static boolean fire(ServerLevel sl, LivingEntity owner, ItemStack stack, Vec3 dir, boolean releaseWhenFull) {
		int lvl = levelOf(stack);
		if (lvl < 0) return false;
		PS ps = STATE.computeIfAbsent(owner.getUUID(), k -> new PS());
		ps.hooks.removeIf(HookEntity::isRemoved);
		int max = (lvl >= 4 ? 2 : 1) + (ench(stack, Enchantments.MULTISHOT) > 0 ? 1 : 0);
		long now = sl.getGameTime();
		if (now - ps.lastFire < 4) return false;
		if (ps.hooks.size() >= max) { // using again with all hooks out = release
			if (releaseWhenFull) release(ps);
			return false;
		}
		ps.lastFire = now;
		HookEntity h = new HookEntity(sl, owner, new ItemStack(ModItems.HOOK_HEAD));
		h.lvl = lvl;
		h.power = ench(stack, Enchantments.POWER);
		h.range = lvl >= 4 ? 32 : 20;
		h.setDeltaMovement(dir.normalize().scale(2.5));
		sl.addFreshEntity(h);
		ps.hooks.add(h);
		sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.6F);
		return true;
	}

	/** Mobs holding a hook: shoot it at their target (called from MobAbilities). */
	static void mobFire(ServerLevel sl, net.minecraft.world.entity.Mob mob, LivingEntity target) {
		ItemStack held = heldHook(mob);
		if (held.isEmpty()) return;
		PS ps = STATE.computeIfAbsent(mob.getUUID(), k -> new PS());
		ps.hooks.removeIf(HookEntity::isRemoved);
		long now = sl.getGameTime();
		if (!ps.hooks.isEmpty() || now < ps.disabledUntil || now - ps.lastFire < 70) return;
		Vec3 d = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mob.getEyePosition());
		if (fire(sl, mob, held, d, false)) mob.swing(InteractionHand.MAIN_HAND);
	}

	// ------------------------------------------------------------ hits

	static void hitEntity(ServerLevel sl, HookEntity hook, Entity target) {
		if (!(hook.getOwner() instanceof LivingEntity owner)) { hook.discard(); return; }
		PS ps = STATE.computeIfAbsent(owner.getUUID(), k -> new PS());
		long now = sl.getGameTime();
		if (owner instanceof net.minecraft.world.entity.Mob m && m.getTarget() != target) { // mobs only hook their own target
			hook.discard();
			return;
		}

		if (target instanceof LivingEntity) {
			target.hurtServer(sl, sl.damageSources().thrown(hook, owner), 2.0F);
		}
		boolean disabled = now < ps.disabledUntil;
		if (disabled || (target instanceof LivingEntity le && le.isFallFlying())) { // can't catch elytra flyers
			hook.discard();
			return;
		}
		if (target instanceof LivingEntity le) {
			int need = requiredLevel(le);
			boolean heavyDouble = hook.lvl >= 5 && need >= 5 && countPullers(ps, target) >= 1
					&& sl.getRandom().nextFloat() < 0.5F;
			if (hook.lvl < need || heavyDouble) {
				emergency(sl, owner, ps, target);
				return;
			}
		}
		hook.state = HookEntity.State.PULL;
		hook.pulled = target;
	}

	private static int countPullers(PS ps, Entity target) {
		int n = 0;
		for (HookEntity h : ps.hooks) if (!h.isRemoved() && h.state == HookEntity.State.PULL && h.pulled == target) n++;
		return n;
	}

	/** Emergency pullback: the owner is yanked away, hooks retract, pulling is locked for ~15 s. */
	private static void emergency(ServerLevel sl, LivingEntity owner, PS ps, Entity target) {
		ItemStack held = heldHook(owner);
		int quick = ench(held, Enchantments.QUICK_CHARGE);
		int cd = Math.max(100, 300 - 50 * quick);
		long now = sl.getGameTime();
		ps.disabledUntil = now + cd;
		ps.emergencyUntil = now + cd;
		ps.chainMax = cd / 10;
		ps.chain = ps.chainMax;
		ps.chainActive = ench(held, "defensive_mechanism") > 0;
		Vec3 back = owner.position().subtract(target.position()).normalize();
		owner.setDeltaMovement(back.x * 1.4, 0.5, back.z * 1.4);
		owner.hurtMarked = true;
		sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
		sl.playSound(null, owner.blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (owner instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("message.bricks_and_beats.emergency"), true);
		release(ps);
	}

	static void hitBlock(ServerLevel sl, HookEntity hook, BlockHitResult hit) {
		if (!(hook.getOwner() instanceof LivingEntity owner)) { hook.discard(); return; }
		if (!(owner instanceof Player)) { // mobs never grapple blocks
			hook.discard();
			return;
		}
		PS ps = STATE.computeIfAbsent(owner.getUUID(), k -> new PS());
		BlockPos pos = hit.getBlockPos();
		BlockState st = sl.getBlockState(pos);
		Block block = st.getBlock();
		String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
		float hardness = st.getDestroySpeed(sl, pos);
		boolean canPull = sl.getGameTime() >= ps.disabledUntil;
		ItemStack held = heldHook(owner);
		int rs = ench(held, "redstone_hook");

		// Redstone hook: recharge from a redstone block.
		if (rs > 0 && st.is(Blocks.REDSTONE_BLOCK)) {
			held.setDamageValue(rs);
			sl.removeBlock(pos, false);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
			hook.discard();
			return;
		}

		// Falling blocks (sand, gravel, concrete powder, anvils): pull them.
		if (canPull && block instanceof FallingBlock) {
			FallingBlockEntity fb = FallingBlockEntity.fall(sl, pos, st);
			Vec3 d = owner.position().add(0, 1, 0).subtract(fb.position()).normalize();
			fb.setDeltaMovement(d.scale(0.9).add(0, 0.25, 0));
			hook.discard();
			return;
		}

		// Storage blocks: chests/barrels/shulkers from level 1, any container from level 5 (never ender chests).
		BlockEntity be = sl.getBlockEntity(pos);
		if (be instanceof Container && !st.is(Blocks.ENDER_CHEST)) {
			boolean basic = id.contains("chest") || id.contains("barrel") || id.contains("shulker_box");
			if (canPull && ((hook.lvl >= 1 && basic) || hook.lvl >= 5)) {
				BlockPos to = dest(sl, owner);
				if (to != null) {
					MinecraftServer server = sl.getServer();
					String cmd = "clone %d %d %d %d %d %d %d %d %d replace move".formatted(pos.getX(), pos.getY(), pos.getZ(),
							pos.getX(), pos.getY(), pos.getZ(), to.getX(), to.getY(), to.getZ());
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(sl).withSuppressedOutput(), cmd);
					sl.playSound(null, to, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				hook.discard();
				return;
			}
		}

		// Level 3: crack bricks / break weak blocks.
		if (hook.lvl >= 3) {
			Block cracked = CRACK.get(block);
			if (cracked != null) {
				sl.setBlock(pos, cracked.defaultBlockState(), 3);
				sl.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.0F, 0.8F);
				hook.discard();
				return;
			}
			if (hardness >= 0 && hardness <= 0.6F && !(be instanceof Container)) {
				sl.destroyBlock(pos, true, owner);
				hook.discard();
				return;
			}
		}

		// Super-hard blocks (obsidian, bedrock...) can't be hooked.
		if (hardness < 0 || hardness >= 30) {
			hook.discard();
			return;
		}

		// Grapple.
		hook.state = HookEntity.State.GRAPPLE;
		hook.anchor = hit.getLocation();
		hook.setDeltaMovement(Vec3.ZERO);
		if (rs > 0 && held.getDamageValue() > 0) { // redstone hook: pulse the hit block
			held.setDamageValue(held.getDamageValue() - 1);
			BlockPos p2 = pos.relative(hit.getDirection());
			if (sl.getBlockState(p2).isAir()) {
				sl.setBlock(p2, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
				TEMP.add(new Temp(sl, p2, sl.getGameTime() + 30));
			}
		}
	}

	// ------------------------------------------------------------ per tick

	private static void tick(MinecraftServer server) {
		Fx.tick();
		if (server.getTickCount() % 200 == 0) { // forget idle owners (mobs that died, players that left)
			long now = server.overworld().getGameTime();
			STATE.entrySet().removeIf(en -> {
				en.getValue().hooks.removeIf(HookEntity::isRemoved);
				return en.getValue().hooks.isEmpty() && now > en.getValue().disabledUntil && now - en.getValue().lastFire > 400;
			});
		}
		for (Iterator<Temp> it = TEMP.iterator(); it.hasNext(); ) {
			Temp t = it.next();
			if (t.level().getGameTime() >= t.expire()) {
				if (t.level().getBlockState(t.pos()).is(Blocks.REDSTONE_BLOCK)) t.level().removeBlock(t.pos(), false);
				it.remove();
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!(p.level() instanceof ServerLevel sl)) continue;

			// Hook-Hands: extra reach for blocks (chests, levers, crafting tables...). Sneak + use fires the hook.
			ItemStack held = heldHook(p);
			var reach = p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
			if (reach != null) {
				if (levelOf(held) >= 0 && ench(held, "hook_hands") > 0) {
					reach.addOrUpdateTransientModifier(new AttributeModifier(HANDS_MOD, 24.0, AttributeModifier.Operation.ADD_VALUE));
				} else if (reach.hasModifier(HANDS_MOD)) {
					reach.removeModifier(HANDS_MOD);
				}
			}

			PS ps = STATE.get(p.getUUID());
			if (ps == null) continue;
			ps.hooks.removeIf(HookEntity::isRemoved);
			grapple(p, ps);
			defend(sl, p, ps);
		}
	}

	private static void grapple(ServerPlayer p, PS ps) {
		List<HookEntity> g = ps.hooks.stream().filter(h -> h.state == HookEntity.State.GRAPPLE && h.anchor != null).toList();
		if (g.isEmpty()) return;
		Vec3 v = p.getDeltaMovement();
		if (g.size() == 1) {
			HookEntity h = g.get(0);
			double k = 1 + 0.25 * h.power; // Power = faster pull (range unchanged)
			Vec3 d = h.anchor.subtract(p.getEyePosition());
			if (d.length() > 2.0) {
				v = v.add(d.normalize().scale(0.16 * k));
				double max = 1.5 * k;
				if (v.length() > max) v = v.normalize().scale(max);
			} else {
				v = v.scale(0.5).add(0, 0.039, 0); // close to the anchor: hang (0.039 cancels gravity at this damping)
			}
		} else {
			// Two anchors (e.g. between two walls while falling): hang, with a soft pull toward the middle.
			// 0.0627 cancels gravity at damping 0.8, so the player no longer drifts upwards.
			Vec3 mid = g.get(0).anchor.add(g.get(1).anchor).scale(0.5);
			Vec3 d = mid.subtract(p.getEyePosition());
			v = v.scale(0.8).add(0, 0.0627, 0);
			if (d.length() > 3.0) v = v.add(d.normalize().scale(0.05));
		}
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.resetFallDistance();
	}

	/** Defensive Mechanism: during the emergency mode the chain eats incoming projectiles. */
	private static void defend(ServerLevel sl, ServerPlayer p, PS ps) {
		if (!ps.chainActive || sl.getGameTime() >= ps.emergencyUntil || ps.chain <= 0) return;
		for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5),
				e -> !(e instanceof HookEntity) && e.getOwner() != p)) {
			Vec3 toPlayer = p.position().add(0, 1, 0).subtract(pr.position());
			if (pr.getDeltaMovement().dot(toPlayer) > 0) {
				pr.discard();
				ps.chain -= 2;
				sl.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
			}
		}
		if (sl.getGameTime() % 4 == 0) {
			Vec3 f = p.position().add(p.getLookAngle().scale(1.2)).add(0, 1.0, 0);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, f.x, f.y, f.z, 4, 0.4, 0.5, 0.4, 0.0);
		}
	}

	private Hooks() {}
}
