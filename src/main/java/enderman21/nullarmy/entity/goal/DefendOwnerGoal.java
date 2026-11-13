package enderman21.nullarmy.entity.goal;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/**
 * Защита владельца.
 * attackersOfOwner = true: бить того, кто ударил владельца;
 * attackersOfOwner = false: бить того, кого ударил владелец.
 */
public class DefendOwnerGoal extends TargetGoal {
	private final NullWarrior warrior;
	private final boolean attackersOfOwner;
	private LivingEntity candidate;
	private int lastTimestamp;

	public DefendOwnerGoal(NullWarrior warrior, boolean attackersOfOwner) {
		super(warrior, false);
		this.warrior = warrior;
		this.attackersOfOwner = attackersOfOwner;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		Player owner = warrior.getOwner();
		if (owner == null || warrior.getMode() == NullWarrior.Mode.ATTACK
				|| warrior.getBehavior() == NullWarrior.Behavior.PASSIVE) {
			return false;
		}
		this.candidate = attackersOfOwner ? owner.getLastHurtByMob() : owner.getLastHurtMob();
		int timestamp = attackersOfOwner ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
		return timestamp != this.lastTimestamp
				&& this.candidate != null
				&& this.canAttack(this.candidate, TargetingConditions.DEFAULT);
	}

	@Override
	public void start() {
		warrior.setTarget(this.candidate);
		Player owner = warrior.getOwner();
		if (owner != null) {
			this.lastTimestamp = attackersOfOwner ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
		}
		super.start();
	}
}
