package enderman21.nullarmy;

import enderman21.nullarmy.entity.NullGear;
import enderman21.nullarmy.entity.NullWarrior;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/** Вся серверная логика армии игрока: тотем, учёт, призыв, приказы, передача. */
public final class NullArmy {
	public static final int MAX_ARMY = 20;
	public static final int LEVEL_COST = 5;
	/** Границы дистанции, на которой воины держатся от владельца (блоки). */
	public static final int MIN_DISTANCE = 3;
	public static final int MAX_DISTANCE = 16;
	public static final int DEFAULT_DISTANCE = 6;

	private static final String TOTEM_KEY = "NullArmy";

	private NullArmy() {
	}

	// ---- тотем: обычный Тотем бессмертия с проклятьем утраты (канон) ----

	public static boolean isNullTotem(ItemStack stack) {
		if (!stack.is(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
			if (enchantment.is(Enchantments.VANISHING_CURSE)) {
				return true;
			}
		}
		return false;
	}

	public static ItemStack createTotem(RegistryAccess registries) {
		ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);
		stack.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE), 1);
		return stack;
	}

	/** Скрытый id армии на тотеме ("" если ещё не привязан). */
	public static String armyIdOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		return data.copyTag().getStringOr(TOTEM_KEY, "");
	}

	/** Привязывает тотем к новой армии, если он ещё не привязан; возвращает id. */
	public static String ensureArmyId(ItemStack stack) {
		String id = armyIdOf(stack);
		if (id.isEmpty()) {
			final String fresh = UUID.randomUUID().toString();
			stack.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY.update(tag -> tag.putString(TOTEM_KEY, fresh)));
			id = fresh;
		}
		return id;
	}

	/** Первый Тотем Нулла в инвентаре (или пустой стек). */
	public static ItemStack firstTotem(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (isNullTotem(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Тотем где угодно в инвентаре даёт право командовать. */
	public static boolean hasTotem(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isNullTotem(inv.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	// ---- учёт ----

	/** Все загруженные воины игрока во всех измерениях. */
	public static List<NullWarrior> warriorsOf(MinecraftServer server, UUID owner) {
		List<NullWarrior> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof NullWarrior w && w.isAlive() && owner.equals(w.getOwnerUuid())) {
					result.add(w);
				}
			}
		}
		return result;
	}

	/** Все загруженные воины одной армии (по id тотема). */
	public static List<NullWarrior> warriorsOfArmy(MinecraftServer server, String armyId) {
		List<NullWarrior> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof NullWarrior w && w.isAlive() && armyId.equals(w.getArmyId())) {
					result.add(w);
				}
			}
		}
		return result;
	}

	// ---- призыв ----

	/** Ритуал: проверка условий, оплата, призыв. Вызывается на сервере. */
	public static boolean tryChargeSummon(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		ItemStack totem = player.getMainHandItem();
		if (!isNullTotem(totem)) {
			return false;
		}
		String armyId = ensureArmyId(totem);
		if (ArmyRegistry.count(armyId) >= MAX_ARMY) {
			player.sendSystemMessage(Component.translatable("message.endermans-null.army_full", MAX_ARMY), true);
			return false;
		}
		boolean free = player.isCreative();
		if (!free && player.experienceLevel < LEVEL_COST) {
			player.sendSystemMessage(Component.translatable("message.endermans-null.need_levels", LEVEL_COST), true);
			return false;
		}
		NullWarrior warrior = summon(player, armyId);
		if (warrior == null) {
			return false;
		}
		if (!free) {
			player.giveExperienceLevels(-LEVEL_COST);
		}
		return true;
	}

	/** Создаёт воина перед игроком и отдаёт ему во владение (без оплаты). */
	public static NullWarrior summon(ServerPlayer player, String armyId) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		NullWarrior warrior = ModEntities.NULL_WARRIOR.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (warrior == null) {
			return null;
		}
		Vec3 look = player.getLookAngle();
		double x = player.getX() + look.x * 2.0;
		double z = player.getZ() + look.z * 2.0;
		double y = player.getY();

		warrior.setPos(x, y, z);
		warrior.setYRot(player.getYRot() + 180.0F);
		warrior.setOwner(player);
		warrior.setArmyId(armyId);
		NullGear.equip(warrior);
		level.addFreshEntity(warrior);
		ArmyRegistry.update(warrior);

		level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 60, 0.4, 0.9, 0.4, 0.4);
		level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.6F);
		return warrior;
	}

	// ---- приказы ----

	public static void setMode(ServerPlayer owner, NullWarrior.Mode mode) {
		MinecraftServer server = owner.level().getServer();
		if (server == null) {
			return;
		}
		net.minecraft.core.BlockPos flag = mode == NullWarrior.Mode.GUARD ? FlagManager.plant(owner) : null;
		for (NullWarrior w : warriorsOf(server, owner.getUUID())) {
			if (flag != null) {
				w.guardHere(flag);
			} else {
				w.setMode(mode);
			}
		}
	}

	public static void setFormation(ServerPlayer owner, NullWarrior.Formation formation) {
		MinecraftServer server = owner.level().getServer();
		if (server != null) {
			for (NullWarrior w : warriorsOf(server, owner.getUUID())) {
				w.setFormation(formation);
			}
		}
	}

	public static void setBehavior(ServerPlayer owner, NullWarrior.Behavior behavior) {
		MinecraftServer server = owner.level().getServer();
		if (server != null) {
			for (NullWarrior w : warriorsOf(server, owner.getUUID())) {
				w.setBehavior(behavior);
			}
		}
	}

	public static void orderAttack(ServerPlayer owner, LivingEntity target) {
		MinecraftServer server = owner.level().getServer();
		if (server == null) {
			return;
		}
		for (NullWarrior w : warriorsOf(server, owner.getUUID())) {
			if (w.canAttack(target)) {
				w.orderAttack(target);
			}
		}
	}

	public static void setDistance(ServerPlayer owner, int distance) {
		MinecraftServer server = owner.level().getServer();
		if (server == null) {
			return;
		}
		int clamped = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
		for (NullWarrior w : warriorsOf(server, owner.getUUID())) {
			w.setFollowDistance(clamped);
		}
	}

	// ---- исчезновение ----

	/** Воин телепортируется прочь: частицы, звук, ничего не выпадает. */
	public static void vanish(NullWarrior warrior, ServerLevel level) {
		double x = warrior.getX();
		double y = warrior.getY();
		double z = warrior.getZ();
		level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 60, 0.5, 1.0, 0.5, 0.5);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.0, z, 30, 0.4, 1.0, 0.4, 0.08);
		level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4F, 0.6F);
		warrior.discard();
	}
}
