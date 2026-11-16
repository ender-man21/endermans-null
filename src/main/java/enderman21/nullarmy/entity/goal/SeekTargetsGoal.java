package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Поведения AGGRESSIVE и RAID: воин сам ищет цели в радиусе 16 блоков.
 * AGGRESSIVE: только враждебные мобы.
 * RAID: всё живое, кроме владельца, своих, игроков, жителей, прирученных животных и стоек для брони.
 */
public class SeekTargetsGoal extends Goal {
	private static final double RANGE = enderman21.nullarmy.NullConfig.targetSearchRange;

	private final NullWarrior warrior;
	private LivingEntity found;
	private int cooldown;

	public SeekTargetsGoal(NullWarrior warrior) {
		this.warrior = warrior;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		NullWarrior.Behavior behavior = warrior.getBehavior();
		if ((behavior != NullWarrior.Behavior.AGGRESSIVE && behavior != NullWarrior.Behavior.RAID)
				|| warrior.getMode() == NullWarrior.Mode.HOLD
				|| warrior.getTarget() != null) {
			return false;
		}
		if (--cooldown > 0) {
			return false;
		}
		cooldown = 15;
		List<LivingEntity> candidates = warrior.level().getEntitiesOfClass(LivingEntity.class,
				warrior.getBoundingBox().inflate(RANGE), this::valid);
		found = candidates.stream().min(Comparator.comparingDouble(warrior::distanceToSqr)).orElse(null);
		return found != null;
	}

	private boolean valid(LivingEntity e) {
		if (e == warrior || !e.isAlive() || e.isSpectator() || !warrior.canAttack(e) || !warrior.hasLineOfSight(e)) {
			return false;
		}
		if (warrior.getBehavior() == NullWarrior.Behavior.AGGRESSIVE) {
			return e instanceof Enemy;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return (enderman21.nullarmy.NullConfig.raidAttacksPlayers || !(e instanceof Player))
				&& !type.equals("villager")
				&& !type.equals("wandering_trader")
				&& !(e instanceof ArmorStand)
				&& !(e instanceof TamableAnimal pet && pet.isTame());
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		warrior.setTarget(found);
	}
}
