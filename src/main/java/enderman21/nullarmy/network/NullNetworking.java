package enderman21.nullarmy.network;

import enderman21.nullarmy.ArmyCall;
import enderman21.nullarmy.ModItems;
import enderman21.nullarmy.NullArmy;
import enderman21.nullarmy.entity.NullWarrior;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NullNetworking {
	private NullNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SetModePayload.TYPE, SetModePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CallArmyPayload.TYPE, CallArmyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SummonCommanderPayload.TYPE, SummonCommanderPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CommanderRequestPayload.TYPE, CommanderRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DismissCommanderPayload.TYPE, DismissCommanderPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetCommanderModePayload.TYPE, SetCommanderModePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CommanderStatePayload.TYPE, CommanderStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SurveyPayload.TYPE, SurveyPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(CommanderRequestPayload.TYPE, (payload, context) -> {
			NullArmy.sendCommanderState(context.player());
		});

		ServerPlayNetworking.registerGlobalReceiver(SetCommanderModePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!player.getMainHandItem().is(ModItems.NULL_BANNER) || !NullArmy.hasTotem(player)) {
				return;
			}
			NullArmy.setCommanderMode(player, payload.mode());
		});

		ServerPlayNetworking.registerGlobalReceiver(SummonCommanderPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!player.getMainHandItem().is(ModItems.NULL_BANNER)) {
				return;
			}
			if (!NullArmy.hasTotem(player)) {
				player.sendSystemMessage(Component.translatable("message.endermans-null.need_totem"), true);
				return;
			}
			NullArmy.summonCommander(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(DismissCommanderPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!player.getMainHandItem().is(ModItems.NULL_BANNER) || !NullArmy.hasTotem(player)) {
				return;
			}
			NullArmy.dismissCommander(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(CallArmyPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!player.getMainHandItem().is(ModItems.NULL_BANNER)) {
				return;
			}
			if (!NullArmy.hasTotem(player)) {
				player.sendSystemMessage(Component.translatable("message.endermans-null.need_totem"), true);
				return;
			}
			ArmyCall.request(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(SetModePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			// Сервер не доверяет клиенту: проверяем тотем и знамя в руке
			if (!player.getMainHandItem().is(ModItems.NULL_BANNER)) {
				return;
			}
			if (!NullArmy.hasTotem(player)) {
				player.sendSystemMessage(Component.translatable("message.endermans-null.need_totem"), true);
				return;
			}
			if (payload.distance() > 0) {
				NullArmy.setDistance(player, payload.distance());
				player.sendSystemMessage(Component.translatable("message.endermans-null.distance",
						Math.max(NullArmy.MIN_DISTANCE, Math.min(NullArmy.MAX_DISTANCE, payload.distance()))), true);
			}
			if (payload.formation() >= 0) {
				NullWarrior.Formation formation = NullWarrior.Formation.byId(payload.formation());
				NullArmy.setFormation(player, formation);
				player.sendSystemMessage(Component.translatable("message.endermans-null.formation",
						Component.translatable(formation.translationKey())), true);
			}
			if (payload.behavior() >= 0) {
				NullWarrior.Behavior behavior = NullWarrior.Behavior.byId(payload.behavior());
				NullArmy.setBehavior(player, behavior);
				player.sendSystemMessage(Component.translatable("message.endermans-null.behavior",
						Component.translatable(behavior.translationKey())), true);
			}
			if (payload.mode() < 0) {
				return;
			}
			NullWarrior.Mode mode = NullWarrior.Mode.byId(payload.mode());
			if (mode == NullWarrior.Mode.ATTACK) {
				return;
			}
			NullArmy.setMode(player, mode);
			player.sendSystemMessage(Component.translatable("message.endermans-null.mode",
					Component.translatable(mode.translationKey())), true);
		});
	}
}
