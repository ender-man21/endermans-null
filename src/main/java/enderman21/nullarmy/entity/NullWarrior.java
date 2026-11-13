package enderman21.nullarmy.entity;

import enderman21.nullarmy.entity.goal.DefendOwnerGoal;
import enderman21.nullarmy.entity.goal.FollowOwnerGoal;
import enderman21.nullarmy.entity.goal.GuardPointGoal;
import enderman21.nullarmy.entity.goal.NullMeleeGoal;
import enderman21.nullarmy.entity.goal.SeekTargetsGoal;
import java.util.UUID;
import enderman21.nullarmy.ArmyRegistry;
import enderman21.nullarmy.NullArmy;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Воин Null: подчиняется владельцу, у которого есть Тотем Нулла. */
public class NullWarrior extends PathfinderMob {

	public enum Mode {
		FOLLOW, HOLD, GUARD, ATTACK;

		public Mode next() {
			// ATTACK ставится только приказом на цель, в цикл не входит
			return switch (this) {
				case FOLLOW -> HOLD;
				case HOLD -> GUARD;
				default -> FOLLOW;
			};
		}

		public String translationKey() {
			return "mode.endermans-null." + name().toLowerCase();
		}

		public static Mode byId(int id) {
			Mode[] values = values();
			return values[Math.floorMod(id, values.length)];
		}
	}

	public enum Formation {
		CIRCLE, SQUARE, TRIANGLE;

		public String translationKey() {
			return "formation.endermans-null." + name().toLowerCase();
		}

		public static Formation byId(int id) {
			Formation[] values = values();
			return values[Math.floorMod(id, values.length)];
		}
	}

	public enum Behavior {
		PASSIVE, NEUTRAL, AGGRESSIVE, RAID;

		public String translationKey() {
			return "behavior.endermans-null." + name().toLowerCase();
		}

		public static Behavior byId(int id) {
			Behavior[] values = values();
			return values[Math.floorMod(id, values.length)];
		}
	}

	private static final EntityDataAccessor<Integer> DATA_MODE =
			SynchedEntityData.defineId(NullWarrior.class, EntityDataSerializers.INT);

	@Nullable
	private UUID ownerUuid;
	@Nullable
	private BlockPos guardPos;
	/** id армии (скрытая метка на Тотеме Нулла). */
	private String armyId = "";
	/** На каком расстоянии от владельца держаться (блоки). */
	private int followDistance = NullArmy.DEFAULT_DISTANCE;
	private float lastHealth = -1.0F;
	private Formation formation = Formation.CIRCLE;
	private Behavior behavior = Behavior.NEUTRAL;
	private int slotIndex = 0;
	private int slotCount = 1;
	/** Тиков с последнего полученного урона (для регенерации). */
	private int sinceHurt = 0;

