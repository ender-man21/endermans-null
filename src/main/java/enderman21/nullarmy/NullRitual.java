package enderman21.nullarmy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Ритуал призыва: тотем в основной руке, Shift + удерживать ПКМ 3 секунды.
 * У ванильного тотема нет «использования с удержанием», поэтому считаем повторные
 * срабатывания ПКМ (клиент шлёт их каждые 4 тика, пока кнопка зажата).
 */
public final class NullRitual {
	private static final int NEEDED_TICKS = 60;
	/** Если пауза между срабатываниями больше, считаем, что игрок отпустил кнопку. */
	private static final int MAX_GAP_TICKS = 8;

	private record State(long start, long last) {
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private NullRitual() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (!NullArmy.isNullTotem(sp.getMainHandItem())) {
				return InteractionResult.PASS;
			}

			long now = level.getGameTime();
			State previous = STATES.get(sp.getUUID());
			State state = (previous == null || now - previous.last() > MAX_GAP_TICKS)
					? new State(now, now)
					: new State(previous.start(), now);

			long progress = now - state.start();
			if (progress >= NEEDED_TICKS) {
				STATES.remove(sp.getUUID());
				NullArmy.tryChargeSummon(sp);
				return InteractionResult.CONSUME;
			}
			STATES.put(sp.getUUID(), state);

			if (level instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ParticleTypes.PORTAL,
						sp.getX(), sp.getY() + 1.0, sp.getZ(), 6, 0.5, 0.8, 0.5, 0.3);
			}
			int filled = (int) (progress * 10 / NEEDED_TICKS);
			String bar = "▮".repeat(filled) + "▯".repeat(10 - filled);
			sp.sendSystemMessage(Component.translatable("message.endermans-null.ritual", bar), true);
			return InteractionResult.CONSUME;
		});
	}
}
