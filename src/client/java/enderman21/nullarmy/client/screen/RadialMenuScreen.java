package enderman21.nullarmy.client.screen;

import enderman21.nullarmy.NullArmy;
import enderman21.nullarmy.entity.NullWarrior;
import enderman21.nullarmy.network.CallArmyPayload;
import enderman21.nullarmy.network.SetModePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Method;

/**
 * Радиальное меню приказов. Открывается, пока зажат ПКМ со знаменем;
 * мышь выбирает сектор, отпустить ПКМ значит применить. Esc закрывает без выбора.
 *
 * Сектора: вверх это следовать, вправо это держать, вниз это охранять точку, влево это отмена.
 */
public class RadialMenuScreen extends Screen {
	private static final int DEAD_ZONE = 18;
	private static final int RADIUS = 56;

	private static final NullWarrior.Mode[] MODES = {
			NullWarrior.Mode.FOLLOW, NullWarrior.Mode.HOLD, NullWarrior.Mode.GUARD
	};

	/** 0 вверх, 1 вправо, 2 вниз, 3 влево, -1 ничего. */
	private int selected = -1;

	private static final int COLUMN_EDGE = 150;
	private static final int ROW_HEIGHT = 20;
	/** Выбранные настройки (клиент помнит последний выбор; сервер хранит настоящие значения). */
	private static int currentFormation = 0;
	private static int currentBehavior = 1;
	/** Наведённые пункты колонок, -1 если мышь не над колонкой. */
	private int hoverFormation = -1;
	private int hoverBehavior = -1;

