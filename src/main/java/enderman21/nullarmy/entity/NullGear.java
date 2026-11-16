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

	/** Версия экипировки: 2 это ранги (цвет отделки) и Прочность 3 + Починка на всём, включая щит. */
	public static final int VERSION = 2;

	/** Полная выдача: ранг, экипировка и имя по рангу. */
	public static void equip(NullWarrior warrior, NullWarrior.Rank rank) {
		warrior.setRank(rank);
		if (rank == NullWarrior.Rank.COMMANDER) {
			// Командир втрое крепче рядового
			warrior.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60.0);
			warrior.setHealth(60.0F);
		}
		refresh(warrior);
		warrior.setCustomName(Component.literal(nameFor(rank)));
		warrior.setCustomNameVisible(true);
	}

	/** Обновляет экипировку по текущему рангу, не трогая имя (для воинов старых версий мода). */
	public static void refresh(NullWarrior warrior) {
		RegistryAccess registries = warrior.level().registryAccess();
		if (warrior.isSurveying()) {
			// Разведчик невидим: снимаем всё, иначе броня и оружие висели бы в воздухе
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
					EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
				warrior.setItemSlot(slot, ItemStack.EMPTY);
			}
			warrior.setGearVersion(VERSION);
			return;
		}
		ResourceKey<TrimMaterial> trim = switch (warrior.getRank()) {
			case SOLDIER -> TrimMaterials.NETHERITE;
			case GENERAL -> TrimMaterials.REDSTONE;
			case COMMANDER -> TrimMaterials.QUARTZ;
		};

		warrior.setItemSlot(EquipmentSlot.HEAD, armor(registries, Items.NETHERITE_HELMET, trim));
		warrior.setItemSlot(EquipmentSlot.CHEST, armor(registries, Items.NETHERITE_CHESTPLATE, trim));
		warrior.setItemSlot(EquipmentSlot.LEGS, armor(registries, Items.NETHERITE_LEGGINGS, trim));
		warrior.setItemSlot(EquipmentSlot.FEET, armor(registries, Items.NETHERITE_BOOTS, trim));

		ItemStack weapon;
		if (warrior.isCommander() && warrior.getCommanderMode() == NullWarrior.CommanderMode.LEAD) {
			// Булава только у командира и только в режиме Lead
			weapon = new ItemStack(Items.MACE);
		} else {
			weapon = new ItemStack(Items.NETHERITE_SWORD);
			enchant(registries, weapon, Enchantments.SHARPNESS, 5);
		}
		enchant(registries, weapon, Enchantments.UNBREAKING, 3);
		enchant(registries, weapon, Enchantments.MENDING, 1);
		warrior.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		ItemStack shield = new ItemStack(Items.SHIELD);
		enchant(registries, shield, Enchantments.UNBREAKING, 3);
		enchant(registries, shield, Enchantments.MENDING, 1);
		warrior.setItemSlot(EquipmentSlot.OFFHAND, shield);

		// Ничего не выпадает при смерти
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			warrior.setDropChance(slot, 0.0F);
		}
		warrior.setGearVersion(VERSION);
	}

	/** Имя по рангу: у солдата случайное, у генерала _случайное_ (11 знаков), у командира восемь подчёркиваний. */
	public static String nameFor(NullWarrior.Rank rank) {
		return switch (rank) {
			case SOLDIER -> randomName();
			case GENERAL -> "_" + randomString(11) + "_";
			case COMMANDER -> "________";
		};
	}

	private static ItemStack armor(RegistryAccess registries, Item item, ResourceKey<TrimMaterial> trimMaterial) {
		ItemStack stack = new ItemStack(item);
		enchant(registries, stack, Enchantments.PROTECTION, 4);
		enchant(registries, stack, Enchantments.UNBREAKING, 3);
		enchant(registries, stack, Enchantments.MENDING, 1);

		Holder<TrimMaterial> material = registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(trimMaterial);
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
		return randomString(8 + RANDOM.nextInt(9));
	}

	private static String randomString(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
