package enderman21.nullarmy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Командир как отдельная от армии сущность: хранится по id тотема (армии).
 * paid: первый призыв оплачен (15 уровней), дальше включение и выключение бесплатны; со смертью оплата сгорает.
 * active: UUID включённого командира; командир, чей UUID не совпал (отозванный в незагруженном чанке), исчезает при загрузке.
 */
public final class CommanderStore {
	public static final class Rec {
		public boolean paid;
		public String active;
		public int mode;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, Rec>>() {
	}.getType();

	private static Map<String, Rec> data = new HashMap<>();
	private static Path file;

	private CommanderStore() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(CommanderStore::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
	}

	private static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("endermans-null-commanders.json");
		data = new HashMap<>();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, Rec> loaded = GSON.fromJson(reader, TYPE);
				if (loaded != null) {
					data = loaded;
				}
			} catch (Exception e) {
				EndermanSNull.LOGGER.error("Could not read commander store", e);
			}
		}
	}

	private static void save() {
		if (file == null) {
			return;
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(data, TYPE, writer);
		} catch (IOException e) {
			EndermanSNull.LOGGER.error("Could not save commander store", e);
		}
	}

	public static Rec get(String armyId) {
		return data.get(armyId);
	}

	private static Rec rec(String armyId) {
		return data.computeIfAbsent(armyId, k -> new Rec());
	}

	/** Первый призыв уже оплачен? Для старых миров (записи нет, командир уже был) считаем оплаченным. */
	public static boolean isPaid(String armyId) {
		Rec r = data.get(armyId);
		return r != null ? r.paid : ArmyRegistry.commanderCount(armyId) > 0;
	}

	/** Командир включён (в мире или в незагруженном чанке). */
	public static boolean isActive(String armyId) {
		Rec r = data.get(armyId);
		return r != null ? r.active != null : ArmyRegistry.commanderCount(armyId) > 0;
	}

	/** Этому ли командиру разрешено существовать (старые миры без записи разрешают). */
	public static boolean allowed(String armyId, UUID uuid) {
		Rec r = data.get(armyId);
		return r == null || uuid.toString().equals(r.active);
	}

	public static int mode(String armyId) {
		Rec r = data.get(armyId);
		return r == null ? 0 : r.mode;
	}

	public static void activate(String armyId, UUID uuid) {
		Rec r = rec(armyId);
		r.paid = true;
		r.active = uuid.toString();
		save();
	}

	public static void deactivate(String armyId) {
		Rec r = rec(armyId);
		r.active = null;
		save();
	}

	public static void setMode(String armyId, int mode) {
		rec(armyId).mode = mode;
		save();
	}

	/** Командир погиб: оплата сгорает, следующий призыв снова стоит уровни. */
	public static void onDeath(String armyId, UUID uuid) {
		Rec r = data.get(armyId);
		if (r != null && uuid.toString().equals(r.active)) {
			r.active = null;
			r.paid = false;
			save();
		}
	}
}
