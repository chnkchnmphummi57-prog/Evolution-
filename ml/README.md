# Evolution ML (v1: learns from the rule modes)

The model imitates what Strict or Smart does, then plays through a small pure-Java network inside the mod.
It plays like the rules it learned from, not better. Self-play comes after this works.

## Three steps

1. **Record.** In the game open the Evolution menu, go to **ML > Record Demos** and switch it on. Set Mode to Strict or Smart,
   switch the AI on and let it fight a bot or a friend. Aim for about 40 minutes (50,000+ rows). The row counter is on the HUD.
2. **Train.** Double-click `run.bat` (Windows) or run `./run.sh` (Mac/Linux). It installs numpy if needed, finds your Minecraft
   folder by itself, trains, and writes `model.json` into your `config/evolution` folder.
3. **Play.** The mod reloads `model.json` by itself within a few seconds. In the menu set Mode to **ML**
   (Combat > Evolution AI > Mode). The HUD shows the model status.

If the trainer cannot find your demos, use **ML > Record Demos > Copy folder path** in the game and run
`python train.py --demos "<paste>"`.

## Options
`--hidden 128,128`  `--epochs 40`  `--lr 0.001`  `--out path/model.json`

Self-test without the game: `python train.py --demos ./demos_test --synthetic 30000 --epochs 15`

## Format
Observation: 64 floats (`Ctx.toFloats` in the mod; the layout is fixed). Action: 10 floats:
forward, back, left, right, jump, sprint, attack, use (logits), yaw turn, pitch turn (tanh x 45 degrees).
The mod still applies its hit-registration gates to a model's attacks, so a model cannot send ghost hits.
