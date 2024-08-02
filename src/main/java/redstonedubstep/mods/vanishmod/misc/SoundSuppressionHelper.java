package redstonedubstep.mods.vanishmod.misc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import redstonedubstep.mods.vanishmod.VanishConfig;
import redstonedubstep.mods.vanishmod.VanishUtil;

public class SoundSuppressionHelper {
	private static final Map<ServerPlayer, Pair<BlockPos, Entity>> vanishedPlayersAndHitResults = new HashMap<>();
	private static Pair<Packet<?>, Player> packetOrigin = null;

	public static boolean shouldCapturePlayers() {
		return VanishConfig.CONFIG.indirectSoundSuppression.get() || VanishConfig.CONFIG.indirectParticleSuppression.get();
	}

	public static void updateVanishedPlayerMap(ServerPlayer player, boolean vanished) {
		if (vanished)
			vanishedPlayersAndHitResults.put(player, null);
		else
			vanishedPlayersAndHitResults.remove(player);

		new HashSet<>(vanishedPlayersAndHitResults.keySet()).stream().filter(Entity::isRemoved).forEach(vanishedPlayersAndHitResults::remove);
	}

	public static void updateBlockHitResult(ServerPlayer player, BlockHitResult hitResult) {
		if (VanishUtil.isVanished(player)) {
			Pair<BlockPos, Entity> oldHitResults = vanishedPlayersAndHitResults.get(player);
			vanishedPlayersAndHitResults.put(player, oldHitResults == null ? Pair.of(hitResult.getBlockPos(), null) : Pair.of(hitResult.getBlockPos(), oldHitResults.getRight()));
		}
	}

	public static void updateEntityHitResult(ServerPlayer player, Entity hitEntity) {
		if (VanishUtil.isVanished(player))
			vanishedPlayersAndHitResults.put(player, Pair.of(hitEntity.blockPosition(), hitEntity));
	}

	public static void invalidateHitResults(ServerPlayer player) {
		if (VanishUtil.isVanished(player))
			vanishedPlayersAndHitResults.put(player, null);
	}

	public static void putSoundPacket(Packet<?> packet, Player player) {
		packetOrigin = Pair.of(packet, player);
	}

	public static Player getPlayerForPacket(Packet<?> packet) {
		return packetOrigin != null && packetOrigin.getLeft().equals(packet) ? packetOrigin.getRight() : null;
	}

	public static Player getIndirectVanishedSoundCause(Player player, Level level, double x, double y, double z, Player forPlayer) {
		return getIndirectVanishedSoundCause(player, level, new Vec3(x, y, z), forPlayer);
	}

	//Returns true if a vanished player directly produced the sound, or if it is determined that a vanished player was indirectly causing a sound, and that it thus should not be broadcast
	public static Player getIndirectVanishedSoundCause(Player player, Level level, Vec3 soundOrigin, Player forPlayer) {
		if (player != null)
			return VanishUtil.isVanished(player, forPlayer) ? player : null;

		if (!VanishConfig.CONFIG.indirectSoundSuppression.get())
			return null;

		Player vanishedSoundCause = SoundSuppressionHelper.getVanishedPlayerInteractedWith(level, soundOrigin, forPlayer);

		if (vanishedSoundCause == null)
			vanishedSoundCause = SoundSuppressionHelper.getVanishedPlayerAt(level, soundOrigin, forPlayer);

		if (vanishedSoundCause == null)
			vanishedSoundCause = SoundSuppressionHelper.getVanishedProjectileOwnerAt(level, soundOrigin, forPlayer);

		if (vanishedSoundCause == null)
			vanishedSoundCause = SoundSuppressionHelper.getVanishedPlayerWithVehicleAt(level, soundOrigin, forPlayer);

		return vanishedSoundCause;
	}

	//Returns true if a vanished player directly produced the sound, or if it is determined that a vanished player was indirectly causing a sound, and that it thus should not be broadcast
	public static Player getIndirectVanishedSoundCause(Player player, Level level, Entity soundOrigin, Player forPlayer) {
		if (player != null)
			return VanishUtil.isVanished(player, forPlayer) ? player : null;

		if (!VanishConfig.CONFIG.indirectSoundSuppression.get() || soundOrigin == null)
			return null;

		Player vanishedSoundCause = SoundSuppressionHelper.getVanishedPlayerInteractedWith(level, soundOrigin, forPlayer);

		if (vanishedSoundCause == null)
			vanishedSoundCause = SoundSuppressionHelper.getVanishedPlayerAt(level, soundOrigin.position(), forPlayer);

		if (vanishedSoundCause == null)
			vanishedSoundCause = SoundSuppressionHelper.getVanishedProjectileOwner(soundOrigin, forPlayer);

		if (vanishedSoundCause == null)
			vanishedSoundCause = SoundSuppressionHelper.getVanishedPlayerInVehicle(soundOrigin, forPlayer);

		return vanishedSoundCause;
	}

