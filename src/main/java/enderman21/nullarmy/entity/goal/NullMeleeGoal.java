package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.entity.NullWarrior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Обычная ближняя атака, но с учётом режима: HOLD и GUARD не уходят далеко за целью. */
public class NullMeleeGoal extends MeleeAttackGoal {
	private static final double HOLD_RANGE = 6.0;
	private static final double GUARD_RANGE = 20.0;

	private final NullWarrior warrior;

	public NullMeleeGoal(NullWarrior warrior, double speed) {
		super(warrior, speed, true);
		this.warrior = warrior;
	}

	@Override
	public boolean canUse() {
		return allowed() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return allowed() && super.canContinueToUse();
	}

	private boolean allowed() {
		LivingEntity target = warrior.getTarget();
		if (target == null) {
			return false;
		}
		return switch (warrior.getMode()) {
			case HOLD -> warrior.distanceToSqr(target) <= HOLD_RANGE * HOLD_RANGE;
			case GUARD -> {
				BlockPos guard = warrior.getGuardPos();
				yield guard == null || guard.distToCenterSqr(target.position()) <= GUARD_RANGE * GUARD_RANGE;
			}
			default -> true;
		};
	}
}
