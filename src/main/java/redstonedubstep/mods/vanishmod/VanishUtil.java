package redstonedubstep.mods.vanishmod;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

public class VanishUtil {
	public static final MutableComponent VANISHMOD_PREFIX = Component.literal("").append(Component.literal("[").withStyle(ChatFormatting.WHITE)).append(Component.literal("Vanishmod").withStyle(s -> s.applyFormat(ChatFormatting.GRAY).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/vanishmod")))).append(Component.literal("] ").withStyle(ChatFormatting.WHITE));

	public static boolean isVanished(Entity player) {
		return isVanished(player, null);
	}

	public static boolean isVanished(Entity player, Entity forPlayer) {
		if (player instanceof Player && !player.level().isClientSide) {
			boolean isVanished = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean("Vanished");

			if (forPlayer != null)
				return !allowedToSeePlayer(forPlayer, player, isVanished(forPlayer), isVanished);

			return isVanished;
		}

		return false;
	}

	public static List<? extends Entity> removeVanishedFromEntityList(List<? extends Entity> rawList, Entity forPlayer) {
		return rawList.stream().filter(entity -> !(entity instanceof Player player) || !isVanished(player, forPlayer)).collect(Collectors.toList());
	}

	public static List<ServerPlayer> removeVanishedFromPlayerList(List<ServerPlayer> rawList, Entity forPlayer) {
		return rawList.stream().filter(player -> !isVanished(player, forPlayer)).collect(Collectors.toList());
	}

	public static boolean allowedToSeePlayer(Entity player, Entity otherPlayer, boolean isVanished, boolean isOtherVanished) {
		if (player.equals(otherPlayer)) //All players should be able to see each other
			return true;

		return !isOtherVanished || canSeeAllVanishedPlayers(player, isVanished) || checkTeamVisibility(player, otherPlayer);
	}

	public static boolean canSeeAllVanishedPlayers(Entity entity, boolean isVanished) {
		if (entity instanceof Player player)
			return (VanishConfig.CONFIG.vanishedPlayersSeeEachOther.get() && isVanished) || (VanishConfig.CONFIG.seeVanishedPermissionLevel.get() >= 0 && player.hasPermissions(VanishConfig.CONFIG.seeVanishedPermissionLevel.get()));

		return false;
	}

	public static boolean checkTeamVisibility(Entity player, Entity otherPlayer) {
		if (!VanishConfig.CONFIG.seeVanishedTeamPlayers.get())
			return false;

		Team team = player.getTeam();

		return team != null && team.canSeeFriendlyInvisibles() && team.getPlayers().contains(otherPlayer.getScoreboardName());
	}

	public static MutableComponent getVanishedStatusText(ServerPlayer player, boolean isVanished) {
		return Component.translatable(isVanished ? VanishConfig.CONFIG.onVanishQuery.get() : VanishConfig.CONFIG.onUnvanishQuery.get(), player.getDisplayName());
	}

	public static ResourceKey<ChatType> getChatTypeRegistryKey(ChatType.Bound chatType, Player player) {
		return player.level().registryAccess().registryOrThrow(Registries.CHAT_TYPE).getResourceKey(chatType.chatType().value()).orElse(ChatType.CHAT);
	}
}
