package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Блинк-удар командира (режим Lead). Короткая "замашка": командир смотрит вверх с жемчугом в руке,
 * затем телепортируется на 4-9 блоков над целью, падает на неё с самонаведением и бьёт обычной
 * ванильной атакой. Бонус булавы и волна считаются самой булавой (как у зомби с булавой),
 * для этого достаточно, чтобы fallDistance был больше 1.5.
 */
public class CommanderSmashGoal extends Goal {
	private static final long COOLDOWN = (long) (enderman21.nullarmy.NullConfig.smashCooldownSeconds * 20.0);
	private static final double MIN_RANGE = 2.5;
	private static final double MAX_RANGE = 28.0;
	private static final int WINDUP_TICKS = 8;
	private static final int MAX_FALL_TICKS = 60;

	private final NullWarrior warrior;
	private long readyAt = 0L;
	private int windup;
	private int fallTicks;
	private boolean falling;
	private boolean done;
	private ItemStack saved = ItemStack.EMPTY;

	public CommanderSmashGoal(NullWarrior warrior) {
		this.warrior = warrior;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private boolean active() {
		return warrior.isCommander() && warrior.getCommanderMode() == NullWarrior.CommanderMode.LEAD;
	}

	@Override
	public boolean canUse() {
		if (!active() || warrior.level().getGameTime() < readyAt || warrior.isInWater()
				|| warrior.getDeltaMovement().y > 0.2) {
			return false;
		}
		LivingEntity target = warrior.getTarget();
		if (target == null || !target.isAlive() || target.isInWater() || target.isInLava()) {
			return false;
		}
		double dist = warrior.distanceTo(target);
		return dist >= MIN_RANGE && dist <= MAX_RANGE && blinkPoint(target) != null;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = warrior.getTarget();
		return active() && !done && target != null && target.isAlive() && fallTicks < MAX_FALL_TICKS;
	}

	@Override
	public void start() {
		done = false;
		falling = false;
		fallTicks = 0;
		windup = WINDUP_TICKS;
		warrior.getNavigation().stop();
		saved = warrior.getItemBySlot(EquipmentSlot.MAINHAND).copy();
		warrior.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ENDER_PEARL));
		if (warrior.level() instanceof ServerLevel level) {
			level.playSound(null, warrior.getX(), warrior.getY(), warrior.getZ(),
					SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 0.8F, 0.8F);
		}
	}

	@Override
	public void tick() {
		LivingEntity target = warrior.getTarget();
		if (target == null) {
			return;
		}
		if (!falling) {
			// Замашка: смотрит вверх, стоит на месте
			warrior.getNavigation().stop();
			warrior.getLookControl().setLookAt(warrior.getX(), warrior.getEyeY() + 10.0, warrior.getZ());
			if (--windup > 0) {
				return;
			}
			restoreWeapon();
			Vec3 point = blinkPoint(target);
			if (point == null) {
				done = true;
				readyAt = warrior.level().getGameTime() + 40L;
				return;
			}
			fx(warrior.position());
			warrior.setPos(point.x, point.y, point.z);
			warrior.setDeltaMovement(0.0, 0.0, 0.0);
			warrior.fallDistance = 0;
			fx(point);
			falling = true;
			return;
		}

		fallTicks++;
		double dx = target.getX() - warrior.getX();
		double dz = target.getZ() - warrior.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		Vec3 motion = warrior.getDeltaMovement();
		if (dist > 0.4) {
			double speed = Math.min(0.45, dist * 0.5);
			warrior.setDeltaMovement(dx / dist * speed, motion.y, dz / dist * speed);
		}
		warrior.getLookControl().setLookAt(target, 60.0F, 60.0F);

		boolean inReach = warrior.distanceTo(target) < 3.0F;
		if (inReach && warrior.fallDistance > 1.5F) {
			// Ванильный удар: булава сама добавит урон за падение и устроит волну
			if (warrior.level() instanceof ServerLevel level) {
				warrior.doHurtTarget(level, target);
			}
			finish();
		} else if (fallTicks > 2 && warrior.onGround()) {
			finish();
		}
	}

	@Override
	public void stop() {
		restoreWeapon();
		warrior.fallDistance = 0;
		falling = false;
	}

	private void finish() {
		done = true;
		readyAt = warrior.level().getGameTime() + COOLDOWN;
	}

	private void restoreWeapon() {
		if (warrior.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.ENDER_PEARL) && !saved.isEmpty()) {
			warrior.setItemSlot(EquipmentSlot.MAINHAND, saved);
			saved = ItemStack.EMPTY;
		}
	}

	/** Точка над целью: самая высокая из 4-9 блоков, где помещается командир. */
	private Vec3 blinkPoint(LivingEntity target) {
		for (int h = 9; h >= 4; h--) {
			AABB box = warrior.getBoundingBox().move(
					target.getX() - warrior.getX(),
					target.getY() + h - warrior.getY(),
					target.getZ() - warrior.getZ());
			if (warrior.level().noCollision(warrior, box)) {
				return new Vec3(target.getX(), target.getY() + h, target.getZ());
			}
		}
		return null;
	}

	private void fx(Vec3 at) {
		if (warrior.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 40, 0.4, 0.9, 0.4, 0.4);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.7F);
		}
	}
}
