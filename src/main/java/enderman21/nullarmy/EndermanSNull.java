package enderman21.nullarmy;

import enderman21.nullarmy.network.NullNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndermanSNull implements ModInitializer {
	public static final String MOD_ID = "endermans-null";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();
		NullCommands.register();
		NullRitual.register();
		NullNetworking.register();
		ArmyManager.register();
		ArmyRegistry.register();
		ArmyCall.register();
		FlagManager.register();
		LOGGER.info("Enderman's Null loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
