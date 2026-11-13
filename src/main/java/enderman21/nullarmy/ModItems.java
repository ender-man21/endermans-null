package enderman21.nullarmy;

import enderman21.nullarmy.item.NullBannerItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class ModItems {
	/** Знамя командира. Урон и перезарядка удара настраиваются модификаторами. */
	public static final Item NULL_BANNER = register(
			"null_banner",
			NullBannerItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
					.component(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
							.add(Attributes.ATTACK_DAMAGE,
									new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 5.0, AttributeModifier.Operation.ADD_VALUE),
									EquipmentSlotGroup.MAINHAND)
							.add(Attributes.ATTACK_SPEED,
									new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
									EquipmentSlotGroup.MAINHAND)
							.build())
	);

	public static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EndermanSNull.id(name));
		T item = factory.apply(props.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(tab -> tab.accept(NULL_BANNER));
	}
}
