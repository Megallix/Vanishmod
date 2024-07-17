package redstonedubstep.mods.vanishmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

public class VanishCommand {
	private static final Component HELP_TEXT = VanishUtil.VANISHMOD_PREFIX.copy().append(Component.literal("""
				§7§nVanishmod§r is a mod that allows you to become completely undetectable for other players. Most features can be accessed using the §7/vanish§r (or §7/v§r) command.
				§nCommand Usage§r:
				§7/v get [<player>]§r: Queries the current vanished status of the given player.
				§7/v help§r: Shows this message.
				§7/v queue [<player>]§r: Adds the given player name to the vanishing queue. If the player is online, they are immediately vanished. If not, they are vanished as soon as they join the server.
				§7/v toggle [<player>]§r (or §7/v§r): Vanishes or unvanishes the given player, depending on their prior vanished status.
				
				A lot of the features of this mod are customizable via the configuration file, which is located in the "config" folder of your server, so check that out if you're interested!
				If you have a suggestion or found a bug, feel free to open an issue in""")).append(Component.literal(" §7§nthe mod's GitHub repository§r.").withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/RedstoneDubstep/Vanishmod"))));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(alias("v"));
		dispatcher.register(alias("vanish"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> alias(String prefix) {
		return Commands.literal(prefix).requires(player -> player.hasPermission(VanishConfig.CONFIG.vanishCommandPermissionLevel.get())).executes(ctx -> vanish(ctx, ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("get").executes(ctx -> getVanishedStatus(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> getVanishedStatus(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("help").executes(VanishCommand::sendHelpText))
				.then(Commands.literal("queue").executes(ctx -> queue(ctx, ctx.getSource().getPlayerOrException().getGameProfile().getName()))
						.then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> queue(ctx, StringArgumentType.getString(ctx, "player")))))
				.then(Commands.literal("toggle").executes(ctx -> vanish(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> vanish(ctx, EntityArgument.getPlayer(ctx, "player")))));
	}

	private static int getVanishedStatus(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		MutableComponent vanishedStatus = VanishUtil.getVanishedStatusText(player, VanishUtil.isVanished(player));

		ctx.getSource().sendSuccess(() -> VanishUtil.VANISHMOD_PREFIX.copy().append(vanishedStatus), false);

		if (ctx.getSource().getEntity() instanceof ServerPlayer currentPlayer)
			currentPlayer.connection.send(new ClientboundSetActionBarTextPacket(vanishedStatus));

		return 1;
	}

	private static int sendHelpText(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSystemMessage(HELP_TEXT);
		return 1;
	}

	private static int queue(CommandContext<CommandSourceStack> ctx, String playerName) {
		ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);

		if (player != null) {
			if (VanishUtil.isVanished(player)) {
				ctx.getSource().sendFailure(VanishUtil.VANISHMOD_PREFIX.copy().append(String.format("Could not add already vanished player %s to the vanishing queue", playerName)));
				return 0;
			}

			vanish(ctx, player);
		}
		else if (VanishUtil.removeFromQueue(playerName))
			ctx.getSource().sendSuccess(() -> VanishUtil.VANISHMOD_PREFIX.copy().append(String.format("Removed %s from the vanishing queue", playerName)), true);
		else if (VanishUtil.addToQueue(playerName))
			ctx.getSource().sendSuccess(() -> VanishUtil.VANISHMOD_PREFIX.copy().append(String.format("Added %s to the vanishing queue", playerName)), true);

		return 1;
	}

	private static int vanish(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		ctx.getSource().sendSuccess(() -> VanishUtil.VANISHMOD_PREFIX.copy().append(Component.translatable(!VanishUtil.isVanished(player) ? VanishConfig.CONFIG.onVanishMessage.get() : VanishConfig.CONFIG.onUnvanishMessage.get(), player.getDisplayName())), true);
		VanishUtil.toggleVanish(player);
		return 1;
	}
}
