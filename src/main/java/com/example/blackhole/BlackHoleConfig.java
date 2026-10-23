package com.example.blackhole;

/** Todos los valores ajustables del mod. Cambia lo que quieras y recompila. */
public final class BlackHoleConfig {
	private BlackHoleConfig() {}

	// Centro del agujero negro (0 64 0 -> centro del bloque)
	public static final double CENTER_X = 0.5;
	public static final double CENTER_Y = 64.5;
	public static final double CENTER_Z = 0.5;

	/** Si es true, el agujero negro empieza solo al crear el mundo / iniciar el servidor. */
	public static final boolean AUTO_START = true;

	// ---- Crecimiento ----
	/** Minutos (de juego) que tarda en llegar al 100 % (tamano maximo). */
	public static final double GROWTH_MINUTES = 15.0;

	/** Esfera del centro (obsidiana, magma, piedra luminosa, basalto): radio inicial y final. */
	public static final double CORE_START = 5.0;
	public static final double CORE_MAX = 30.0;

	/** Cráter (zona donde se destruye el terreno): radio inicial y final. */
	public static final double CRATER_START = 14.0;
	public static final double CRATER_MAX = 250.0;
	/** 1.0 = crece a ritmo constante; mayor = empieza lento y se acelera. */
	public static final double CRATER_EXPONENT = 1.3;

	// ---- Atraccion ----
	/** Rango de atraccion = radio del crater * MULT + BASE. */
	public static final double PULL_RANGE_MULT = 3.0;
	public static final double PULL_RANGE_BASE = 40.0;

	/**
	 * Jugadores (se aplica en el cliente): velocidad de arrastre en bloques/tick.
	 * Maximo = DRIFT_MIN + DRIFT_MAX (0.12 = 2.4 bloques/seg), muy por debajo de correr (5.6/seg).
	 */
	public static final double DRIFT_MIN = 0.02;
	public static final double DRIFT_MAX = 0.10;

	/** Otras entidades (mobs, items...). */
	public static final double PULL_BASE = 0.01;
	public static final double PULL_MAX = 0.2;
	public static final double PULL_EXPONENT = 2.5;

	/** Los jugadores a menos de (radio del nucleo + esto) reciben dano cada 0.5 s. */
	public static final double CORE_DAMAGE_RANGE = 3.0;
	public static final float CORE_DAMAGE = 3.0f;

	// ---- Destruccion de bloques ----
	/** Los bloques entre el borde del crater y borde + (RIP_BASE + borde*RIP_MULT) salen volando al centro. */
	public static final double RIP_BASE = 4.0;
	public static final double RIP_MULT = 0.2;
	/** Grosor de la franja interior del borde que se limpia con prioridad. */
	public static final double BAND_WIDTH = 8.0;
	/** Probabilidad de limpiar tambien el interior profundo del crater (restos sueltos). */
	public static final double INTERIOR_CHANCE = 0.15;
	public static final int ATTEMPTS_PER_PLAYER = 6000;
	public static final int LOOKUPS_PER_PLAYER = 2500;
	public static final int MAX_FALLING_BLOCKS = 700;
	public static final int MAX_RIPS_PER_TICK = 20;
	public static final boolean DESTROY_BEDROCK = false;

	/** Bloques de la esfera central que se colocan por tick. */
	public static final int CORE_PLACE_PER_TICK = 250;

	// ---- Efectos ----
	public static final int RING_PARTICLES = 40;
	public static final double PARTICLE_RANGE_BASE = 160.0;

	public static double pullRange(double craterRadius) {
		return craterRadius * PULL_RANGE_MULT + PULL_RANGE_BASE;
	}
}
