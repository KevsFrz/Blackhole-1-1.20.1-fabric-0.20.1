package com.example.blackhole;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Estado guardado en el mundo (sobrevive a reinicios). */
public class BlackHoleState extends PersistentState {
	/** Ticks de crecimiento acumulados (ya multiplicados por la velocidad). */
	public double elapsed = 0;
	public boolean active = BlackHoleConfig.AUTO_START;
	public double speed = 1.0;

	public static BlackHoleState fromNbt(NbtCompound nbt) {
		BlackHoleState s = new BlackHoleState();
		s.elapsed = nbt.getDouble("elapsed");
		s.active = nbt.getBoolean("active");
		s.speed = nbt.contains("speed") ? nbt.getDouble("speed") : 1.0;
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putDouble("elapsed", elapsed);
		nbt.putBoolean("active", active);
		nbt.putDouble("speed", speed);
		return nbt;
	}

	public static BlackHoleState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager()
				.getOrCreate(BlackHoleState::fromNbt, BlackHoleState::new, "blackhole");
	}
}
