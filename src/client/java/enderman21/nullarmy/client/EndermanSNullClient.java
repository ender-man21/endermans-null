package enderman21.nullarmy.client;

import enderman21.nullarmy.ModEntities;
import enderman21.nullarmy.ModItems;
import enderman21.nullarmy.client.render.NullWarriorRenderer;
import enderman21.nullarmy.client.screen.RadialMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class EndermanSNullClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.NULL_WARRIOR, NullWarriorRenderer::new);

		// Меню открывается сразу, как только зажат ПКМ со знаменем (без Shift).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Minecraft mc = client;
			if (mc.player == null || mc.gui.screen() != null) {
				return;
			}
			boolean holding = mc.options.keyUse.isDown()
					&& mc.player.getMainHandItem().is(ModItems.NULL_BANNER)
					&& !mc.player.isShiftKeyDown();
			if (holding) {
				mc.gui.setScreen(new RadialMenuScreen());
			}
		});
	}
}
