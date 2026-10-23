package com.example.blackhole;

import net.minecraft.util.Identifier;

public final class BlackHoleNet {
	private BlackHoleNet() {}

	/** Servidor -> cliente: radio del crater y radio del nucleo (dos doubles). */
	public static final Identifier STATE_ID = new Identifier("blackhole", "state");
}
