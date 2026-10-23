package com.example.blackhole;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class BlackHoleMod implements ModInitializer {
	public static final String MOD_ID = "blackhole";

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(BlackHoleManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BlackHoleManager.reset());
		CommandRegistrationCallback.EVENT.register(BlackHoleCommands::register);
	}
}
