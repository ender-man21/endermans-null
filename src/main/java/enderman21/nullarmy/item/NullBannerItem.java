package enderman21.nullarmy.item;

import enderman21.nullarmy.NullArmy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Знамя командира.
 * ЛКМ по сущности: армия атакует её (плюс удар самим знаменем).
 * Зажать ПКМ: радиальное меню режимов (клиент, см. RadialMenuScreen).
 * Нужен Тотем Нулла в инвентаре.
 */
public class NullBannerItem extends Item {
	public NullBannerItem(Properties properties) {
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (attacker instanceof ServerPlayer sp && NullArmy.hasTotem(sp)) {
			NullArmy.orderAttack(sp, target);
		}
	}
}