	public RadialMenuScreen() {
		super(Component.empty());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Последняя выбранная дистанция (клиент помнит между открытиями). */
	private static int distance = NullArmy.DEFAULT_DISTANCE;
	private boolean distanceChanged;
	private boolean done;
	private int age;

	/**
	 * Отпускание ПКМ экран получает не всегда (нажатие было обработано игрой до открытия меню),
	 * поэтому состояние кнопки опрашиваем напрямую у SDL3 (в 26.x вместо GLFW).
	 * Через рефлексию, чтобы не зависеть от classpath: если SDL недоступен, работают
	 * события мыши (отпускание или повторный клик).
	 */
	@Override
	public void tick() {
		super.tick();
		age++;
		if (age < 3 || done) return;
		if (rightButtonReleased()) {
			finish();
		}
	}

	private static Method sdlGetMouseState;
	private static boolean sdlFailed;

	/** true только если SDL точно сообщил, что ПКМ не зажата. */
	private static boolean rightButtonReleased() {
		if (sdlFailed) return false;
		try {
			if (sdlGetMouseState == null) {
				sdlGetMouseState = Class.forName("org.lwjgl.sdl.SDLMouse")
						.getMethod("SDL_GetMouseState", float[].class, float[].class);
			}
			int mask = ((Number) sdlGetMouseState.invoke(null, new float[1], new float[1])).intValue();
			return (mask & 4) == 0; // SDL_BUTTON_RMASK = 1 << 2
		} catch (Throwable t) {
			sdlFailed = true;
			return false;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 || event.button() == 1) {
			finish();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		finish();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			int next = distance + (scrollY > 0 ? 1 : -1);
			distance = Math.max(NullArmy.MIN_DISTANCE, Math.min(NullArmy.MAX_DISTANCE, next));
			distanceChanged = true;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void finish() {
		if (done) return;
		done = true;
		apply();
		this.onClose();
	}

	private void apply() {
		// колонки: постоянные настройки
		if (hoverFormation >= 0 || hoverBehavior >= 0) {
			if (hoverFormation >= 0) {
				currentFormation = hoverFormation;
			}
			if (hoverBehavior >= 0) {
				currentBehavior = hoverBehavior;
			}
			ClientPlayNetworking.send(new SetModePayload(-1, distanceChanged ? distance : -1, hoverFormation, hoverBehavior));
			return;
		}
		// 0 вверх (следовать), 1 вправо (держать), 2 вниз (гулять), 3 влево (позвать)
		if (selected == 3) {
			ClientPlayNetworking.send(new CallArmyPayload());
			return;
		}
		int mode = (selected >= 0 && selected < MODES.length) ? MODES[selected].ordinal() : -1;
		int dist = distanceChanged ? distance : -1;
		if (mode >= 0 || dist > 0) {
			ClientPlayNetworking.send(new SetModePayload(mode, dist, -1, -1));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int cx = this.width / 2;
		int cy = this.height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;

		hoverFormation = -1;
		hoverBehavior = -1;
		int top = cy - 40;
		if (dx < -COLUMN_EDGE) {
			int row = Math.floorDiv(mouseY - top, ROW_HEIGHT);
			hoverFormation = (row >= 0 && row < NullWarrior.Formation.values().length) ? row : -1;
		} else if (dx > COLUMN_EDGE) {
			int row = Math.floorDiv(mouseY - top, ROW_HEIGHT);
			hoverBehavior = (row >= 0 && row < NullWarrior.Behavior.values().length) ? row : -1;
		}

		if (dx < -COLUMN_EDGE || dx > COLUMN_EDGE || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else if (Math.abs(dx) > Math.abs(dy)) {
			selected = dx > 0 ? 1 : 3;
		} else {
			selected = dy < 0 ? 0 : 2;
		}

		drawLabel(graphics, cx, cy - RADIUS, MODES[0].translationKey(), selected == 0);
		drawLabel(graphics, cx + RADIUS + 20, cy, MODES[1].translationKey(), selected == 1);
		drawLabel(graphics, cx, cy + RADIUS, MODES[2].translationKey(), selected == 2);
		drawLabel(graphics, cx - RADIUS - 20, cy, "screen.endermans-null.radial.call", selected == 3);
		drawColumn(graphics, cx - COLUMN_EDGE - 70, top - 22, "screen.endermans-null.radial.formation");
		for (int i = 0; i < NullWarrior.Formation.values().length; i++) {
			drawItem(graphics, cx - COLUMN_EDGE - 70, top + i * ROW_HEIGHT + 4,
					NullWarrior.Formation.values()[i].translationKey(), hoverFormation == i, currentFormation == i);
		}
		drawColumn(graphics, cx + COLUMN_EDGE + 70, top - 22, "screen.endermans-null.radial.behavior");
		for (int i = 0; i < NullWarrior.Behavior.values().length; i++) {
			drawItem(graphics, cx + COLUMN_EDGE + 70, top + i * ROW_HEIGHT + 4,
					NullWarrior.Behavior.values()[i].translationKey(), hoverBehavior == i, currentBehavior == i);
		}
		String hint = Component.translatable("screen.endermans-null.radial.distance", distance).getString();
		graphics.text(this.font, hint, cx - this.font.width(hint) / 2, cy + RADIUS + 22, 0xFF9A7FD0, true);
		graphics.text(this.font, "●", cx - this.font.width("●") / 2, cy - this.font.lineHeight / 2, 0xFFFFFFFF, true);
	}

	private void drawColumn(GuiGraphicsExtractor graphics, int centerX, int y, String key) {
		String text = Component.translatable(key).getString();
		graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, 0xFFB38CFF, true);
	}

	private void drawItem(GuiGraphicsExtractor graphics, int centerX, int y, String key, boolean hovered, boolean current) {
		String text = (current ? "● " : "  ") + Component.translatable(key).getString();
		int color = hovered ? 0xFFFFD54A : (current ? 0xFFFFFFFF : 0xFFB0B0B0);
		graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, color, true);
	}

	private void drawLabel(GuiGraphicsExtractor graphics, int centerX, int centerY, String key, boolean hovered) {
		String text = (hovered ? "> " : "") + Component.translatable(key).getString() + (hovered ? " <" : "");
		int color = hovered ? 0xFFFFD54A : 0xFFB0B0B0;
		graphics.text(this.font, text, centerX - this.font.width(text) / 2, centerY - this.font.lineHeight / 2, color, true);
	}
}
