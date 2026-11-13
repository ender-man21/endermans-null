package enderman21.nullarmy;

import enderman21.nullarmy.entity.NullWarrior;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
	public static final EntityType<NullWarrior> NULL_WARRIOR = register(
			"null_warrior",
			EntityType.Builder.<NullWarrior>of(NullWarrior::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
	);

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EndermanSNull.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(NULL_WARRIOR, NullWarrior.createAttributes());
	}
}
