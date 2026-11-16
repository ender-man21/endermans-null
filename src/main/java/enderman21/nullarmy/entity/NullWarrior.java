package enderman21.nullarmy.entity;

import enderman21.nullarmy.entity.goal.CommanderSmashGoal;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Воин Null: подчиняется владельцу, у которого есть Тотем Нулла. */
public class NullWarrior extends PathfinderMob {

	public enum Mode {
		FOLLOW, HOLD, GUARD, ATTACK, CURSOR;

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

	/** Ранги из лора: Soldier (отделка netherite), General (redstone), Commander (quartz, один на тотем). */
	public enum Rank {
		SOLDIER, GENERAL, COMMANDER;

		public static Rank byId(int id) {
			Rank[] values = values();
			return id >= 0 && id < values.length ? values[id] : SOLDIER;
		}

		public String translationKey() {
			return "rank.endermans-null." + name().toLowerCase();
		}
	}

	/** Режимы командира: действует с армией (булава), разведка (невидим, подсвечивает врагов), охрана владельца. */
	public enum CommanderMode {
		LEAD, SURVEY, PROTECT;

		public static CommanderMode byId(int id) {
			CommanderMode[] values = values();
			return id >= 0 && id < values.length ? values[id] : LEAD;
		}

		public String translationKey() {
			return "commander.endermans-null." + name().toLowerCase();
		}
	}

	public enum Formation {
		CIRCLE, SQUARE, TRIANGLE, LINE, COLUMN;

		public String translationKey() {
			return "formation.endermans-null." + name().toLowerCase();
		}

		public static Formation byId(int id) {
			Formation[] values = values();
			return values[Math.floorMod(id, values.length)];
		}
	}

	public enum Behavior {
		PASSIVE, NEUTRAL, AGGRESSIVE, RAID, PACIFIST;

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
	private Rank rank = Rank.SOLDIER;
	/** Режим командира (только для ранга COMMANDER): Lead, Survey, Protect. */
	private CommanderMode commanderMode = CommanderMode.LEAD;
	/** Версия выданной экипировки (см. NullGear.VERSION): старым воинам один раз обновляем чары. */
	private int gearVersion = 0;
	private int slotIndex = 0;
	private int slotCount = 1;
	/** Тиков с последнего полученного урона (для регенерации). */
	private int sinceHurt = 0;
	/** Бежит «в припрыжку» (прыгает на каждом шаге, как игрок с bunny hop), пока длится спринт. */
	private boolean hopping = false;

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
		this.goalSelector.addGoal(0, new CommanderSmashGoal(this));
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
			if (this.gearVersion < NullGear.VERSION) {
				NullGear.refresh(this);
			}
			this.regenerate();
			this.playerLikeMovement();
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
	 * Движение как у игрока: вместо ускорения скорости цели воин включает настоящий спринт
	 * (как у игрока, +30% к скорости и частицы) только на длинных отрезках пути или в погоне
	 * за целью, а на бегу иногда прыгает: то одиночным прыжком, то серией прыжков (bunny hop).
	 */
	private void playerLikeMovement() {
		boolean moving = !this.getNavigation().isDone();
		LivingEntity target = this.getTarget();
		boolean want;
		if (target != null && target.isAlive()) {
			want = this.distanceToSqr(target) > 9.0;
		} else if (moving && this.getNavigation().getTargetPos() != null) {
			double dist = Math.sqrt(this.getNavigation().getTargetPos().distToCenterSqr(this.position()));
			// гистерезис: начинает спринт на дальнем пути, прекращает только вблизи цели
			want = dist > (this.isSprinting() ? 3.0 : 8.0);
		} else {
			want = false;
		}
		if (this.isInWater() || this.isPassenger()) {
			want = false;
		}
		if (want != this.isSprinting()) {
			this.setSprinting(want);
			if (want) {
				this.hopping = this.getRandom().nextFloat() < 0.35F;
			}
		}
		if (this.isSprinting() && this.onGround() && this.runningStraight(target)
				&& (this.hopping || this.getRandom().nextInt(70) == 0)) {
			this.getJumpControl().jump();
		}
	}

