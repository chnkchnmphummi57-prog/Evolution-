# Evolution v0.7.5 source audit — 2026-11-26

## Changes in this pass

- Reordered config migrations to run sequentially from version 7 through 10. Previously, the version-9 migration ran before versions 8 and 7, advancing the version and causing those earlier migrations to be skipped for older configs.
- If `pvpai.json` cannot be parsed, preserve a timestamped `.corrupt-*.bak` copy where possible, load defaults, and queue a user-visible warning. The original is not silently discarded before the backup attempt.
- Config saves now write to a temporary file and replace the destination atomically where the filesystem supports it. Save failures are logged and surfaced as an in-game warning rather than silently ignored.
- Added `Manual smooth look` to the existing Settings > Camera module. It toggles Minecraft 1.21.11's native `GameOptions.smoothCameraEnabled` and saves through `GameOptions.write()`. This is a manual mouse-look option and does not alter Evolution's combat camera controller.

## Verification

- `dev-tests/run.sh`: 73 passed, 0 failed (four new action-arbitration regression tests).
- ZIP integrity checked after packaging.
- Minecraft API names checked against Fabric Yarn 1.21.11+build.4 documentation: `GameOptions.smoothCameraEnabled` is public and `GameOptions.write()` is public.
- Full Gradle/Fabric build was not run: Gradle is not installed in the execution environment and this source archive does not include a Gradle wrapper.
- No in-game test was possible here.

## Combat integration recovery

- Added explicit action ownership in `Utility` for emergency totem swaps, eating, healing-pot sequences, shield-breaking, and the Mace shield guard.
- `AiController` now skips spear-lunge and mace controllers on ticks where a utility sequence owns the hotbar/use/attack action. This prevents later controllers from overwriting utility slot selections or use actions.
- Added four regression tests covering eating, healing-pot selection, shield-break priority, and totem swap priority.
- Individual decision algorithms (`StrictController`, `MaceController`, and `LungeController`) were not rewritten. This is a narrow arbitration fix, not a redesign.
- No files were published or pushed to GitHub.
- Actual Minecraft in-game behavior remains unverified.
