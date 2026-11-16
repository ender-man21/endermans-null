package enderman21.nullarmy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Настройки мода: config/endermans-null.json (создаётся при первом запуске, правится вручную, нужен перезапуск).
 * Значения читаются при первом обращении к классу, до остального кода мода.
 */
public final class NullConfig {
	/** Максимум обычных воинов в одной армии (командир не считается). */
	public static int maxArmy = 20;
	/** Цена призыва одного воина (уровни опыта). */
	public static int levelCost = 5;
	/** Цена первого призыва командира (уровни опыта); повторное включение бесплатно. */
	public static int commanderCost = 15;
	/** Каждый N-й воин армии призывается генералом. */
	public static int generalEvery = 10;
	/** Максимальная дальность точки в режиме «За курсором» (блоки). */
	public static double cursorRange = 100.0;
	/** Перезарядка «Позвать Нулл» (секунды). */
	public static int callCooldownSeconds = 30;
	/** В поведении «Налёт» воины нападают и на игроков (кроме владельца и своих). */
	public static boolean raidAttacksPlayers = false;
	/** Радиус, в котором воины сами ищут цели (Агрессивное и Налёт), блоки. */
	public static double targetSearchRange = 16.0;
	/** Командир в режиме Protect принимает смертельный удар за владельца, если ближе этого расстояния (блоки). */
	public static double protectRange = 32.0;
	/** Дальность подсветки врагов в режиме Survey (блоки). */
	public static double surveyRange = 48.0;
	/** Командир в режиме Survey неуязвим (по умолчанию нет: невидимого командира можно убить). */
	public static boolean surveyInvulnerable = false;
	/** Перезарядка блинк-удара командира (секунды). */
	public static double smashCooldownSeconds = 8.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static {
		load();
	}

	private NullConfig() {
	}

	private static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("endermans-null.json");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Snapshot s = GSON.fromJson(reader, Snapshot.class);
				if (s != null) {
					maxArmy = clamp(s.maxArmy, 1, 200, maxArmy);
					levelCost = clamp(s.levelCost, 0, 100, levelCost);
					commanderCost = clamp(s.commanderCost, 0, 100, commanderCost);
					generalEvery = clamp(s.generalEvery, 1, 1000, generalEvery);
					cursorRange = clamp(s.cursorRange, 10.0, 256.0, cursorRange);
					callCooldownSeconds = clamp(s.callCooldownSeconds, 0, 3600, callCooldownSeconds);
					raidAttacksPlayers = s.raidAttacksPlayers;
					targetSearchRange = clamp(s.targetSearchRange, 4.0, 64.0, targetSearchRange);
					protectRange = clamp(s.protectRange, 4.0, 128.0, protectRange);
					surveyRange = clamp(s.surveyRange, 8.0, 128.0, surveyRange);
					surveyInvulnerable = s.surveyInvulnerable;
					smashCooldownSeconds = clamp(s.smashCooldownSeconds, 0.5, 300.0, smashCooldownSeconds);
				}
			} catch (Exception e) {
				EndermanSNull.LOGGER.error("Could not read config, using defaults", e);
			}
		}
		save(file);
	}

	/** Перезаписываем файл: так в нём появляются новые настройки после обновления мода. */
	private static void save(Path file) {
		Snapshot s = new Snapshot();
		s.maxArmy = maxArmy;
		s.levelCost = levelCost;
		s.commanderCost = commanderCost;
		s.generalEvery = generalEvery;
		s.cursorRange = cursorRange;
		s.callCooldownSeconds = callCooldownSeconds;
		s.raidAttacksPlayers = raidAttacksPlayers;
		s.targetSearchRange = targetSearchRange;
		s.protectRange = protectRange;
		s.surveyRange = surveyRange;
		s.surveyInvulnerable = surveyInvulnerable;
		s.smashCooldownSeconds = smashCooldownSeconds;
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(s, writer);
		} catch (IOException e) {
			EndermanSNull.LOGGER.error("Could not write config", e);
		}
	}

	/** Формат файла: значения по умолчанию совпадают с полями выше, отсутствующие ключи остаются по умолчанию. */
	private static final class Snapshot {
		int maxArmy = NullConfig.maxArmy;
		int levelCost = NullConfig.levelCost;
		int commanderCost = NullConfig.commanderCost;
		int generalEvery = NullConfig.generalEvery;
		double cursorRange = NullConfig.cursorRange;
		int callCooldownSeconds = NullConfig.callCooldownSeconds;
		boolean raidAttacksPlayers = NullConfig.raidAttacksPlayers;
		double targetSearchRange = NullConfig.targetSearchRange;
		double protectRange = NullConfig.protectRange;
		double surveyRange = NullConfig.surveyRange;
		boolean surveyInvulnerable = NullConfig.surveyInvulnerable;
		double smashCooldownSeconds = NullConfig.smashCooldownSeconds;
	}

	private static int clamp(int v, int min, int max, int fallback) {
		return v < min || v > max ? fallback : v;
	}

	private static double clamp(double v, double min, double max, double fallback) {
		return Double.isNaN(v) || v < min || v > max ? fallback : v;
	}
}
