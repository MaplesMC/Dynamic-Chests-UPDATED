package net.example.dynamicchests.pocket;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * {@code /pocket trust|untrust|trusted}: manage which friends may walk into your pocket through
 * one of your Pocket-Dimension Chests. Visiting itself needs no command, just right-click the chest.
 */
public final class PocketCommands {

	private PocketCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pocket")
				.then(Commands.literal("trust")
						.then(Commands.argument("player", EntityArgument.player()).executes(context -> {
							ServerPlayer owner = context.getSource().getPlayerOrException();
							ServerPlayer friend = EntityArgument.getPlayer(context, "player");
							if (friend.getUUID().equals(owner.getUUID())) {
								context.getSource().sendFailure(Component.translatable("pocket.dynamicchests.command.self"));
								return 0;
							}
							PocketData.get(context.getSource().getServer())
									.trust(owner.getUUID(), friend.getUUID(), friend.getName().getString());
							context.getSource().sendSuccess(() -> Component.translatable(
									"pocket.dynamicchests.command.trusted", friend.getName()), false);
							friend.sendSystemMessage(Component.translatable(
									"pocket.dynamicchests.command.trusted_notice", owner.getName()));
							return 1;
						})))
				.then(Commands.literal("untrust")
						.then(Commands.argument("player", EntityArgument.player()).executes(context -> {
							ServerPlayer owner = context.getSource().getPlayerOrException();
							ServerPlayer friend = EntityArgument.getPlayer(context, "player");
							boolean removed = PocketData.get(context.getSource().getServer())
									.untrust(owner.getUUID(), friend.getUUID());
							if (!removed) {
								context.getSource().sendFailure(Component.translatable(
										"pocket.dynamicchests.command.not_in_list", friend.getName()));
								return 0;
							}
							context.getSource().sendSuccess(() -> Component.translatable(
									"pocket.dynamicchests.command.untrusted", friend.getName()), false);
							return 1;
						})))
				.then(Commands.literal("trusted").executes(context -> {
					ServerPlayer owner = context.getSource().getPlayerOrException();
					PocketData.Plot plot = PocketData.get(context.getSource().getServer()).existing(owner.getUUID());
					if (plot == null || plot.trusted.isEmpty()) {
						context.getSource().sendSuccess(() -> Component.translatable("pocket.dynamicchests.command.list_empty"), false);
						return 0;
					}
					String names = String.join(", ", plot.trusted.values());
					context.getSource().sendSuccess(() -> Component.translatable("pocket.dynamicchests.command.list", names), false);
					return plot.trusted.size();
				})));
	}

	/** Names of everyone an owner trusts; used by tests and other code. */
	public static Map<String, String> trustedOf(PocketData.Plot plot) {
		return Map.copyOf(plot.trusted);
	}
}
