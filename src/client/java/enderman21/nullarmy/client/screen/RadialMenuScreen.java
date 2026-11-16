package enderman21.nullarmy.client.screen;

import enderman21.nullarmy.NullArmy;
import enderman21.nullarmy.entity.NullWarrior;
import enderman21.nullarmy.client.ClientCommanderState;
import enderman21.nullarmy.network.CallArmyPayload;
import enderman21.nullarmy.network.CommanderRequestPayload;
import enderman21.nullarmy.network.DismissCommanderPayload;
import enderman21.nullarmy.network.SetCommanderModePayload;
import enderman21.nullarmy.network.SummonCommanderPayload;
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
 * Сектора: вверх это следовать, вправо вверх это «За курсором», вправо это держать,
 * вниз это «Гулять», влево это позвать.
 */
public class RadialMenuScreen extends Screen {
	private static final int DEAD_ZONE = 18;
	private static final int RADIUS = 56;
	private static final int RING_RADIUS = 118;

	private static final NullWarrior.Mode[] MODES = {
			NullWarrior.Mode.FOLLOW, NullWarrior.Mode.HOLD, NullWarrior.Mode.GUARD
	};

	/** 0 вверх, 1 вправо, 2 вниз, 3 влево, -1 ничего. */
	private int selected = -1;

	private static final int COLUMN_EDGE = 150;
	private static final int ROW_HEIGHT = 16;
	/** Выбранные настройки (клиент помнит последний выбор; сервер хранит настоящие значения). */
	private static int currentFormation = 0;
	private static int currentBehavior = 1;
	/** Наведённые пункты колонок, -1 если мышь не над колонкой. */
	private int hoverFormation = -1;
	private int hoverBehavior = -1;

	/** -2 это иконка командира (призвать, если его нет), 0..2 это режим командира, -1 ничего. */
	private int hoverCommander = -1;

