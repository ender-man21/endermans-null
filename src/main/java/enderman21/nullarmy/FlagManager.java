package enderman21.nullarmy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import enderman21.nullarmy.entity.NullWarrior;
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
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Стоячее знамя: невидимая стойка для брони с Знаменем Нулла на голове.
 * Ставится режимом «Гулять» туда, куда смотрит игрок; у армии один флаг, новый заменяет старый.
 * Забрать (ПКМ) может только владелец, после этого армия возвращается в режим «Следовать».
 *
 * Актуальный флаг каждого владельца записан в файл мира. Если прежний флаг стоял в незагруженной
 * области, он исчезнет сам, как только его чанк загрузится (он больше не актуален).
 */
public final class FlagManager {
	/** Флаг опознаётся по имени стойки: префикс + UUID владельца (имя не показывается). */
	private static final String PREFIX = "endermans_null_flag:";
	private static final Gson GSON = new Gson();
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	/** UUID владельца -> UUID его актуальной стойки. */
	private static Map<String, String> current = new HashMap<>();
	private static Path file;

	private FlagManager() {
	}

	private static boolean isFlag(Entity e) {
		return e.getCustomName() != null && e.getCustomName().getString().startsWith(PREFIX);
	}

	private static String ownerOf(Entity e) {
		return e.getCustomName().getString().substring(PREFIX.length());
	}

	private static boolean ownedBy(Entity e, UUID owner) {
		return isFlag(e) && ownerOf(e).equals(owner.toString());
	}

	private static boolean isStale(Entity e) {
		return !e.getUUID().toString().equals(current.get(ownerOf(e)));
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(FlagManager::load);

		// Устаревшие флаги (старые, оставшиеся в незагруженной области) удаляем, когда их чанк загружен:
		// проверка раз в 2 секунды по тику сервера, а не на загрузке каждой сущности
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0 || current == null) {
				return;
			}
			java.util.List<Entity> stale = new java.util.ArrayList<>();
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity e : level.getAllEntities()) {
					if (isFlag(e) && isStale(e)) {
						stale.add(e);
					}
				}
			}
			for (Entity e : stale) {
				e.discard();
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				isFlag(entity) ? InteractionResult.FAIL : InteractionResult.PASS);

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!isFlag(entity)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp && ownedBy(entity, sp.getUUID())) {
				if (sp.level() instanceof ServerLevel serverLevel) {
					puff(serverLevel, entity.position());
				}
				entity.discard();
				current.remove(sp.getUUID().toString());
				save();
				NullArmy.setMode(sp, NullWarrior.Mode.FOLLOW);
				sp.sendSystemMessage(Component.translatable("message.endermans-null.flag_taken"), true);
			} else if (player instanceof ServerPlayer other) {
				other.sendSystemMessage(Component.translatable("message.endermans-null.flag_foreign"), true);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("endermans-null-flags.json");
		current = new HashMap<>();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, String> loaded = GSON.fromJson(reader, TYPE);
				if (loaded != null) {
					current = loaded;
				}
			} catch (Exception e) {
				EndermanSNull.LOGGER.error("Could not read flag registry", e);
			}
		}
	}

	private static void save() {
		if (file == null) {
			return;
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(current, TYPE, writer);
		} catch (Exception e) {
			EndermanSNull.LOGGER.error("Could not save flag registry", e);
		}
	}

	/** Ставит флаг туда, куда смотрит владелец (заменяя прежний); возвращает позицию флага. */
	public static BlockPos plant(ServerPlayer owner) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return owner.blockPosition();
		}
		removeLoadedFlagOf(level, owner.getUUID());

		Vec3 pos = ArmyCall.aim(level, owner);
		var standType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "armor_stand"));
		Entity created = standType == null ? null : standType.create(level, EntitySpawnReason.TRIGGERED);
		if (!(created instanceof ArmorStand stand)) {
			return BlockPos.containing(pos);
		}
		stand.setPos(pos.x, pos.y, pos.z);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.NULL_BANNER));
		stand.setCustomName(Component.literal(PREFIX + owner.getUUID()));

		// Сначала записываем актуальный флаг (старый в незагруженной области станет устаревшим), потом создаём
		current.put(owner.getUUID().toString(), stand.getUUID().toString());
		save();
		level.addFreshEntity(stand);
		puff(level, pos);
		return BlockPos.containing(pos);
	}

	public static int count() {
		return current.size();
	}

	/** Убирает все флаги (загруженные) и забывает записи. */
	public static int clearAll(MinecraftServer server) {
		int removed = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (isFlag(e)) {
					e.discard();
					removed++;
				}
			}
		}
		current.clear();
		save();
		return removed;
	}

	/** Убирает сразу те старые флаги владельца, которые сейчас загружены. */
	private static void removeLoadedFlagOf(ServerLevel origin, UUID owner) {
		for (ServerLevel level : origin.getServer().getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (ownedBy(e, owner)) {
					e.discard();
				}
			}
		}
	}

	private static void puff(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 0.8, 0.3, 0.3);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
	}
}
