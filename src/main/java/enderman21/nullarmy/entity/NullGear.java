package enderman21.nullarmy.entity;

import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

/** Экипировка и имя воина. Здесь же собраны все обращения к реестрам (чары, отделка). */
public final class NullGear {
	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final Random RANDOM = new Random();

	private NullGear() {
	}

	public static void equip(NullWarrior warrior) {
		RegistryAccess registries = warrior.level().registryAccess();

		warrior.setItemSlot(EquipmentSlot.HEAD, armor(registries, Items.NETHERITE_HELMET));
		warrior.setItemSlot(EquipmentSlot.CHEST, armor(registries, Items.NETHERITE_CHESTPLATE));
		warrior.setItemSlot(EquipmentSlot.LEGS, armor(registries, Items.NETHERITE_LEGGINGS));
		warrior.setItemSlot(EquipmentSlot.FEET, armor(registries, Items.NETHERITE_BOOTS));

		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		enchant(registries, sword, Enchantments.SHARPNESS, 5);
		enchant(registries, sword, Enchantments.UNBREAKING, 3);
		enchant(registries, sword, Enchantments.MENDING, 1);
		warrior.setItemSlot(EquipmentSlot.MAINHAND, sword);
		warrior.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

		// Ничего не выпадает при смерти
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			warrior.setDropChance(slot, 0.0F);
		}

		warrior.setCustomName(Component.literal(randomName()));
		warrior.setCustomNameVisible(true);
	}

	private static ItemStack armor(RegistryAccess registries, Item item) {
		ItemStack stack = new ItemStack(item);
		enchant(registries, stack, Enchantments.PROTECTION, 4);
		enchant(registries, stack, Enchantments.UNBREAKING, 3);
		enchant(registries, stack, Enchantments.MENDING, 1);

		Holder<TrimMaterial> material = registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.NETHERITE);
		Holder<TrimPattern> pattern = registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.FLOW);
		stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
		return stack;
	}

	private static void enchant(RegistryAccess registries, ItemStack stack, ResourceKey<Enchantment> key, int level) {
		HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(lookup.getOrThrow(key), level);
	}

	/** Имя-мешанина из букв и цифр, как у Null'ов из лора. */
	public static String randomName() {
		int length = 8 + RANDOM.nextInt(9);
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
