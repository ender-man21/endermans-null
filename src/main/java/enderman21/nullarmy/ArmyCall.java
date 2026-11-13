package enderman21.nullarmy;

import enderman21.nullarmy.entity.NullWarrior;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Зов Нулла: с неба падают эндер-жемчуги, и воины телепортируются туда, где жемчуг упал.
 * Воины в незагруженных чанках находятся по реестру: чанк временно подгружается.
 * Работает и между измерениями.
 */
public final class ArmyCall {
	public static final int COOLDOWN_TICKS = 600;
	private static final int LOAD_TIMEOUT = 100;
	private static final double RANGE = 64.0;

	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final List<Task> TASKS = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private static final class Task {
		UUID warrior;
		ServerLevel src;
		int cx;
		int cz;
		ServerLevel dest;
		Vec3 target;
		String armyId;
		int delay;
		int age;
		int waited;
		boolean forced;
		int fallLeft = -1;
		Vec3 landing;
		Entity pearl;
	}

	private ArmyCall() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ArmyCall::tick);
	}

	public static void request(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) {
			long left = (COOLDOWN_TICKS - (now - last) + 19) / 20;
			player.sendSystemMessage(Component.translatable("message.endermans-null.call_cooldown", left), true);
			return;
		}

		Set<String> ids = new HashSet<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (NullArmy.isNullTotem(stack) && !NullArmy.armyIdOf(stack).isEmpty()) {
				ids.add(NullArmy.armyIdOf(stack));
			}
		}

		Vec3 target = aim(level, player);
		int index = 0;
		for (String id : ids) {
			for (Map.Entry<String, ArmyRegistry.Entry> e : ArmyRegistry.entries(id).entrySet()) {
				ServerLevel src = levelByKey(server, e.getValue().dim);
				if (src == null) {
					continue;
				}
				Task t = new Task();
				try {
					t.warrior = UUID.fromString(e.getKey());
				} catch (IllegalArgumentException ex) {
					continue;
				}
				t.src = src;
				t.cx = ((int) Math.floor(e.getValue().x)) >> 4;
				t.cz = ((int) Math.floor(e.getValue().z)) >> 4;
				t.dest = level;
				t.target = target;
				t.armyId = id;
				t.delay = index * 2;
				TASKS.add(t);
				index++;
			}
		}
		if (index == 0) {
			player.sendSystemMessage(Component.translatable("message.endermans-null.call_nobody"), true);
			return;
		}
		LAST.put(player.getUUID(), now);
		player.sendSystemMessage(Component.translatable("message.endermans-null.army_called", index), true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	/** Куда смотрит игрок; в небо или в пустоту: вокруг самого игрока. */
	static Vec3 aim(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return player.position();
		}
		return hit.getLocation().subtract(look.scale(0.6));
	}

	private static ServerLevel levelByKey(MinecraftServer server, String key) {
		for (ServerLevel level : server.getAllLevels()) {
			if (ArmyRegistry.dimKey(level).equals(key)) {
				return level;
			}
		}
		return null;
	}

	/** Сброс: отменяет все зовы в процессе (снимая принудительную загрузку чанков) и кулдауны. */
	public static void clearAll() {
		for (Task t : TASKS) {
			if (t.pearl != null && !t.pearl.isRemoved()) {
				t.pearl.discard();
			}
			release(t);
		}
		TASKS.clear();
		LAST.clear();
	}

	private static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		Iterator<Task> it = TASKS.iterator();
		while (it.hasNext()) {
			if (step(it.next())) {
				it.remove();
			}
		}
	}

	/** true когда задача закончена. */
	private static boolean step(Task t) {
		t.age++;
		if (t.age <= t.delay) {
			return false;
		}
		if (t.fallLeft < 0) {
			Entity e = t.src.getEntity(t.warrior);
			if (!(e instanceof NullWarrior warrior)) {
				if (!t.forced) {
					t.src.setChunkForced(t.cx, t.cz, true);
					t.forced = true;
				}
				if (++t.waited > LOAD_TIMEOUT) {
					if (t.src.hasChunk(t.cx, t.cz)) {
						ArmyRegistry.remove(t.armyId, t.warrior); // воина там нет
					}
					release(t);
					return true;
				}
				return false;
			}
			launch(t, warrior);
			return false;
		}
		if (--t.fallLeft > 0) {
			return false;
		}
		land(t);
		release(t);
		return true;
	}

	private static void launch(Task t, NullWarrior warrior) {
		double angle = RANDOM.nextDouble() * Math.PI * 2.0;
		double radius = 1.0 + RANDOM.nextDouble() * 3.5;
		Vec3 spot = new Vec3(t.target.x + Math.cos(angle) * radius, t.target.y, t.target.z + Math.sin(angle) * radius);
		t.landing = findSafe(t.dest, warrior, spot);

		int free = 0;
		BlockPos base = BlockPos.containing(t.landing);
		while (free < 24 && t.dest.getBlockState(base.above(free + 2)).isAir()) {
			free++;
		}
		double height = Math.max(4, free);

		// время падения при гравитации жемчуга (0.03 за тик, сопротивление 0.99)
		int ticks = 0;
		double fallen = 0;
		double v = 0.1;
		while (fallen < height && ticks < 200) {
			fallen += v;
			v = (v + 0.03) * 0.99;
			ticks++;
		}
		t.fallLeft = Math.max(ticks, 5);

		EntityType<?> pearlType = BuiltInRegistries.ENTITY_TYPE.getValue(
				Identifier.fromNamespaceAndPath("minecraft", "ender_pearl"));
		Entity pearl = pearlType == null ? null : pearlType.create(t.dest, EntitySpawnReason.TRIGGERED);
		if (pearl != null) {
			pearl.setPos(t.landing.x, t.landing.y + height, t.landing.z);
			pearl.setDeltaMovement(0.0, -0.1, 0.0);
			t.dest.addFreshEntity(pearl);
			t.pearl = pearl;
		}
		warrior.getNavigation().stop();
	}

	private static void land(Task t) {
		if (t.pearl != null && !t.pearl.isRemoved()) {
			t.pearl.discard();
		}
		Entity e = t.src.getEntity(t.warrior);
		if (!(e instanceof NullWarrior warrior)) {
			return;
		}
		puff(t.src, warrior.position());
		Entity result = warrior;
		if (t.src == t.dest) {
			warrior.setPos(t.landing.x, t.landing.y, t.landing.z);
		} else {
			Entity moved = warrior.teleport(new TeleportTransition(t.dest, t.landing, Vec3.ZERO,
					warrior.getYRot(), warrior.getXRot(), TeleportTransition.DO_NOTHING));
			if (moved != null) {
				result = moved;
			}
		}
		if (result instanceof NullWarrior w) {
			w.getNavigation().stop();
			ArmyRegistry.update(w);
		}
		puff(t.dest, t.landing);
	}

	private static void puff(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 50, 0.4, 0.9, 0.4, 0.5);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.9, 0.3, 0.1);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	private static void release(Task t) {
		if (t.forced) {
			t.src.setChunkForced(t.cx, t.cz, false);
			t.forced = false;
		}
	}

	/** Ближайшая свободная точка с опорой под ногами (ищем по вертикали вокруг заданной). */
	private static Vec3 findSafe(ServerLevel level, NullWarrior warrior, Vec3 p) {
		for (int k = 0; k < 10; k++) {
			int dy = (k % 2 == 0) ? k / 2 : -((k + 1) / 2);
			double y = Math.floor(p.y) + dy;
			AABB here = warrior.getBoundingBox().move(p.x - warrior.getX(), y - warrior.getY(), p.z - warrior.getZ());
			AABB below = here.move(0.0, -0.5, 0.0);
			if (level.noCollision(warrior, here) && !level.noCollision(warrior, below)) {
				return new Vec3(p.x, y, p.z);
			}
		}
		return p;
	}
}