	public NullWarrior(EntityType<? extends NullWarrior> entityType, Level level) {
		super(entityType, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, Mode.FOLLOW.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new NullMeleeGoal(this, 1.2D));
		this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.15D));
		this.goalSelector.addGoal(3, new GuardPointGoal(this, 1.0D));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new DefendOwnerGoal(this, true));
		this.targetSelector.addGoal(2, new DefendOwnerGoal(this, false));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this, NullWarrior.class));
		this.targetSelector.addGoal(4, new SeekTargetsGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			this.serverTickExtras();
		}
		if (!this.level().isClientSide()) {
			this.regenerate();
		}
		// Цель побеждена или потеряна: возвращаемся в строй
		if (!this.level().isClientSide() && this.getMode() == Mode.ATTACK) {
			LivingEntity target = this.getTarget();
			if (target == null || !target.isAlive()) {
				this.setMode(Mode.FOLLOW);
			}
		}
	}

	/**
	 * Регенерация: 8 секунд без урона, затем 1 HP каждые 3 секунды,
	 * рядом с владельцем (до 8 блоков) вдвое быстрее.
	 */
	private void regenerate() {
		float health = this.getHealth();
		if (this.lastHealth >= 0 && health < this.lastHealth - 0.001F) {
			this.sinceHurt = 0;
		} else {
			this.sinceHurt++;
		}
		this.lastHealth = health;
		if (this.sinceHurt < 160 || health >= this.getMaxHealth()) {
			return;
		}
		Player owner = this.getOwner();
		int interval = (owner != null && this.distanceToSqr(owner) < 64.0) ? 30 : 60;
		if (this.tickCount % interval == 0) {
			this.heal(1.0F);
			this.lastHealth = this.getHealth();
		}
	}

	/** Периодически записывает положение воина в реестр армии. */
	private void serverTickExtras() {
		if (this.tickCount % 40 == 1 && !this.armyId.isEmpty()) {
			ArmyRegistry.update(this);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && !this.level().isClientSide() && !this.armyId.isEmpty()) {
			ArmyRegistry.remove(this.armyId, this.getUUID());
		}
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	/** Воины одного владельца и сам владелец не друг для друга цели. */
	@Override
	public boolean canAttack(LivingEntity target) {
		if (this.ownerUuid != null) {
			if (this.ownerUuid.equals(target.getUUID())) {
				return false;
			}
			if (target instanceof NullWarrior other && this.ownerUuid.equals(other.ownerUuid)) {
				return false;
			}
		}
		return super.canAttack(target);
	}

	// ---- владелец и приказы ----

	@Nullable
	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	@Nullable
	public Player getOwner() {
		return this.ownerUuid == null ? null : this.level().getPlayerByUUID(this.ownerUuid);
	}

	public void setOwnerUuid(@Nullable UUID uuid) {
		this.ownerUuid = uuid;
	}

	public void setOwner(Player owner) {
		this.ownerUuid = owner.getUUID();
	}

	public Formation getFormation() {
		return this.formation;
	}

	public void setFormation(Formation formation) {
		this.formation = formation;
	}

	public Behavior getBehavior() {
		return this.behavior;
	}

	public void setBehavior(Behavior behavior) {
		this.behavior = behavior;
	}

	public int getSlotIndex() {
		return this.slotIndex;
	}

	public int getSlotCount() {
		return this.slotCount;
	}

	public void setSlot(int index, int count) {
		this.slotIndex = index;
		this.slotCount = Math.max(1, count);
	}

	public String getArmyId() {
		return this.armyId;
	}

	public void setArmyId(String armyId) {
		this.armyId = armyId;
	}

	public int getFollowDistance() {
		return this.followDistance;
	}

	public void setFollowDistance(int distance) {
		this.followDistance = distance;
	}

	public Mode getMode() {
		return Mode.byId(this.entityData.get(DATA_MODE));
	}

	public void setMode(Mode mode) {
		this.entityData.set(DATA_MODE, mode.ordinal());
	}

	@Nullable
	public BlockPos getGuardPos() {
		return this.guardPos;
	}

	public void guardHere(BlockPos pos) {
		this.guardPos = pos;
		this.setMode(Mode.GUARD);
	}

	public void orderAttack(LivingEntity target) {
		this.setTarget(target);
		this.setMode(Mode.ATTACK);
	}

	// ---- сохранение ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Mode", this.getMode().ordinal());
		output.putString("ArmyId", this.armyId);
		output.putInt("Formation", this.formation.ordinal());
		output.putInt("Behavior", this.behavior.ordinal());
		output.putInt("FollowDistance", this.followDistance);
		if (this.ownerUuid != null) {
			output.putString("Owner", this.ownerUuid.toString());
		}
		if (this.guardPos != null) {
			output.putBoolean("HasGuard", true);
			output.putInt("GuardX", this.guardPos.getX());
			output.putInt("GuardY", this.guardPos.getY());
			output.putInt("GuardZ", this.guardPos.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setMode(Mode.byId(input.getIntOr("Mode", 0)));
		this.armyId = input.getStringOr("ArmyId", "");
		this.formation = Formation.byId(input.getIntOr("Formation", 0));
		this.behavior = Behavior.byId(input.getIntOr("Behavior", 1));
		this.followDistance = input.getIntOr("FollowDistance", NullArmy.DEFAULT_DISTANCE);
		String owner = input.getStringOr("Owner", "");
		if (!owner.isEmpty()) {
			try {
				this.ownerUuid = UUID.fromString(owner);
			} catch (IllegalArgumentException ignored) {
				this.ownerUuid = null;
			}
		}
		if (input.getBooleanOr("HasGuard", false)) {
			this.guardPos = new BlockPos(
					input.getIntOr("GuardX", 0),
					input.getIntOr("GuardY", 0),
					input.getIntOr("GuardZ", 0));
		}
	}
}
