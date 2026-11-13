package enderman21.nullarmy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import enderman21.nullarmy.entity.NullWarrior;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Реестр армий: id армии -> воины (UUID, измерение, последняя известная позиция).
 * Нужен, чтобы знать о воинах в незагруженных чанках (лимит, зов).
 * Хранится в файле endermans-null-army.json в папке мира.
 */
public final class ArmyRegistry {
	public static final class Entry {
		public String dim = "";
		public double x;
		public double y;
		public double z;
		/** Сколько проверок подряд воина не нашли в загруженном чанке. */
		public int missed;

		public Entry() {
		}

		public Entry(String dim, double x, double y, double z) {
			this.dim = dim;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, Map<String, Entry>>>() {
	}.getType();

	private static Map<String, Map<String, Entry>> data = new HashMap<>();
	private static Path file;
	private static boolean dirty;

	private ArmyRegistry() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(ArmyRegistry::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (dirty && server.getTickCount() % 1200 == 0) {
				save();
			}
		});
	}

	private static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("endermans-null-army.json");
		data = new HashMap<>();
		dirty = false;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, Map<String, Entry>> loaded = GSON.fromJson(reader, TYPE);
				if (loaded != null) {
					data = loaded;
				}
			} catch (Exception e) {
				EndermanSNull.LOGGER.error("Could not read army registry", e);
			}
		}
	}

	private static void save() {
		if (file == null) {
			return;
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(data, TYPE, writer);
			dirty = false;
		} catch (IOException e) {
			EndermanSNull.LOGGER.error("Could not save army registry", e);
		}
	}

	/** Строковый ключ измерения (стабилен в рамках версии, API не нужен). */
	public static String dimKey(ServerLevel level) {
		return level.dimension().toString();
	}

	public static void update(NullWarrior warrior) {
		String id = warrior.getArmyId();
		if (id.isEmpty() || !(warrior.level() instanceof ServerLevel level)) {
			return;
		}
		data.computeIfAbsent(id, k -> new HashMap<>())
				.put(warrior.getUUID().toString(), new Entry(dimKey(level), warrior.getX(), warrior.getY(), warrior.getZ()));
		dirty = true;
	}

	/** Вычищает воинов, которых нет в загруженном чанке в записанной позиции три проверки подряд. */
	public static void prune(MinecraftServer server, java.util.Set<UUID> seen) {
		for (Map.Entry<String, Map<String, Entry>> army : data.entrySet()) {
			java.util.Iterator<Map.Entry<String, Entry>> it = army.getValue().entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, Entry> en = it.next();
				UUID id;
				try {
					id = UUID.fromString(en.getKey());
				} catch (IllegalArgumentException ex) {
					it.remove();
					dirty = true;
					continue;
				}
				if (seen.contains(id)) {
					en.getValue().missed = 0;
					continue;
				}
				Entry e = en.getValue();
				for (ServerLevel level : server.getAllLevels()) {
					if (dimKey(level).equals(e.dim)) {
						if (level.hasChunk(((int) Math.floor(e.x)) >> 4, ((int) Math.floor(e.z)) >> 4) && ++e.missed >= 3) {
							it.remove();
							dirty = true;
						}
						break;
					}
				}
			}
		}
		data.values().removeIf(Map::isEmpty);
	}

	public static void remove(String armyId, UUID warrior) {
		Map<String, Entry> army = data.get(armyId);
		if (army != null) {
			army.remove(warrior.toString());
			if (army.isEmpty()) {
				data.remove(armyId);
			}
			dirty = true;
		}
	}

	public static java.util.Set<String> ids() {
		return new java.util.HashSet<>(data.keySet());
	}

	public static void clear() {
		data.clear();
		dirty = true;
		save();
	}

	public static int count(String armyId) {
		Map<String, Entry> army = data.get(armyId);
		return army == null ? 0 : army.size();
	}

	public static Map<String, Entry> entries(String armyId) {
		Map<String, Entry> army = data.get(armyId);
		return army == null ? new HashMap<>() : new HashMap<>(army);
	}
}
