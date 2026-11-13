package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/** Режим GUARD («Гулять»): воины бродят вокруг флага (радиус около 8 блоков), издалека возвращаются к нему. */
public class GuardPointGoal extends Goal {
	private static final double WANDER_RADIUS = 8.0;

	private final NullWarrior warrior;
	private final double speed;

	public GuardPointGoal(NullWarrior warrior, double speed) {
		this.warrior = warrior;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos pos = warrior.getGuardPos();
		if (warrior.getMode() != NullWarrior.Mode.GUARD || pos == null || !warrior.getNavigation().isDone()) {
			return false;
		}
		boolean far = pos.distToCenterSqr(warrior.position()) > (WANDER_RADIUS + 2.0) * (WANDER_RADIUS + 2.0);
		return far || warrior.getRandom().nextInt(50) == 0;
	}

	@Override
	public boolean canContinueToUse() {
		return warrior.getMode() == NullWarrior.Mode.GUARD && !warrior.getNavigation().isDone();
	}

	@Override
	public void start() {
		BlockPos pos = warrior.getGuardPos();
		if (pos == null) {
			return;
		}
		double x = pos.getX() + 0.5 + (warrior.getRandom().nextDouble() * 2.0 - 1.0) * WANDER_RADIUS;
		double z = pos.getZ() + 0.5 + (warrior.getRandom().nextDouble() * 2.0 - 1.0) * WANDER_RADIUS;
		warrior.getNavigation().moveTo(x, pos.getY(), z, speed * 0.8);
	}

	@Override
	public void stop() {
		warrior.getNavigation().stop();
	}
}
