package enderman21.nullarmy.client.render;

import enderman21.nullarmy.EndermanSNull;
import enderman21.nullarmy.entity.NullWarrior;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Человекоподобный рендер: модель игрока + наш скин, броня и оружие рисуются ванильными слоями.
 * Это самый вероятный файл для правок под 26.3: если IDE ругается на конструктор слоя брони,
 * сверься с ZombieRenderer в исходниках игры.
 */
public class NullWarriorRenderer extends HumanoidMobRenderer<NullWarrior, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = EndermanSNull.id("textures/entity/null_warrior.png");

	public NullWarriorRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(
				this,
				ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new),
				ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()
		));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
