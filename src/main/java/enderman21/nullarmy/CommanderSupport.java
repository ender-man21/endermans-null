package enderman21.nullarmy;

import enderman21.nullarmy.entity.NullWarrior;
import enderman21.nullarmy.network.SurveyPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Механики командира вне его ИИ:
 * Protect: смертельный для владельца удар принимает на себя командир (раньше срабатывания тотема);
 * Survey: владельцу подсвечиваются враги рядом (подсветку включает клиент, остальные игроки её не видят).
 */
public final class CommanderSupport {
	private static final double PROTECT_RANGE = NullConfig.protectRange;
	private static final double SURVEY_RANGE = NullConfig.surveyRange;
	private static final int SURVEY_INTERVAL = 10;

	/** Игроки, которым уже отправлен непустой список подсветки (чтобы потом отправить пустой). */
	private static final Set<UUID> SURVEYED = new HashSet<>();

	private CommanderSupport() {
	}

	public static void register() {
		// Событие идёт до расчёта урона и до проверки тотема бессмертия
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Разведчик неуязвим только если включено в конфиге (кроме пустоты и /kill)
			if (NullConfig.surveyInvulnerable && entity instanceof NullWarrior scout && scout.isSurveying()
					&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return false;
			}
			if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
				return true;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			NullWarrior protector = protector(player);
			if (protector == null || !lethal(player, source, amount)) {
				return true;
			}
			sacrifice(player, protector);
			return false;
		});
		ServerTickEvents.END_SERVER_TICK.register(CommanderSupport::tickSurvey);
	}

	private static NullWarrior protector(ServerPlayer player) {
		List<NullWarrior> found = player.level().getEntitiesOfClass(NullWarrior.class,
				player.getBoundingBox().inflate(PROTECT_RANGE),
				w -> w.isAlive() && w.isCommander()
						&& w.getCommanderMode() == NullWarrior.CommanderMode.PROTECT
						&& player.getUUID().equals(w.getOwnerUuid()));
		return found.isEmpty() ? null : found.get(0);
	}

	/** Грубая оценка: хватит ли удара, чтобы убить игрока, с учётом брони (без чар и эффектов, с запасом). */
	private static boolean lethal(ServerPlayer player, DamageSource source, float amount) {
		float health = player.getHealth() + player.getAbsorptionAmount();
		double dmg = amount;
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			double armor = player.getArmorValue();
			double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
			double f = Math.min(20.0, Math.max(armor / 5.0, armor - 4.0 * dmg / (toughness + 8.0)));
			dmg = dmg * (1.0 - f / 25.0);
		}
		return dmg >= health;
	}

	private static void sacrifice(ServerPlayer player, NullWarrior commander) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		// Командир появляется рядом, принимает удар и исчезает
		commander.setPos(player.getX() + 1.0, player.getY(), player.getZ());
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.5, 1.0, 0.5, 0.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		NullArmy.vanish(commander, level);
		player.sendSystemMessage(Component.translatable("message.endermans-null.commander_sacrifice"), true);
	}

	private static void tickSurvey(MinecraftServer server) {
		if (server.getTickCount() % SURVEY_INTERVAL != 0) {
			return;
		}
		Set<UUID> active = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			List<NullWarrior> scouts = new ArrayList<>();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof NullWarrior w && w.isAlive() && w.isSurveying() && w.getOwnerUuid() != null) {
					scouts.add(w);
				}
			}
			for (NullWarrior scout : scouts) {
				ServerPlayer owner = server.getPlayerList().getPlayer(scout.getOwnerUuid());
				if (owner == null || owner.level() != level) {
					continue;
				}
				List<Integer> ids = new ArrayList<>();
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
						owner.getBoundingBox().inflate(SURVEY_RANGE), e -> isFoe(e, owner))) {
					ids.add(e.getId());
				}
				ServerPlayNetworking.send(owner, new SurveyPayload(ids));
				active.add(owner.getUUID());
			}
		}
		// Кто больше не в разведке: один раз отправляем пустой список, чтобы снять подсветку
		for (UUID uuid : new ArrayList<>(SURVEYED)) {
			if (!active.contains(uuid)) {
				ServerPlayer p = server.getPlayerList().getPlayer(uuid);
				if (p != null) {
					ServerPlayNetworking.send(p, new SurveyPayload(List.of()));
				}
				SURVEYED.remove(uuid);
			}
		}
		SURVEYED.addAll(active);
	}

	private static boolean isFoe(LivingEntity e, ServerPlayer owner) {
		if (!e.isAlive() || e == owner || e instanceof NullWarrior) {
			return false;
		}
		if (e instanceof ServerPlayer other) {
			return !other.isSpectator();
		}
		return e instanceof Enemy;
	}
}
