package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Режим FOLLOW: каждый воин идёт не к владельцу, а в свой слот на кольце вокруг него.
 * Радиус кольца это настраиваемая дистанция (меню знамени), угол и разброс радиуса
 * берутся из UUID воина, поэтому армия расходится веером, а не толпится.
 * Не телепортируются: если отстали, бегут быстрее; издалека их зовёт кнопка «Позвать» в меню знамени.
 */
public class FollowOwnerGoal extends Goal {
	private static final double SLOT_TOLERANCE = 2.5;

	private final NullWarrior warrior;
	private final double speed;
	private int recalc;

	public FollowOwnerGoal(NullWarrior warrior, double speed) {
		this.warrior = warrior;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private static final double SPACING = 2.0;

	/** Место воина в формации относительно владельца (формация поворачивается вслед за его взглядом). */
	private Vec3 slot(Player owner) {
		int n = Math.max(1, warrior.getSlotCount());
		int i = Math.min(warrior.getSlotIndex(), n - 1);
		double d = warrior.getFollowDistance();
		double yaw = Math.toRadians(owner.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double back;
		double side;
		switch (warrior.getFormation()) {
			case SQUARE -> {
				int cols = (int) Math.ceil(Math.sqrt(n));
				int row = i / cols;
				int col = i % cols;
				int inRow = Math.min(cols, n - row * cols);
				side = (col - (inRow - 1) / 2.0) * SPACING;
				back = d * 0.5 + row * SPACING;
			}
			case TRIANGLE -> {
				int row = 0;
				int start = 0;
				while (i >= start + row + 1) {
					start += row + 1;
					row++;
				}
				side = (i - start - row / 2.0) * SPACING;
				back = d * 0.5 + row * SPACING;
			}
			default -> {
				double a = Math.PI * 2.0 * i / n;
				side = Math.cos(a) * d;
				back = -Math.sin(a) * d;
			}
		}
		return owner.position().add(-fx * back + rx * side, 0.0, -fz * back + rz * side);
	}

	@Override
	public boolean canUse() {
		Player owner = warrior.getOwner();
		if (warrior.getMode() != NullWarrior.Mode.FOLLOW || owner == null || owner.isSpectator()) {
			return false;
		}
		Vec3 slot = slot(owner);
		double dx = warrior.getX() - slot.x;
		double dz = warrior.getZ() - slot.z;
		return dx * dx + dz * dz > SLOT_TOLERANCE * SLOT_TOLERANCE;
	}

	@Override
	public boolean canContinueToUse() {
		Player owner = warrior.getOwner();
		return warrior.getMode() == NullWarrior.Mode.FOLLOW
				&& owner != null
				&& !warrior.getNavigation().isDone();
	}

	@Override
	public void start() {
		this.recalc = 0;
	}

	@Override
	public void stop() {
		warrior.getNavigation().stop();
	}

	@Override
	public void tick() {
		Player owner = warrior.getOwner();
		if (owner == null || --this.recalc > 0) {
			return;
		}
		this.recalc = 10;
		Vec3 slot = slot(owner);
		double sprint = warrior.distanceToSqr(owner) > 14.0 * 14.0 ? 1.5 : 1.0;
		warrior.getNavigation().moveTo(slot.x, owner.getY(), slot.z, speed * sprint);
	}
}
