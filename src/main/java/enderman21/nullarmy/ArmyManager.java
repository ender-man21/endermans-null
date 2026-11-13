package enderman21.nullarmy;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Связь армии с тотемом. Армия жива, пока её Тотем Нулла существует в мире:
 * лежит в инвентаре игрока или валяется предметом на земле.
 * Кто держит тотем в инвентаре, тот и командует: воины сразу переходят к нему.
 * Тотем пропал (сработал, сгорел, удалён, исчез из-за проклятья) на несколько секунд, и вся армия гибнет.
 * Если владелец не в сети, отсчёт стоит на паузе.
 */
public final class ArmyManager {
	private static final int INTERVAL_TICKS = 10;
	/** Сколько тиков армия живёт без тотема (5 секунд). */
	public static final int GRACE_TICKS = 100;

	private static final Map<String, Integer> MISSING = new HashMap<>();

	public static int missingTicks(String armyId) {
		return MISSING.getOrDefault(armyId, 0);
	}

	public static void resetState() {
		MISSING.clear();
	}

	private ArmyManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ArmyManager::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL_TICKS != 0) {
			return;
		}

		Map<String, List<NullWarrior>> armies = new HashMap<>();
		Set<String> looseTotems = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof NullWarrior w && w.isAlive()) {
					String id = w.getArmyId();
					if (id.isEmpty()) {
						id = adopt(server, w);
					}
					if (!id.isEmpty()) {
						armies.computeIfAbsent(id, k -> new ArrayList<>()).add(w);
					}
				} else if (e instanceof ItemEntity item && NullArmy.isNullTotem(item.getItem())) {
					String id = NullArmy.armyIdOf(item.getItem());
					if (!id.isEmpty()) {
						looseTotems.add(id);
					}
				}
			}
		}
		for (List<NullWarrior> list : armies.values()) {
			list.sort(java.util.Comparator.comparing(NullWarrior::getUUID));
			for (int i = 0; i < list.size(); i++) {
				list.get(i).setSlot(i, list.size());
			}
		}
		if (server.getTickCount() % 40 == 0) {
			syncRegistry(server, armies);
		}
		if (armies.isEmpty()) {
			MISSING.clear();
			return;
		}

		Map<String, ServerPlayer> holders = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				register(holders, inv.getItem(i), player);
			}
			register(holders, player.containerMenu.getCarried(), player);
		}

		for (Map.Entry<String, List<NullWarrior>> entry : armies.entrySet()) {
			String id = entry.getKey();
			List<NullWarrior> warriors = entry.getValue();
			ServerPlayer holder = holders.get(id);
			if (holder != null) {
				MISSING.remove(id);
				transfer(warriors, holder);
			} else if (looseTotems.contains(id)) {
				MISSING.remove(id);
			} else {
				UUID owner = warriors.get(0).getOwnerUuid();
				if (owner != null && server.getPlayerList().getPlayer(owner) == null) {
					continue; // владелец оффлайн: тотем в его инвентаре, отсчёт на паузе
				}
				int missing = MISSING.merge(id, INTERVAL_TICKS, Integer::sum);
				if (missing >= GRACE_TICKS) {
					MISSING.remove(id);
					disband(server, warriors, owner);
				}
			}
		}
		MISSING.keySet().removeIf(id -> !armies.containsKey(id));
	}

	/** Обновляет позиции загруженных воинов и вычищает погибших из реестра. */
	private static void syncRegistry(MinecraftServer server, Map<String, List<NullWarrior>> armies) {
		Set<UUID> seen = new HashSet<>();
		for (List<NullWarrior> list : armies.values()) {
			for (NullWarrior w : list) {
				seen.add(w.getUUID());
				ArmyRegistry.update(w);
			}
		}
		ArmyRegistry.prune(server, seen);
	}

	private static void register(Map<String, ServerPlayer> holders, ItemStack stack, ServerPlayer player) {
		if (NullArmy.isNullTotem(stack)) {
			String id = NullArmy.armyIdOf(stack);
			if (!id.isEmpty()) {
				holders.putIfAbsent(id, player);
			}
		}
	}

	/** Воин без id (из старой версии мода): привязываем к тотему его владельца, если тот в сети. */
	private static String adopt(MinecraftServer server, NullWarrior warrior) {
		UUID owner = warrior.getOwnerUuid();
		if (owner == null) {
			return "";
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null) {
			return "";
		}
		ItemStack totem = NullArmy.firstTotem(player);
		if (totem.isEmpty()) {
			return "";
		}
		String id = NullArmy.ensureArmyId(totem);
		warrior.setArmyId(id);
		return id;
	}

	/** Тотем у другого игрока: вся армия сразу переходит к нему. */
	private static void transfer(List<NullWarrior> warriors, ServerPlayer holder) {
		int count = 0;
		for (NullWarrior w : warriors) {
			if (holder.getUUID().equals(w.getOwnerUuid())) {
				continue;
			}
			w.setOwner(holder);
			w.setMode(NullWarrior.Mode.FOLLOW);
			if (w.getTarget() == holder) {
				w.setTarget(null);
			}
			count++;
		}
		if (count > 0) {
			holder.sendSystemMessage(Component.translatable("message.endermans-null.army_captured", count), true);
		}
	}

	private static void disband(MinecraftServer server, List<NullWarrior> warriors, UUID owner) {
		for (NullWarrior w : warriors) {
			if (w.level() instanceof ServerLevel level) {
				NullArmy.vanish(w, level);
			}
		}
		if (owner != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message.endermans-null.army_lost"), true);
			}
		}
	}
}
