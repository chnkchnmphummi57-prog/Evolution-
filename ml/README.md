# Evolution ML (v1: behaviour cloning)

What this is: the model **imitates** the rule modes (Strict/Smart) from recorded fights, then plays in-game through a
small pure-Java network. It will play like the rules it learned from, not better. Reinforcement learning / self-play comes after this works.

## 1. Record demos
In the mod's quick panel (ML tab) turn **Record demos** on, set Mode to **Strict** or **Smart**, switch the AI on and let it
fight a bot or a friend. Rows are written to `.minecraft/config/evolution/demos/demo_*.csv` (only while a rule mode fights).
Aim for at least 50,000 rows (about 40 minutes of fighting). More variety (kits, strategies, opponents) gives a better model.

## 2. Train
```
pip install -r requirements.txt
python train.py --demos "<minecraft>/config/evolution/demos" --out model.json
```
NumPy only, runs on a normal CPU. Options: `--hidden 128,128`, `--epochs 40`, `--lr 0.001`.
Self-test without the game: `python train.py --demos ./demos_test --synthetic 30000 --epochs 15`

## 3. Load
Copy `model.json` to `.minecraft/config/evolution/model.json`, press **Reload model** (ML tab), set Mode to **ML**.
The HUD shows the model status. Without a valid model, ML mode runs the Strict rules.

## Format
Observation: 64 floats (see `Ctx.toFloats` in the mod, layout is fixed). Action: 10 floats:
forward, back, left, right, jump, sprint, attack, use (logits), yaw turn, pitch turn (tanh x 45 degrees).
The mod still applies its safety gates to a model's attacks (crosshair ray, hurt frames, shields, reach), so a model cannot send ghost hits.
