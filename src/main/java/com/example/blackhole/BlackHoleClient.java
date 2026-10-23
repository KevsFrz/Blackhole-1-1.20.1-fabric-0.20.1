package com.example.blackhole;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Parte del cliente: aplica la atraccion al jugador local con la fisica normal de Minecraft,
 * de forma que siempre se pueda escapar caminando, corriendo o volando.
 */
public class BlackHoleClient implements ClientModInitializer {
	private static final Vec3d CENTER = new Vec3d(BlackHoleConfig.CENTER_X, BlackHoleConfig.CENTER_Y, BlackHoleConfig.CENTER_Z);

	private static double craterR = 0;
	private static long lastPacketTime = Long.MIN_VALUE / 2;
	private static boolean received = false;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(BlackHoleNet.STATE_ID, (client, handler, buf, sender) -> {
			double r = buf.readDouble();
			buf.readDouble(); // radio del nucleo (no se usa en el cliente)
			client.execute(() -> {
				craterR = r;
				received = true;
				lastPacketTime = client.world != null ? client.world.getTime() : 0;
			});
		});
		ClientTickEvents.END_CLIENT_TICK.register(BlackHoleClient::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		ClientWorld w = client.world;
		if (p == null || w == null || client.isPaused() || !received) return;
		if (w.getTime() - lastPacketTime > 40) return;
		if (p.isSpectator()) return;

		double range = BlackHoleConfig.pullRange(craterR);
		Vec3d to = CENTER.subtract(p.getPos().add(0, p.getHeight() * 0.5, 0));
		double dist = to.length();
		if (dist > range || dist < 0.5) return;

		double t = 1.0 - dist / range;
		double drift = BlackHoleConfig.DRIFT_MIN + BlackHoleConfig.DRIFT_MAX * t * t;
		Vec3d dir = to.normalize();

		// Friccion aproximada del jugador en cada situacion, para que la velocidad de arrastre
		// sea la misma (y pequena) este donde este.
		double fh;
		double fv;
		if (p.getAbilities().flying) {
			fh = 0.91;
			fv = 0.6;
		} else if (p.isFallFlying()) {
			fh = 0.99;
			fv = 0.99;
		} else if (p.isTouchingWater()) {
			fh = 0.8;
			fv = 0.8;
		} else if (p.isOnGround()) {
			BlockPos below = BlockPos.ofFloored(p.getX(), p.getBoundingBox().minY - 0.5000001, p.getZ());
			fh = w.getBlockState(below).getBlock().getSlipperiness() * 0.91;
			fv = 0.98;
		} else {
			fh = 0.91;
			fv = 0.98;
		}

		double ph = drift * (1.0 - fh);
		double pv = drift * (1.0 - fv);
		p.setVelocity(p.getVelocity().add(dir.x * ph, dir.y * pv, dir.z * ph));
	}
}