	/**
	 * Прыгать на бегу можно только на прямой: цель далеко (больше 10 блоков) и воин бежит почти точно
	 * на неё. В воздухе мобом не повернуть, поэтому прыжок в поворотах и рядом с целью даёт занос и зигзаги.
	 */
	private boolean runningStraight(@Nullable LivingEntity target) {
		Vec3 goal;
		if (target != null && target.isAlive()) {
			goal = target.position();
		} else if (this.getNavigation().getTargetPos() != null) {
			goal = Vec3.atCenterOf(this.getNavigation().getTargetPos());
		} else {
			return false;
		}
		double dx = goal.x - this.getX();
		double dz = goal.z - this.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		Vec3 v = this.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		if (dist < 10.0 || speed < 0.05) {
			return false;
		}
		return (dx * v.x + dz * v.z) / (dist * speed) > 0.97;
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
		// Отозванный командир, который был в незагруженном чанке: исчезает при загрузке
		if (this.tickCount % 20 == 5 && this.rank == Rank.COMMANDER && !this.armyId.isEmpty()
				&& !enderman21.nullarmy.CommanderStore.allowed(this.armyId, this.getUUID())) {
			this.discard();
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && !this.level().isClientSide() && !this.armyId.isEmpty()) {
			ArmyRegistry.remove(this.armyId, this.getUUID());
			if (reason == Entity.RemovalReason.KILLED && this.rank == Rank.COMMANDER) {
				enderman21.nullarmy.CommanderStore.onDeath(this.armyId, this.getUUID());
			}
		}
		super.remove(reason);
	}

	/** Пацифист получает урон как обычно, но не отвечает: никакая цель ИИ и никакой приказ не задают ему цель. */
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target != null && this.behavior == Behavior.PACIFIST) {
			return;
		}
		super.setTarget(target);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	/** Воины одного владельца и сам владелец не друг для друга цели. */
	@Override
	public boolean canAttack(LivingEntity target) {
		// Пацифист и разведчик никого не атакуют
		if (this.behavior == Behavior.PACIFIST || this.isSurveying()) {
			return false;
		}
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
		if (behavior == Behavior.PACIFIST) {
			this.setTarget(null);
			if (this.getMode() == Mode.ATTACK) {
				this.setMode(Mode.FOLLOW);
			}
		}
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

	public Rank getRank() {
		return this.rank;
	}

	public boolean isCommander() {
		return this.rank == Rank.COMMANDER;
	}

	public CommanderMode getCommanderMode() {
		return this.commanderMode;
	}

	/** Слушается ли воин приказов армии: командир вне режима Lead действует сам по себе. */
	public boolean obeysArmy() {
		return !this.isCommander() || this.commanderMode == CommanderMode.LEAD;
	}

	public boolean isSurveying() {
		return this.isCommander() && this.commanderMode == CommanderMode.SURVEY;
	}

	/** Меняет режим командира: оружие (булава только в Lead), невидимость и невидимость в Survey. */
	public void setCommanderMode(CommanderMode mode) {
		this.commanderMode = mode;
		if (!this.isCommander()) {
			return;
		}
		boolean survey = mode == CommanderMode.SURVEY;
		this.setInvisible(survey);
		this.setCustomNameVisible(!survey);
		if (mode != CommanderMode.LEAD) {
			this.setTarget(null);
			this.setMode(Mode.FOLLOW);
			this.behavior = Behavior.NEUTRAL;
		}
		NullGear.refresh(this);
		if (!this.level().isClientSide()) {
			ArmyRegistry.update(this);
		}
	}

	@Override
	public boolean isPushable() {
		return !this.isSurveying() && super.isPushable();
	}

	public void setRank(Rank rank) {
		this.rank = rank;
	}

	public void setGearVersion(int gearVersion) {
		this.gearVersion = gearVersion;
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
		output.putInt("Rank", this.rank.ordinal());
		output.putInt("CommanderMode", this.commanderMode.ordinal());
		output.putInt("GearVersion", this.gearVersion);
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
		this.rank = Rank.byId(input.getIntOr("Rank", 0));
		this.commanderMode = CommanderMode.byId(input.getIntOr("CommanderMode", 0));
		this.gearVersion = input.getIntOr("GearVersion", 0);
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
