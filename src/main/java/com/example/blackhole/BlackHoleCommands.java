package com.example.blackhole;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BlackHoleCommands {
	private BlackHoleCommands() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access,
								CommandManager.RegistrationEnvironment env) {
		dispatcher.register(literal("blackhole")
				.requires(src -> src.hasPermissionLevel(2))
				.then(literal("status").executes(ctx -> {
					BlackHoleState s = BlackHoleState.get(ctx.getSource().getServer());
					double p = BlackHoleManager.progress(s);
					reply(ctx.getSource(), String.format(Locale.ROOT,
							"Agujero negro: %s | progreso %.1f%% | cráter %.1f | núcleo %.1f | velocidad x%.2f",
							s.active ? "ACTIVO" : "detenido", p * 100,
							BlackHoleManager.craterRadius(p), BlackHoleManager.coreRadius(p), s.speed));
					return 1;
				}))
				.then(literal("start").executes(ctx -> {
					BlackHoleState s = BlackHoleState.get(ctx.getSource().getServer());
					s.active = true;
					s.markDirty();
					reply(ctx.getSource(), "Agujero negro activado.");
					return 1;
				}))
				.then(literal("stop").executes(ctx -> {
					BlackHoleState s = BlackHoleState.get(ctx.getSource().getServer());
					s.active = false;
					s.markDirty();
					reply(ctx.getSource(), "Agujero negro detenido.");
					return 1;
				}))
				.then(literal("reset").executes(ctx -> {
					BlackHoleState s = BlackHoleState.get(ctx.getSource().getServer());
					s.elapsed = 0;
					s.active = true;
					s.markDirty();
					reply(ctx.getSource(), "Agujero negro reiniciado.");
					return 1;
				}))
				.then(literal("progress").then(argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
						.executes(ctx -> {
							BlackHoleState s = BlackHoleState.get(ctx.getSource().getServer());
							double pct = DoubleArgumentType.getDouble(ctx, "percent");
							s.elapsed = pct / 100.0 * BlackHoleManager.durationTicks();
							s.markDirty();
							reply(ctx.getSource(), String.format(Locale.ROOT, "Progreso fijado en %.1f%%", pct));
							return 1;
						})))
				.then(literal("speed").then(argument("multiplier", DoubleArgumentType.doubleArg(0.0, 100.0))
						.executes(ctx -> {
							BlackHoleState s = BlackHoleState.get(ctx.getSource().getServer());
							s.speed = DoubleArgumentType.getDouble(ctx, "multiplier");
							s.markDirty();
							reply(ctx.getSource(), String.format(Locale.ROOT, "Velocidad de crecimiento x%.2f", s.speed));
							return 1;
						}))));
	}

	private static void reply(ServerCommandSource src, String msg) {
		src.sendFeedback(() -> Text.literal(msg), true);
	}
}
