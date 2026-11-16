package enderman21.nullarmy.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** Состояние командира для меню знамени и подсветка врагов, которую включает разведка командира. */
public final class ClientCommanderState {
	public static boolean exists = false;
	public static int mode = 0;
	/** Первый призыв оплачен: дальше призыв бесплатный. */
	public static boolean paid = false;

	/** Сущности, подсвеченные разведкой (id на клиенте); список приходит только владельцу. */
	private static volatile Set<Integer> glowing = new HashSet<>();

	private ClientCommanderState() {
	}

	public static void setSurvey(List<Integer> ids) {
		glowing = new HashSet<>(ids);
	}

	public static boolean isGlowing(int id) {
		return glowing.contains(id);
	}

	/** Каждый тик: при выходе из мира сбрасываем подсветку (саму подсветку рисует EntityGlowMixin). */
	public static void tickGlow(Minecraft mc) {
		if (mc.level == null && !glowing.isEmpty()) {
			glowing = new HashSet<>();
		}
	}
}