	public static Player getIndirectVanishedParticleCause(Player player, Level level, double x, double y, double z, Player forPlayer) {
		Vec3 particleOrigin = new Vec3(x, y, z);

		if (player != null)
			return VanishUtil.isVanished(player, forPlayer) ? player : null;

		if (!VanishConfig.CONFIG.indirectParticleSuppression.get())
			return null;

		Player vanishedParticleCause = SoundSuppressionHelper.getVanishedPlayerInteractedWith(level, particleOrigin, forPlayer);

		if (vanishedParticleCause == null)
			vanishedParticleCause = SoundSuppressionHelper.getVanishedPlayerAt(level, particleOrigin, forPlayer);

		if (vanishedParticleCause == null)
			vanishedParticleCause = SoundSuppressionHelper.getVanishedProjectileOwnerAt(level, particleOrigin, forPlayer);

		if (vanishedParticleCause == null)
			vanishedParticleCause = SoundSuppressionHelper.getVanishedPlayerWithVehicleAt(level, particleOrigin, forPlayer);

		return vanishedParticleCause;
	}

	public static Player getVanishedPlayerAt(Level level, Vec3 pos, Player forPlayer) {
		VoxelShape shape = Shapes.block().move(pos.x - 0.5D, pos.y - 0.5D, pos.z - 0.5D);

		return vanishedPlayersAndHitResults.keySet().stream().filter(p -> p.level().equals(level) && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && VanishUtil.isVanished(p, forPlayer) && Shapes.joinIsNotEmpty(shape, Shapes.create(p.getBoundingBox()), BooleanOp.AND)).findFirst().orElse(null);
	}

	public static Player getVanishedProjectileOwnerAt(Level level, Vec3 pos, Player forPlayer) {
		AABB soundArea = AABB.ofSize(pos, 1.0D, 1.0D, 1.0D);
		List<Entity> projectiles = level.getEntities((Entity) null, soundArea, e -> e instanceof Projectile);

		if (!projectiles.isEmpty())
			return vanishedPlayersAndHitResults.keySet().stream().filter(p -> p.level().equals(level) && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && VanishUtil.isVanished(p, forPlayer) && projectiles.stream().anyMatch(proj -> p.equals(((Projectile) proj).getOwner()))).findFirst().orElse(null);

		return null;
	}

	public static Player getVanishedProjectileOwner(Entity entity, Player forPlayer) {
		if (entity instanceof Projectile projectile)
			return vanishedPlayersAndHitResults.keySet().stream().filter(p -> p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && VanishUtil.isVanished(p, forPlayer) && p.equals(projectile.getOwner())).findFirst().orElse(null);

		return null;
	}

	public static Player getVanishedPlayerWithVehicleAt(Level level, Vec3 pos, Player forPlayer) {
		VoxelShape shape = Shapes.block().move(pos.x - 0.5D, pos.y - 0.5D, pos.z - 0.5D);

		return vanishedPlayersAndHitResults.keySet().stream().filter(p -> p.level().equals(level) && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && VanishUtil.isVanished(p, forPlayer)).map(p -> Pair.of(p, p.getVehicle())).filter(pv -> pv.getRight() != null && Shapes.joinIsNotEmpty(shape, Shapes.create(pv.getRight().getBoundingBox()), BooleanOp.AND)).findFirst().map(Pair::getLeft).orElse(null);
	}

	public static Player getVanishedPlayerInVehicle(Entity entity, Player forPlayer) {
		return vanishedPlayersAndHitResults.keySet().stream().filter(p -> p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && VanishUtil.isVanished(p, forPlayer) && entity.equals(p.getVehicle())).findFirst().orElse(null);
	}

	public static Player getVanishedPlayerInteractedWith(Level level, Vec3 pos, Player forPlayer) {
		return vanishedPlayersAndHitResults.entrySet().stream().filter(e -> e.getKey().level().equals(level) && VanishUtil.isVanished(e.getKey(), forPlayer) && e.getValue() != null && equalsThisOrConnected(pos, level, e.getValue().getLeft())).findFirst().map(Map.Entry::getKey).orElse(null);
	}

	public static Player getVanishedPlayerInteractedWith(Level level, Entity entity, Player forPlayer) {
		return vanishedPlayersAndHitResults.entrySet().stream().filter(e -> e.getKey().level().equals(level) && VanishUtil.isVanished(e.getKey(), forPlayer) && e.getValue() != null && entity.equals(e.getValue().getRight())).findFirst().map(Map.Entry::getKey).orElse(null);
	}

	public static boolean equalsThisOrConnected(Vec3 soundPos, Level level, BlockPos interactPos) {
		if (interactPos != null) {
			BlockState state = level.getBlockState(interactPos);

			if (new AABB(interactPos).inflate(0.01).contains(soundPos)) //expand the interaction pos a little to cover (literal) edge cases
				return true;
			else if (state.getBlock() instanceof ChestBlock)
				return new AABB(interactPos.relative(ChestBlock.getConnectedDirection(state))).inflate(0.01).contains(soundPos);
		}

		return false;
	}
}
