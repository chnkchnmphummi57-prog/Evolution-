# Evolution (Fabric 1.21.11) - sword PvP AI

Scope: singleplayer, LAN host, or servers YOU own (add them to the allowlist). Out-of-scope = AI refuses to run.

Build: install Gradle 9.x (or open in IntelliJ), run `gradle wrapper --gradle-version 9.2.1`, then `./gradlew runClient`.
Keys: ] = toggle AI (starts OFF every launch), [ = config menu.
Working now: scope gate, observation builder, Strict controller, action applier + toggle masking, HUD, YACL settings screen.
ML and Smart currently fall back to Strict. Next: metrics/Elo/tier (3), Python trainer + ONNX (4), Smart + online learning (5).

v0.4.0: strategies (Normal / Safe / Crit spam), shield break, auto-totem, auto-eat, rebindable hotkeys (Input tab).
ML mode is next: trainer + ONNX export + in-game backend.

v0.5.0: crit jump starts at lower charge, crit response picks crit spam or combo, W-tap fixed (S-tap no longer starves it),
jump reset in every phase (+ predict mode), totem upkeep with fallback + diagnostics, shield break at any charge,
kits (Sword, Axe, Mace, Pot, NethPot, SMP, UHC).
