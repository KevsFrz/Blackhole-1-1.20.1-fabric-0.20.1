# Agujero Negro (Fabric 1.20.1)

Un agujero negro aparece en **0 64 0**: una esfera de obsidiana, basalto, bloque de magma y piedra
luminosa (radio 5 -> 30) crece en el centro, el terreno de alrededor se destruye formando un cráter
cada vez más grande y los bloques salen volando hacia el centro.

Una boss bar muestra el progreso. Tarda 15 minutos de juego en llegar al 100 %.

## Instalación
Pon el .jar en `mods/` junto con Fabric API 0.92.2+1.20.1.
Debe estar instalado **también en el cliente** (la atracción del jugador se aplica en el cliente
para que siempre puedas escapar).

## Comandos (op nivel 2)
- `/blackhole status`
- `/blackhole start` / `/blackhole stop`
- `/blackhole reset`
- `/blackhole progress <0-100>`
- `/blackhole speed <multiplicador>`

Todos los valores están en `BlackHoleConfig.java`.