	public RadialMenuScreen() {
		super(Component.empty());
		// Узнаём у сервера, есть ли командир и в каком он режиме
		ClientPlayNetworking.send(new CommanderRequestPayload());
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
		// иконка командира и его режимы
		if (hoverCommander == -2) {
			// Переключатель справа от панели: призвать или отозвать
			ClientPlayNetworking.send(ClientCommanderState.exists
					? new DismissCommanderPayload() : new SummonCommanderPayload());
			return;
		}
		if (hoverCommander >= 0 && ClientCommanderState.exists) {
			ClientPlayNetworking.send(new SetCommanderModePayload(hoverCommander));
			return;
		}
		if (hoverCommander != -1) {
			return;
		}
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
		if (selected == 4) {
			mode = NullWarrior.Mode.CURSOR.ordinal();
		}
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
		hoverCommander = -1;
		int top = cy - 40;
		int headY = top + NullWarrior.Formation.values().length * ROW_HEIGHT + 18;
		if (dx < -COLUMN_EDGE && mouseY >= headY) {
			// Левый нижний угол: иконка командира (ряд 0) и его режимы (ряды 1-3)
			int row = Math.floorDiv(mouseY - headY, ROW_HEIGHT);
			int panelX = cx - COLUMN_EDGE - 70;
			if (row == 0 && mouseX >= panelX - 6) {
				hoverCommander = -2;
			} else if (row == 1 && !ClientCommanderState.exists && !ClientCommanderState.paid) {
				hoverCommander = -2;
			} else if (row >= 1 && row <= NullWarrior.CommanderMode.values().length && ClientCommanderState.exists) {
				hoverCommander = row - 1;
			}
		} else if (dx < -COLUMN_EDGE) {
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
		// Диагональ вправо-вверх (25-65 градусов от вертикали) это «За курсором»
		if (selected == 0 || selected == 1) {
			double angle = Math.toDegrees(Math.atan2(dx, -dy));
			if (selected >= 0 && angle >= 25.0 && angle < 65.0
					&& dx * dx + dy * dy >= DEAD_ZONE * DEAD_ZONE && dx <= COLUMN_EDGE) {
				selected = 4;
			}
		}

		drawRing(graphics, cx, cy);
		drawLabel(graphics, cx, cy - RADIUS, MODES[0].translationKey(), selected == 0);
		drawLabel(graphics, cx + RADIUS + 20, cy, MODES[1].translationKey(), selected == 1);
		drawLabel(graphics, cx, cy + RADIUS, MODES[2].translationKey(), selected == 2);
		drawLabel(graphics, cx - RADIUS - 20, cy, "screen.endermans-null.radial.call", selected == 3);
		drawLabel(graphics, cx + 62, cy - 42, NullWarrior.Mode.CURSOR.translationKey(), selected == 4);
		// Рамки вокруг блоков меню
		int rows = NullWarrior.Formation.values().length;
		frame(graphics, cx - COLUMN_EDGE - 70 - 74, top - 28, cx - COLUMN_EDGE - 70 + 74, top + rows * ROW_HEIGHT + 6);
		frame(graphics, cx + COLUMN_EDGE + 70 - 74, top - 28, cx + COLUMN_EDGE + 70 + 74,
				top + NullWarrior.Behavior.values().length * ROW_HEIGHT + 6);
		frame(graphics, cx - COLUMN_EDGE - 70 - 74, headY - 4, cx - COLUMN_EDGE - 70 + 74,
				headY + (NullWarrior.CommanderMode.values().length + 1) * ROW_HEIGHT + 2);
		drawCommander(graphics, cx - COLUMN_EDGE - 70, headY);
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

	/** Иконка командира и колонка его режимов (Lead, Survey, Protect) в левом нижнем углу. */
	private void drawCommander(GuiGraphicsExtractor graphics, int centerX, int headY) {
		boolean exists = ClientCommanderState.exists;
		String name = "♛ " + Component.translatable("screen.endermans-null.radial.commander").getString();
		int color = exists ? 0xFFB38CFF : 0xFF8A8A8A;
		graphics.text(this.font, name, centerX - 66, headY + 4, color, true);
		// Переключатель справа от названия: призвать или отозвать
		String toggle = "[ " + Component.translatable(exists
				? "screen.endermans-null.radial.commander_dismiss"
				: "screen.endermans-null.radial.commander_toggle").getString() + " ]";
		int toggleColor = hoverCommander == -2 ? 0xFFFFD54A : (exists ? 0xFFFF7A7A : 0xFF7AE08A);
		graphics.text(this.font, toggle, centerX + 66 - this.font.width(toggle), headY + 4, toggleColor, true);
		if (!exists && ClientCommanderState.paid) {
			return;
		}
		if (!exists) {
			String hint = Component.translatable("screen.endermans-null.radial.commander_summon", NullArmy.COMMANDER_COST).getString();
			graphics.text(this.font, hint, centerX - this.font.width(hint) / 2, headY + 4 + ROW_HEIGHT,
					hoverCommander == -2 ? 0xFFFFD54A : 0xFF8A8A8A, true);
			return;
		}
		for (int i = 0; i < NullWarrior.CommanderMode.values().length; i++) {
			drawItem(graphics, centerX, headY + (i + 1) * ROW_HEIGHT + 4,
					NullWarrior.CommanderMode.values()[i].translationKey(),
					hoverCommander == i, ClientCommanderState.mode == i);
		}
	}

	/** Рамка блока меню: тёмная подложка и тонкая фиолетовая обводка. */
	private void frame(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, 0x90100818);
		int border = 0xFF6A4FB0;
		graphics.fill(x1, y1, x2, y1 + 1, border);
		graphics.fill(x1, y2 - 1, x2, y2, border);
		graphics.fill(x1, y1, x1 + 1, y2, border);
		graphics.fill(x2 - 1, y1, x2, y2, border);
		// Золотые уголки
		int gold = 0xFFE8B84A;
		int len = 7;
		graphics.fill(x1, y1, x1 + len, y1 + 1, gold);
		graphics.fill(x1, y1, x1 + 1, y1 + len, gold);
		graphics.fill(x2 - len, y1, x2, y1 + 1, gold);
		graphics.fill(x2 - 1, y1, x2, y1 + len, gold);
		graphics.fill(x1, y2 - 1, x1 + len, y2, gold);
		graphics.fill(x1, y2 - len, x1 + 1, y2, gold);
		graphics.fill(x2 - len, y2 - 1, x2, y2, gold);
		graphics.fill(x2 - 1, y2 - len, x2, y2, gold);
	}

	/** Кольцо вокруг центра: двойная линия с лёгким свечением, золотые точки по сторонам света и засечки на диагоналях. */
	private void drawRing(GuiGraphicsExtractor graphics, int cx, int cy) {
		int r = RING_RADIUS;
		for (int i = 0; i < 720; i++) {
			double a = i * Math.PI / 360.0;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			plot(graphics, cx, cy, cos, sin, r + 2, 0x28B38CFF);
			plot(graphics, cx, cy, cos, sin, r + 1, 0x50B38CFF);
			plot(graphics, cx, cy, cos, sin, r, 0xFF8A72D6);
			plot(graphics, cx, cy, cos, sin, r - 6, 0x906A4FB0);
		}
		int gold = 0xFFE8B84A;
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2.0;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			graphics.fill(x - 2, y - 2, x + 3, y + 3, gold);
		}
		for (int i = 0; i < 4; i++) {
			double a = Math.PI / 4.0 + i * Math.PI / 2.0;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			graphics.fill(x - 1, y - 1, x + 2, y + 2, gold);
		}
	}

	private static void plot(GuiGraphicsExtractor graphics, int cx, int cy, double cos, double sin, int r, int color) {
		int x = cx + (int) Math.round(cos * r);
		int y = cy + (int) Math.round(sin * r);
		graphics.fill(x, y, x + 1, y + 1, color);
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
