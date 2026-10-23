package com.example.blackhole;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlackHoleManager {
	private BlackHoleManager() {}

	public static final Vec3d CENTER = new Vec3d(BlackHoleConfig.CENTER_X, BlackHoleConfig.CENTER_Y, BlackHoleConfig.CENTER_Z);
	private static final BlockPos CENTER_POS = BlockPos.ofFloored(CENTER);

	private static final DustParticleEffect BLACK_DUST = new DustParticleEffect(new Vector3f(0.0f, 0.0f, 0.0f), 4.0f);
	private static final DustParticleEffect DISK_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.1f), 2.0f);
	private static final DustParticleEffect DISK_DUST_HOT = new DustParticleEffect(new Vector3f(1.0f, 0.95f, 0.8f), 1.5f);

	private static final ServerBossBar BAR = new ServerBossBar(
			Text.literal("Agujero Negro"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_20);

	/** Bloques voladores contados en el tick anterior (para limitar la carga). */
	private static int lastFallingCount = 0;
	/** Radio de la esfera central ya puesta en cola de construccion. */
	private static double builtRadius = 0;
	private static int forcedChunkRadius = -1;
	private static final ArrayDeque<Long> CORE_QUEUE = new ArrayDeque<>();

	/** Limpia el estado temporal al cerrar un mundo. */
	public static void reset() {
		lastFallingCount = 0;
		builtRadius = 0;
		forcedChunkRadius = -1;
		CORE_QUEUE.clear();
		BAR.clearPlayers();
	}

	// ------------------------------------------------------------------ progreso

	public static double durationTicks() {
		return BlackHoleConfig.GROWTH_MINUTES * 60.0 * 20.0;
	}

	public static double progress(BlackHoleState s) {
		return Math.max(0.0, Math.min(1.0, s.elapsed / durationTicks()));
	}

	public static double coreRadius(double p) {
		return BlackHoleConfig.CORE_START + (BlackHoleConfig.CORE_MAX - BlackHoleConfig.CORE_START) * p;
	}

	public static double craterRadius(double p) {
		return BlackHoleConfig.CRATER_START
				+ (BlackHoleConfig.CRATER_MAX - BlackHoleConfig.CRATER_START) * Math.pow(p, BlackHoleConfig.CRATER_EXPONENT);
	}

	// ------------------------------------------------------------------ tick

	public static void tick(MinecraftServer server) {
		BlackHoleState state = BlackHoleState.get(server);
		ServerWorld world = server.getOverworld();

		if (state.active) {
			state.elapsed = Math.min(durationTicks(), state.elapsed + state.speed);
			state.markDirty();
		}
		double p = progress(state);
		double coreR = coreRadius(p);
		double craterR = craterRadius(p);

		updateBar(server, state, p, craterR, coreR);
		if (!state.active) return;

		ensureChunksForced(world, coreR);
		buildCore(world, coreR);
		pullEntities(world, craterR, coreR);
		eatBlocks(world, craterR, coreR);
		sendState(world, craterR, coreR);
		effects(world, craterR, coreR);
	}

	// ------------------------------------------------------------------ boss bar

	private static void updateBar(MinecraftServer server, BlackHoleState state, double p, double craterR, double coreR) {
		BAR.setVisible(state.active);
		BAR.setPercent((float) p);
		if (server.getTicks() % 10 != 0) return;

		List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
		for (ServerPlayerEntity pl : online) {
			if (!BAR.getPlayers().contains(pl)) BAR.addPlayer(pl);
		}
		for (ServerPlayerEntity pl : new ArrayList<>(BAR.getPlayers())) {
			if (!online.contains(pl)) BAR.removePlayer(pl);
		}
		BAR.setName(Text.literal(String.format(Locale.ROOT,
				"Agujero Negro  |  Progreso %d%%  |  Cráter: %d bloques  |  Núcleo: %d",
				(int) Math.round(p * 100), (int) Math.round(craterR), (int) Math.round(coreR)))
				.formatted(Formatting.LIGHT_PURPLE));
	}

	// ------------------------------------------------------------------ esfera central

	private static void ensureChunksForced(ServerWorld world, double coreR) {
		int rc = (int) Math.ceil((coreR + 2.0) / 16.0);
		if (rc <= forcedChunkRadius) return;
		for (int cx = -rc; cx <= rc; cx++) {
			for (int cz = -rc; cz <= rc; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		forcedChunkRadius = rc;
	}

	private static void buildCore(ServerWorld world, double coreR) {
		if (coreR > builtRadius + 0.4 || builtRadius <= 0) {
			enqueueShell(builtRadius, coreR);
			builtRadius = coreR;
		} else if (coreR < builtRadius) {
			builtRadius = coreR;
		}

		int placed = 0;
		int pops = 0;
		List<Long> retry = null;
		while (!CORE_QUEUE.isEmpty() && placed < BlackHoleConfig.CORE_PLACE_PER_TICK && pops < 4000) {
			long l = CORE_QUEUE.poll();
			pops++;
			BlockPos pos = BlockPos.fromLong(l);
			if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				if (retry == null) retry = new ArrayList<>();
				retry.add(l);
				continue;
			}
			BlockState target = coreBlock(pos);
			if (world.getBlockState(pos).getBlock() == target.getBlock()) continue;
			world.setBlockState(pos, target, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			placed++;
		}
		if (retry != null) CORE_QUEUE.addAll(retry);
	}

	private static void enqueueShell(double r0, double r1) {
		int n = (int) Math.ceil(r1);
		double a2 = r0 * r0;
		double b2 = r1 * r1;
		for (int dx = -n; dx <= n; dx++) {
			for (int dy = -n; dy <= n; dy++) {
				for (int dz = -n; dz <= n; dz++) {
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 <= b2 && (r0 <= 0 || d2 > a2)) {
						CORE_QUEUE.add(BlockPos.asLong(CENTER_POS.getX() + dx, CENTER_POS.getY() + dy, CENTER_POS.getZ() + dz));
					}
				}
			}
		}
	}

	/** Mezcla fija por posicion: obsidiana, basalto, bloque de magma y piedra luminosa. */
	private static BlockState coreBlock(BlockPos pos) {
		int h = pos.getX() * 73856093 ^ pos.getY() * 19349663 ^ pos.getZ() * 83492791;
		h ^= (h >>> 15);
		h *= 0x2c1b3c6d;
		h ^= (h >>> 12);
		int v = Math.floorMod(h, 100);
		if (v < 50) return Blocks.OBSIDIAN.getDefaultState();
		if (v < 70) return Blocks.BASALT.getDefaultState();
		if (v < 88) return Blocks.MAGMA_BLOCK.getDefaultState();
		return Blocks.GLOWSTONE.getDefaultState();
	}

	// ------------------------------------------------------------------ entidades

	private static void pullEntities(ServerWorld world, double craterR, double coreR) {
		double range = BlackHoleConfig.pullRange(craterR);
		Box box = new Box(CENTER, CENTER).expand(range);
		long time = world.getTime();
		int falling = 0;

		for (Entity e : world.getOtherEntities(null, box)) {
			if (e.isRemoved() || e instanceof EnderDragonPart) continue;

			Vec3d pos = e.getPos().add(0, e.getHeight() * 0.5, 0);
			Vec3d toCenter = CENTER.subtract(pos);
			double dist = toCenter.length();
			if (dist > range) continue;

			// Jugadores: la atraccion la aplica el cliente (asi se puede escapar).
			// Aqui solo hay dano si te pegas al nucleo.
			if (e instanceof PlayerEntity) {
				if (e instanceof ServerPlayerEntity sp && !sp.isSpectator()
						&& dist <= coreR + BlackHoleConfig.CORE_DAMAGE_RANGE && time % 10 == 0) {
					sp.damage(world.getDamageSources().hotFloor(), BlackHoleConfig.CORE_DAMAGE);
				}
				continue;
			}

			// Bloques arrancados: viajan en linea recta hacia el centro y se funden con el nucleo
			if (e instanceof FallingBlockEntity fb) {
				if (dist <= coreR + 1.0) {
					fb.discard();
					continue;
				}
				falling++;
				double speed = Math.min(2.5, 0.3 + dist * 0.05);
				fb.dropItem = false;
				fb.noClip = true;
				fb.setNoGravity(true);
				fb.setVelocity(toCenter.normalize().multiply(speed));
				if (fb.age % 3 == 0) fb.velocityModified = true;
				continue;
			}

			// Tocan el nucleo: destruidos
			if (dist <= coreR + 1.0) {
				swallow(world, e);
				continue;
			}

			double t = 1.0 - dist / range;
			double strength = BlackHoleConfig.PULL_BASE + BlackHoleConfig.PULL_MAX * Math.pow(t, BlackHoleConfig.PULL_EXPONENT);
			Vec3d push = toCenter.normalize().multiply(strength);
			e.addVelocity(push.x, push.y, push.z);
			e.velocityModified = true;
		}
		lastFallingCount = falling;
	}

	private static void swallow(ServerWorld world, Entity e) {
		if (e instanceof LivingEntity le) {
			if (le.isAlive()) le.damage(world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
		} else {
			e.discard();
		}
	}

	// ------------------------------------------------------------------ bloques / crater

	private static void eatBlocks(ServerWorld world, double craterR, double coreR) {
		Random rnd = world.getRandom();
		double rip = BlackHoleConfig.RIP_BASE + craterR * BlackHoleConfig.RIP_MULT;
		double outer = craterR + rip;
		double inner = coreR + 1.0;
		double inner2 = inner * inner;
		double outer2 = outer * outer;
		int viewBlocks = Math.min(192, world.getServer().getPlayerManager().getViewDistance() * 16);
		int bottom = world.getBottomY();
		int top = world.getTopY();
		BlockPos.Mutable mp = new BlockPos.Mutable();
		int fallingNow = lastFallingCount;
		int ripsLeft = BlackHoleConfig.MAX_RIPS_PER_TICK;
		double cx = CENTER.x;
		double cy = CENTER.y;
		double cz = CENTER.z;

		for (ServerPlayerEntity pl : world.getPlayers()) {
			double minX = Math.max(cx - outer, pl.getX() - viewBlocks);
			double maxX = Math.min(cx + outer, pl.getX() + viewBlocks);
			double minZ = Math.max(cz - outer, pl.getZ() - viewBlocks);
			double maxZ = Math.min(cz + outer, pl.getZ() + viewBlocks);
			double minY = Math.max(Math.max(cy - outer, pl.getY() - 110), bottom);
			double maxY = Math.min(Math.min(cy + outer, pl.getY() + 110), top - 1);
			if (minX >= maxX || minY >= maxY || minZ >= maxZ) continue;

			int lookups = 0;
			for (int i = 0; i < BlackHoleConfig.ATTEMPTS_PER_PLAYER && lookups < BlackHoleConfig.LOOKUPS_PER_PLAYER; i++) {
				double x = minX + rnd.nextDouble() * (maxX - minX);
				double y = minY + rnd.nextDouble() * (maxY - minY);
				double z = minZ + rnd.nextDouble() * (maxZ - minZ);
				double dx = x - cx;
				double dy = y - cy;
				double dz = z - cz;
				double d2 = dx * dx + dy * dy + dz * dz;
				if (d2 <= inner2 || d2 > outer2) continue;
				double d = Math.sqrt(d2);

				boolean direct = d <= craterR;
				if (direct && d < craterR - BlackHoleConfig.BAND_WIDTH && rnd.nextDouble() > BlackHoleConfig.INTERIOR_CHANCE) continue;

				lookups++;
				mp.set(x, y, z);
				BlockState bs = readable(world, mp);
				if (bs == null) continue;

				if (direct) {
					// Dentro del crater: desaparece
					world.setBlockState(mp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				} else if (ripsLeft > 0 && fallingNow < BlackHoleConfig.MAX_FALLING_BLOCKS
						&& !bs.hasBlockEntity() && bs.getFluidState().isEmpty()) {
					// Justo fuera del borde: sale volando hacia el centro
					FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(world, mp.toImmutable(), bs);
					fb.dropItem = false;
					fb.noClip = true;
					fb.setNoGravity(true);
					ripsLeft--;
					fallingNow++;
				}
			}
		}
	}

	/** Devuelve el estado si el bloque es destruible y su chunk esta cargado; si no, null. */
	private static BlockState readable(ServerWorld world, BlockPos pos) {
		if (world.isOutOfHeightLimit(pos)) return null;
		if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return null;
		BlockState bs = world.getBlockState(pos);
		if (bs.isAir()) return null;
		if (!BlackHoleConfig.DESTROY_BEDROCK && bs.getHardness(world, pos) < 0) return null;
		return bs;
	}

	// ------------------------------------------------------------------ red / efectos

	private static void sendState(ServerWorld world, double craterR, double coreR) {
		if (world.getTime() % 4 != 0) return;
		for (ServerPlayerEntity pl : world.getPlayers()) {
			if (!ServerPlayNetworking.canSend(pl, BlackHoleNet.STATE_ID)) continue;
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeDouble(craterR);
			buf.writeDouble(coreR);
			ServerPlayNetworking.send(pl, BlackHoleNet.STATE_ID, buf);
		}
	}

	private static void effects(ServerWorld world, double craterR, double coreR) {
		long time = world.getTime();
		Random rnd = world.getRandom();
		double visRange = BlackHoleConfig.PARTICLE_RANGE_BASE + coreR * 3.0;
		double visRangeSq = visRange * visRange;

		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(CENTER) > visRangeSq) continue;

			// Aura oscura y particulas de portal siendo succionadas
			int aura = (int) Math.min(120, 20 + coreR * 3);
			world.spawnParticles(p, BLACK_DUST, true, CENTER.x, CENTER.y, CENTER.z, aura, coreR * 0.8, coreR * 0.8, coreR * 0.8, 0.0);
			world.spawnParticles(p, ParticleTypes.PORTAL, true, CENTER.x, CENTER.y, CENTER.z, 60, coreR * 1.1, coreR * 1.1, coreR * 1.1, 1.0);

			// Disco de acrecion (anillo inclinado alrededor del nucleo)
			double tilt = 0.35;
			for (int i = 0; i < BlackHoleConfig.RING_PARTICLES; i++) {
				double a = rnd.nextDouble() * Math.PI * 2.0;
				double rr = coreR * (1.4 + rnd.nextDouble() * 0.9);
				double px = CENTER.x + Math.cos(a) * rr;
				double py = CENTER.y + Math.sin(a) * rr * Math.sin(tilt);
				double pz = CENTER.z + Math.sin(a) * rr * Math.cos(tilt);
				DustParticleEffect fx = rnd.nextInt(4) == 0 ? DISK_DUST_HOT : DISK_DUST;
				world.spawnParticles(p, fx, true, px, py, pz, 0, 0, 0, 0, 0);
			}
		}

		if (time % 40 == 0) {
			float vol = (float) Math.min(60.0, 4.0 + craterR * 0.2);
			world.playSound(null, CENTER_POS, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, vol, 0.5f);
		}
	}
}
