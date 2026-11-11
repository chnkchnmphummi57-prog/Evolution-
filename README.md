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

v0.6.0: ghost-hit fixes (crosshair ray gate, hurt-frame gate, shield gate, adaptive reach margin, hit watchdog on the HUD),
attribute swap for Axe/Mace (with a watchdog), auto cobweb + crit spam on webbed opponents, sword techniques
(shift-tap, block-hit, corner hit, circle/jitter strafe, delayed hit, sprint-cut, uppercut, delayed crit, hit-select, P-crit,
wall-pin, crosshair shake, turn-back, sweep disruption), ML v1 (demo recorder, ml/train.py, in-game MLP).

v0.6.0 (input + hit registration): Mouse & keyboard mode now sends real key/click events (KeyBinding.setKeyPressed + onKeyPressed, the
calls the game's own input handlers make). The attack is a real left click, only sent when vanilla's crosshair pick AND the rotation the
server last received both point at the hitbox. Retreat turns around and runs for N seconds (default 5), then turns back and fights.
Uppercut = jump + hit on the same tick, S-tap 5 ticks later.
