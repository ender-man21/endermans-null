package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.NullArmy;
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
 * Режим CURSOR («За курсором»): тот же строй, но центром служит точка, на которую смотрит владелец
 * (не дальше NullArmy.CURSOR_RANGE блоков); если взгляд в небо, армия остаётся у последней точки.
 */
public class FollowOwnerGoal extends Goal {
	private static final double SLOT_TOLERANCE = 2.5;
	/** Самый длинный отрезок пути за один расчёт (блоки). */
	private static final double MAX_LEG = 28.0;

	private final NullWarrior warrior;
	private final double speed;
	private int recalc;
	private Vec3 lastCursor;

	public FollowOwnerGoal(NullWarrior warrior, double speed) {
		this.warrior = warrior;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private static final double SPACING = 2.0;

	/** Место воина в формации относительно владельца (формация поворачивается вслед за его взглядом). */
	private Vec3 slot(Player owner) {
		Vec3 center = owner.position();
		if (warrior.getMode() == NullWarrior.Mode.CURSOR) {
			Vec3 point = NullArmy.cursorPoint(owner);
			if (point != null) {
				lastCursor = point;
			}
			if (lastCursor != null) {
				center = lastCursor;
			}
		} else {
			lastCursor = null;
		}
		if (warrior.isCommander() && !warrior.obeysArmy()) {
			// Командир в режиме Protect/Survey: держится рядом с владельцем сзади справа, формации не учитывает
			double yawC = Math.toRadians(owner.getYRot());
			return owner.position().add(
					Math.sin(yawC) * 3.0 - Math.cos(yawC) * 2.0, 0.0,
					-Math.cos(yawC) * 3.0 - Math.sin(yawC) * 2.0);
		}
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
			case LINE -> {
				// шеренга перед центром, лицом в сторону взгляда владельца
				side = (i - (n - 1) / 2.0) * SPACING;
				back = -d;
			}
			case COLUMN -> {
				// колонна по двое, вытянутая назад
				int row = i / 2;
				int col = i % 2;
				int inRow = Math.min(2, n - row * 2);
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
		return center.add(-fx * back + rx * side, 0.0, -fz * back + rz * side);
	}

	private boolean following() {
		NullWarrior.Mode mode = warrior.getMode();
		return mode == NullWarrior.Mode.FOLLOW || mode == NullWarrior.Mode.CURSOR;
	}

	@Override
	public boolean canUse() {
		Player owner = warrior.getOwner();
		if (!following() || owner == null || owner.isSpectator()) {
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
		return following()
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
		double dx = slot.x - warrior.getX();
		double dz = slot.z - warrior.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		// Путь ограничен дальностью обзора моба (40): к далёкой цели идём по частям
		double tx = slot.x;
		double tz = slot.z;
		if (dist > MAX_LEG) {
			tx = warrior.getX() + dx / dist * MAX_LEG;
			tz = warrior.getZ() + dz / dist * MAX_LEG;
		}
		double ty = warrior.getMode() == NullWarrior.Mode.CURSOR ? slot.y : owner.getY();
		warrior.getNavigation().moveTo(tx, ty, tz, speed);
	}
}
