# Evolution (Fabric 1.21.11)

Sword / axe / mace PvP AI for singleplayer and servers you own. Open the menu with `[` (rebindable): pick a category on the left,
left-click a module to switch it, right-click it for its settings. `]` toggles the AI, `\` the HUD.

Build: push to GitHub and run the workflow, or `gradle build`. Needs Fabric API and YACL (YACL is only used for the server allowlist editor).

## 0.7.0
- Kept: crit (low-charge jump start), crit response, jump reset (react / predict), W/S/shift taps, hit select, circle strafe,
  retreat (turn around, run N seconds, turn back), shield break, attribute swap, totem / eat / healing pots, ML, HUD, watermark.
- New: reach spacing (hit at the edge of reach), shift-tap on the hit tick, S-tap on the hit tick or one tick later,
  uppercut (jump + hit together, S), KB displacement (flick 180, turn back, hit), wind-charge smash rewritten.
- Real keyboard and click events in Mouse & keyboard mode, aim gate against ghost hits.
- Removed: block-hit, corner hit, delayed hit, sprint-cut, delayed crit, P-crit, wall-pin, crosshair shake, turn-back, sweep, auto web,
  and the Pot / NethPot / SMP / UHC kits (the pot, totem and eat features stay).
- ML: `ml/run.bat` or `ml/run.sh` trains and writes the model where the mod reloads it by itself. See `ml/README.md`.

Tick limit: Minecraft runs input once per 50 ms tick, so "instant" means the first tick the action is possible.
