package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Strike (shock chain), Trimming (shreds armor), Cutthroat (backstab + bleeding), Magma Walker (boots: walk on lava).
 * Also the built-in perks of daggers (backstab) and scythes (sweep).
 */
public final class NewEnchants {
	private static boolean busy;

	private record Revert(ServerLevel level, BlockPos pos, long due) {}

	private static final List<Revert> REVERTS = new ArrayList<>();

	public static int lvl(ItemStack stack, String name) {
		if (stack == null || stack.isEmpty()) return 0;
		var ench = stack.getEnchantments();
		for (Holder<Enchantment> h : ench.keySet()) {
			if (h.is(BricksAndBeats.id(name))) return ench.getLevel(h);
		}
		return 0;
	}

	private static boolean isIn(Item[] arr, ItemStack s) {
		for (Item i : arr) if (s.is(i)) return true;
		return false;
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(NewEnchants::onDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof Player p && source.is(DamageTypes.HOT_FLOOR) && lvl(p.getItemBySlot(EquipmentSlot.FEET), "magma_walker") > 0));
		ServerTickEvents.END_SERVER_TICK.register(NewEnchants::tick);
	}

	private static DamageSource src(ServerLevel level, LivingEntity attacker) {
		return attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
	}

	private static boolean behind(LivingEntity attacker, LivingEntity target) {
		Vec3 look = target.getLookAngle();
		Vec3 to = attacker.position().subtract(target.position());
		if (to.lengthSqr() < 1.0E-4) return false;
		return look.dot(to.normalize()) < -0.3;
	}

	private static void onDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (busy || taken <= 0.0F || !(target.level() instanceof ServerLevel level)) return;
		if (!(source.getEntity() instanceof LivingEntity attacker)) return;
		ItemStack w = source.getWeaponItem();
		if (w == null || w.isEmpty()) return;
		boolean dagger = isIn(ModItems.DAGGERS, w);
		boolean scythe = isIn(ModItems.SCYTHES, w);
		int strike = lvl(w, "strike");
		int trim = lvl(w, "trimming");
		int cut = lvl(w, "cutthroat");
		if (!dagger && !scythe && strike == 0 && trim == 0 && cut == 0) return;
		busy = true;
		try {
			if ((dagger || scythe) && attacker instanceof Player) w.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);

			float extra = 0.0F;
			boolean back = behind(attacker, target);
			if (dagger && back) extra += taken * 0.75F;
			if (cut > 0) {
				if (back) extra += 1.5F * cut;
				if (target.getHealth() < target.getMaxHealth() * 0.3F) extra += 1.0F * cut;
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, 50 + 30 * cut, 0));
			}
			if (extra > 0.0F && target.isAlive()) {
				target.invulnerableTime = 0;
				target.hurtServer(level, src(level, attacker), extra);
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			}

			if (trim > 0) {
				for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					ItemStack a = target.getItemBySlot(slot);
					if (a.isEmpty() || !a.isDamageableItem()) continue;
					a.setDamageValue(a.getDamageValue() + 2 * trim);
					if (a.getDamageValue() >= a.getMaxDamage()) target.setItemSlot(slot, ItemStack.EMPTY);
				}
			}

			if (strike > 0 && level.getRandom().nextFloat() < 0.15F * strike) {
				level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.8F);
				int hit = 0;
				for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.0),
						e -> e != attacker && e.isAlive() && !(e instanceof Player))) {
					if (hit++ >= 3 + strike) break;
					o.invulnerableTime = 0;
					o.hurtServer(level, src(level, attacker), 2.0F + 2.0F * strike);
					o.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, o.getX(), o.getY() + 1.0, o.getZ(), 14, 0.3, 0.5, 0.3, 0.3);
				}
			}

			if (scythe) {
				float part = taken * 0.5F;
				for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.0, 0.5, 2.0),
						e -> e != attacker && e != target && e.isAlive() && !(e instanceof Player))) {
					o.invulnerableTime = 0;
					o.hurtServer(level, src(level, attacker), part);
				}
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.8, target.getZ(), 1, 0, 0, 0, 0);
			}
		} finally {
			busy = false;
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (server.getTickCount() % 2 == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				for (var pl : level.players()) {
					int l = lvl(pl.getItemBySlot(EquipmentSlot.FEET), "magma_walker");
					if (l <= 0) continue;
					BlockPos base = pl.blockPosition().below();
					int r = 1 + l;
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							BlockPos p = base.offset(dx, 0, dz);
							if (level.getBlockState(p).is(Blocks.LAVA) && level.getFluidState(p).isSource() && level.getBlockState(p.above()).isAir()) {
								level.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
								REVERTS.add(new Revert(level, p.immutable(), now + 100 + level.getRandom().nextInt(60)));
							}
						}
					}
				}
			}
		}
		Iterator<Revert> it = REVERTS.iterator();
		while (it.hasNext()) {
			Revert r = it.next();
			if (r.due() > now) continue;
			it.remove();
			if (r.level().getBlockState(r.pos()).is(Blocks.MAGMA_BLOCK)) r.level().setBlockAndUpdate(r.pos(), Blocks.LAVA.defaultBlockState());
		}
	}

	private NewEnchants() {}
}
