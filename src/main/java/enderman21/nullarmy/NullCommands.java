package enderman21.nullarmy;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import enderman21.nullarmy.entity.NullWarrior;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class NullCommands {
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("null")
						// Админские: выдать тотем и призвать воина бесплатно (для тестов)
						.then(Commands.literal("totem")
								.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
								.then(Commands.literal("give")
										.executes(ctx -> giveTotem(ctx, ctx.getSource().getPlayerOrException()))
										.then(Commands.argument("target", EntityArgument.player())
												.executes(ctx -> giveTotem(ctx, EntityArgument.getPlayer(ctx, "target"))))))
						.then(Commands.literal("summon")
								.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
								.executes(NullCommands::summon))
						// Диагностика и сброс (админам)
						.then(Commands.literal("debug")
								.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
								.executes(NullDebug::report)
								.then(Commands.literal("reset").executes(NullDebug::reset)))
						// Приказы армии: нужны тотем в инвентаре
						.then(mode("follow", NullWarrior.Mode.FOLLOW))
						.then(mode("hold", NullWarrior.Mode.HOLD))
						.then(mode("guard", NullWarrior.Mode.GUARD))
						.then(mode("cursor", NullWarrior.Mode.CURSOR))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mode(String name, NullWarrior.Mode mode) {
		return Commands.literal(name).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (!NullArmy.hasTotem(player)) {
				ctx.getSource().sendFailure(Component.translatable("message.endermans-null.need_totem"));
				return 0;
			}
			NullArmy.setMode(player, mode);
			ctx.getSource().sendSuccess(() -> Component.translatable("message.endermans-null.mode",
					Component.translatable(mode.translationKey())), false);
			return 1;
		});
	}

	private static int giveTotem(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
		ItemStack stack = NullArmy.createTotem(target.level().registryAccess());
		NullArmy.ensureArmyId(stack);
		if (!target.getInventory().add(stack) && target.level() instanceof ServerLevel level) {
			// Инвентарь полон: кладём тотем на землю рядом с игроком
			level.addFreshEntity(new ItemEntity(level, target.getX(), target.getY(), target.getZ(), stack));
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("message.endermans-null.totem_given", target.getDisplayName()), true);
		return 1;
	}

	private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack totem = NullArmy.firstTotem(player);
		if (totem.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("message.endermans-null.need_totem"));
			return 0;
		}
		NullArmy.summon(player, NullArmy.ensureArmyId(totem));
		return 1;
	}
}
