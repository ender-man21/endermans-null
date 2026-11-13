package enderman21.nullarmy;

import com.mojang.brigadier.context.CommandContext;
import enderman21.nullarmy.entity.NullWarrior;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** /null debug (состояние армий) и /null debug reset (полный сброс мода в мире). */
public final class NullDebug {
	private NullDebug() {
	}

	private static String shortId(String id) {
		return id.length() > 8 ? id.substring(0, 8) : id;
	}

	public static int report(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		MinecraftServer server = src.getServer();

		Map<String, Integer> loaded = new HashMap<>();
		Set<String> loose = new HashSet<>();
		int noId = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof NullWarrior w && w.isAlive()) {
					if (w.getArmyId().isEmpty()) {
						noId++;
					} else {
						loaded.merge(w.getArmyId(), 1, Integer::sum);
					}
				} else if (e instanceof ItemEntity item && NullArmy.isNullTotem(item.getItem())) {
					String id = NullArmy.armyIdOf(item.getItem());
					if (!id.isEmpty()) {
						loose.add(id);
					}
				}
			}
		}

		Map<String, String> holders = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (NullArmy.isNullTotem(stack) && !NullArmy.armyIdOf(stack).isEmpty()) {
					holders.merge(NullArmy.armyIdOf(stack), player.getName().getString(), (a, b) -> a + "," + b);
				}
			}
		}

		Set<String> ids = new HashSet<>(loaded.keySet());
		ids.addAll(ArmyRegistry.ids());
		ids.addAll(holders.keySet());
		ids.addAll(loose);

		List<String> lines = new ArrayList<>();
		lines.add("Null debug: armies " + ids.size() + ", flags " + FlagManager.count()
				+ ", warriors without army id " + noId);
		for (String id : ids) {
			lines.add(shortId(id) + ": loaded " + loaded.getOrDefault(id, 0)
					+ ", registry " + ArmyRegistry.count(id)
					+ ", holder " + holders.getOrDefault(id, "none")
					+ ", loose totem " + (loose.contains(id) ? "yes" : "no")
					+ ", missing " + ArmyManager.missingTicks(id) + "/" + ArmyManager.GRACE_TICKS + " ticks");
		}
		for (String line : lines) {
			src.sendSuccess(() -> Component.literal(line), false);
		}
		return ids.size();
	}

	public static int reset(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		MinecraftServer server = src.getServer();

		List<NullWarrior> all = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof NullWarrior w) {
					all.add(w);
				}
			}
		}
		for (NullWarrior w : all) {
			if (w.level() instanceof ServerLevel level) {
				NullArmy.vanish(w, level);
			}
		}
		ArmyCall.clearAll();
		int flags = FlagManager.clearAll(server);
		ArmyRegistry.clear();
		ArmyManager.resetState();

		int warriors = all.size();
		src.sendSuccess(() -> Component.literal("Null reset: removed " + warriors + " loaded warriors, "
				+ flags + " flags; registry, cooldowns and pending calls cleared. "
				+ "Warriors in unloaded chunks vanish when they load."), true);
		return warriors;
	}
}
