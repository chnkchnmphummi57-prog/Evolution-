#!/usr/bin/env python3
"""
Behaviour-cloning trainer for the Evolution mod. NumPy only (no PyTorch needed).

Reads the CSV demos the mod records (config/evolution/demos/*.csv), trains a small MLP to imitate the recorded
actions, and writes model.json for the mod's pure-Java inference backend.

    python train.py                           # finds your Minecraft folder, trains, writes config/evolution/model.json
    python train.py --demos path/to/demos --out model.json
    python train.py --synthetic 30000 --demos ./test   # self-test with generated data, no game needed

The mod reloads model.json by itself within a couple of seconds, so there is nothing to copy or click.

Row layout (must match the mod): OBS_SIZE observation floats, then 10 action floats:
  forward, back, left, right, jump, sprint, attack, use   (0/1)   -> trained with weighted binary cross-entropy
  yaw, pitch                                              (-1..1) -> trained with MSE on tanh outputs
"""
import argparse
import glob
import json
import os
import sys
import time

import numpy as np

N_BITS = 8
N_ACT = 10
BIT_NAMES = ["forward", "back", "left", "right", "jump", "sprint", "attack", "use"]


def load_demos(folder):
    xs, ys, files = [], [], 0
    for path in sorted(glob.glob(os.path.join(folder, "*.csv"))):
        obs = act = None
        rows = []
        with open(path, "r") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                if line.startswith("#"):
                    for part in line[1:].split(","):
                        k, _, v = part.partition("=")
                        if k == "obs":
                            obs = int(v)
                        elif k == "act":
                            act = int(v)
                    continue
                rows.append(line.split(","))
        if not rows or obs is None or act != N_ACT:
            print(f"skip {os.path.basename(path)}: missing or wrong header")
            continue
        try:
            arr = np.asarray(rows, dtype=np.float32)
        except ValueError:
            print(f"skip {os.path.basename(path)}: unreadable rows (file cut off?)")
            continue
        if arr.shape[1] != obs + act:
            print(f"skip {os.path.basename(path)}: row width {arr.shape[1]} != {obs + act}")
            continue
        xs.append(arr[:, :obs])
        ys.append(arr[:, obs:])
        files += 1
    if not xs:
        sys.exit(f"no usable demos in {folder}")
    sizes = {x.shape[1] for x in xs}
    if len(sizes) != 1:
        sys.exit(f"demo files disagree on observation size: {sorted(sizes)}")
    return np.concatenate(xs), np.concatenate(ys), files


def make_synthetic(folder, n, obs_size=64, seed=0):
    """Generated demos with learnable structure, to test the pipeline without the game."""
    rng = np.random.default_rng(seed)
    os.makedirs(folder, exist_ok=True)
    x = rng.uniform(-1, 1, size=(n, obs_size)).astype(np.float32)
    x[:, 12] = rng.uniform(0, 1, n)                       # attack charge
    x[:, 25] = rng.uniform(0, 1, n)                       # reach distance
    x[:, 26] = (rng.uniform(0, 1, n) > 0.5).astype(np.float32)   # crosshair on target
    y = np.zeros((n, N_ACT), dtype=np.float32)
    y[:, 0] = (x[:, 3] > 0.1)                             # forward when far
    y[:, 1] = (x[:, 3] < -0.6)
    y[:, 2] = (x[:, 14] > 0.3)
    y[:, 3] = (x[:, 14] < -0.3)
    y[:, 4] = (x[:, 13] > 0.5)
    y[:, 5] = (x[:, 3] > 0.3)
    y[:, 6] = (x[:, 12] > 0.9) & (x[:, 25] < 0.45) & (x[:, 26] > 0.5)
    y[:, 7] = (x[:, 27] > 0.8)
    y[:, 8] = np.clip(x[:, 15], -1, 1)
    y[:, 9] = np.clip(x[:, 16], -1, 1)
    path = os.path.join(folder, "synthetic.csv")
    with open(path, "w") as f:
        f.write(f"#obs={obs_size},act={N_ACT}\n")
        for i in range(n):
            f.write(",".join(f"{v:.5f}" for v in np.concatenate([x[i], y[i]])) + "\n")
    return path


def init_params(sizes, rng):
    params = []
    for i in range(len(sizes) - 1):
        w = rng.normal(0, np.sqrt(2.0 / sizes[i]), size=(sizes[i], sizes[i + 1])).astype(np.float32)
        params.append([w, np.zeros(sizes[i + 1], dtype=np.float32)])
    return params


def forward(params, x, keep=False):
    acts = [x]
    for i, (w, b) in enumerate(params):
        z = acts[-1] @ w + b
        acts.append(np.maximum(z, 0) if i < len(params) - 1 else z)
    return (acts[-1], acts) if keep else acts[-1]


def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-np.clip(z, -30, 30)))


def loss_and_grad(params, x, y, pos_w, yaw_w):
    out, acts = forward(params, x, keep=True)
    n = x.shape[0]
    p = sigmoid(out[:, :N_BITS])
    yb = y[:, :N_BITS]
    wgt = np.where(yb > 0.5, pos_w, 1.0)
    eps = 1e-7
    bce = -(yb * np.log(p + eps) + (1 - yb) * np.log(1 - p + eps)) * wgt
    t = np.tanh(out[:, N_BITS:])
    mse = (t - y[:, N_BITS:]) ** 2
    loss = bce.sum() / n + yaw_w * mse.sum() / n

    g = np.zeros_like(out)
    g[:, :N_BITS] = (p - yb) * wgt / n
    g[:, N_BITS:] = yaw_w * 2.0 * (t - y[:, N_BITS:]) * (1.0 - t ** 2) / n

    grads = [None] * len(params)
    for i in range(len(params) - 1, -1, -1):
        w, _ = params[i]
        grads[i] = [acts[i].T @ g, g.sum(axis=0)]
        if i > 0:
            g = (g @ w.T) * (acts[i] > 0)
    return loss, grads


def evaluate(params, x, y):
    out = forward(params, x)
    pred = sigmoid(out[:, :N_BITS]) >= 0.5
    yb = y[:, :N_BITS] > 0.5
    rows = []
    for k, name in enumerate(BIT_NAMES):
        tp = np.sum(pred[:, k] & yb[:, k])
        fp = np.sum(pred[:, k] & ~yb[:, k])
        fn = np.sum(~pred[:, k] & yb[:, k])
        acc = np.mean(pred[:, k] == yb[:, k])
        f1 = 2 * tp / max(1, 2 * tp + fp + fn)
        rows.append((name, acc, f1, float(yb[:, k].mean())))
    mae = np.mean(np.abs(np.tanh(out[:, N_BITS:]) - y[:, N_BITS:]), axis=0)
    return rows, mae


def export_model(path, params, mean, std, obs_size):
    layers = []
    for i, (w, b) in enumerate(params):
        layers.append({
            "w": np.round(w, 6).tolist(),          # shape [in][out]
            "b": np.round(b, 6).tolist(),
            "act": "relu" if i < len(params) - 1 else "linear",
        })
    model = {
        "format": 1,
        "obs_size": obs_size,
        "act_size": N_ACT,
        "mean": np.round(mean, 6).tolist(),
        "std": np.round(std, 6).tolist(),
        "layers": layers,
    }
    with open(path, "w") as f:
        json.dump(model, f)


def java_style_forward(path, obs_row):
    """Mirror of MlBackend.infer in plain Python loops: proves the exported file means what the Java loader expects."""
    with open(path) as f:
        m = json.load(f)
    x = [max(-5.0, min(5.0, (obs_row[i] - m["mean"][i]) / m["std"][i])) for i in range(m["obs_size"])]
    for layer in m["layers"]:
        w, b = layer["w"], layer["b"]
        y = list(b)
        for i, xi in enumerate(x):
            if xi == 0.0:
                continue
            row = w[i]
            for j in range(len(y)):
                y[j] += xi * row[j]
        if layer["act"] == "relu":
            y = [v if v > 0 else 0.0 for v in y]
        x = y
    return x


def find_evolution_dirs():
    """Every .../config/evolution folder that has a demos folder with CSV files, newest first."""
    home = os.path.expanduser("~")
    appdata = os.environ.get("APPDATA", "")
    roots = [os.environ.get("EVOLUTION_DIR", ""),
             os.path.join(appdata, ".minecraft", "config", "evolution"),
             os.path.join(home, ".minecraft", "config", "evolution"),
             os.path.join(home, "Library", "Application Support", "minecraft", "config", "evolution")]
    patterns = [
        os.path.join(appdata, "PrismLauncher", "instances", "*", ".minecraft", "config", "evolution"),
        os.path.join(appdata, "PrismLauncher", "instances", "*", "minecraft", "config", "evolution"),
        os.path.join(home, ".local", "share", "PrismLauncher", "instances", "*", ".minecraft", "config", "evolution"),
        os.path.join(home, ".local", "share", "PrismLauncher", "instances", "*", "minecraft", "config", "evolution"),
        os.path.join(home, "curseforge", "minecraft", "Instances", "*", "config", "evolution"),
        os.path.join(home, "Documents", "curseforge", "minecraft", "Instances", "*", "config", "evolution"),
        os.path.join(appdata, "ModrinthApp", "profiles", "*", "config", "evolution"),
        os.path.join(appdata, "gdlauncher_next", "instances", "*", "config", "evolution"),
        os.path.join("*", "config", "evolution"),
        os.path.join("config", "evolution"),
    ]
    for pat in patterns:
        roots.extend(glob.glob(pat))
    found = []
    for r in roots:
        if r and os.path.isdir(os.path.join(r, "demos")):
            csvs = glob.glob(os.path.join(r, "demos", "*.csv"))
            if csvs:
                found.append((max(os.path.getmtime(c) for c in csvs), os.path.abspath(r)))
    found.sort(reverse=True)
    seen, out = set(), []
    for _, r in found:
        if r not in seen:
            seen.add(r)
            out.append(r)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--demos", default=None, help="folder with the recorded .csv files (default: found automatically)")
    ap.add_argument("--out", default=None, help="where to write model.json (default: next to the demos folder, where the mod looks)")
    ap.add_argument("--hidden", default="128,128", help="hidden layer sizes, comma separated")
    ap.add_argument("--epochs", type=int, default=40)
    ap.add_argument("--batch", type=int, default=256)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--synthetic", type=int, default=0, help="generate N synthetic rows into --demos first (pipeline self-test)")
    args = ap.parse_args()

    if args.synthetic:
        args.demos = args.demos or "demos_test"
        print("wrote", make_synthetic(args.demos, args.synthetic, seed=args.seed))
    if args.demos is None:
        dirs = find_evolution_dirs()
        if not dirs:
            sys.exit("Could not find recorded demos.\n"
                     "  1. In the game: Evolution menu > ML > Record Demos, and let Strict or Smart fight for a while.\n"
                     "  2. Run this again. If it still cannot find them, use:  python train.py --demos \"<folder from 'Copy folder path'>\"")
        args.demos = os.path.join(dirs[0], "demos")
        print("using demos from", args.demos)
        if len(dirs) > 1:
            print("(other Minecraft folders with demos were found; this is the most recent one. Use --demos to pick another.)")
    if args.out is None:
        parent = os.path.dirname(os.path.abspath(args.demos.rstrip("/\\")))
        args.out = os.path.join(parent, "model.json") if os.path.basename(os.path.abspath(args.demos)) == "demos" else "model.json"

    x, y, files = load_demos(args.demos)
    obs_size = x.shape[1]
    print(f"{len(x)} rows from {files} file(s), observation size {obs_size}")
    if len(x) < 2000:
        print("warning: very little data. Record at least 50k rows (about 40 minutes of fighting) for a usable model.")

    rng = np.random.default_rng(args.seed)
    order = rng.permutation(len(x))
    x, y = x[order], y[order]
    n_val = max(1, int(len(x) * 0.1))
    xv, yv, xt, yt = x[:n_val], y[:n_val], x[n_val:], y[n_val:]

    mean = xt.mean(axis=0)
    std = xt.std(axis=0)
    std = np.where(std < 1e-3, 1.0, std)          # constant features: leave unscaled
    xt_n = np.clip((xt - mean) / std, -5, 5).astype(np.float32)
    xv_n = np.clip((xv - mean) / std, -5, 5).astype(np.float32)

    rate = yt[:, :N_BITS].mean(axis=0)
    pos_w = np.clip(np.sqrt((1 - rate) / np.maximum(rate, 1e-3)), 1.0, 4.0).astype(np.float32)

    hidden = [int(h) for h in args.hidden.split(",") if h]
    sizes = [obs_size] + hidden + [N_ACT]
    params = init_params(sizes, rng)
    m = [[np.zeros_like(w), np.zeros_like(b)] for w, b in params]
    v = [[np.zeros_like(w), np.zeros_like(b)] for w, b in params]
    b1, b2, eps, step = 0.9, 0.999, 1e-8, 0

    t0 = time.time()
    for epoch in range(1, args.epochs + 1):
        perm = rng.permutation(len(xt_n))
        total, batches = 0.0, 0
        for s in range(0, len(perm), args.batch):
            idx = perm[s:s + args.batch]
            loss, grads = loss_and_grad(params, xt_n[idx], yt[idx], pos_w, 2.0)
            step += 1
            for li in range(len(params)):
                for k in range(2):
                    g = grads[li][k]
                    m[li][k] = b1 * m[li][k] + (1 - b1) * g
                    v[li][k] = b2 * v[li][k] + (1 - b2) * g * g
                    mh = m[li][k] / (1 - b1 ** step)
                    vh = v[li][k] / (1 - b2 ** step)
                    params[li][k] -= args.lr * mh / (np.sqrt(vh) + eps)
            total += loss
            batches += 1
        if epoch == 1 or epoch % 5 == 0 or epoch == args.epochs:
            vloss, _ = loss_and_grad(params, xv_n, yv, pos_w, 2.0)
            print(f"epoch {epoch:3d}  train loss {total / batches:.4f}  val loss {vloss:.4f}  ({time.time() - t0:.0f}s)")

    rows, mae = evaluate(params, xv_n, yv)
    print("\nvalidation (threshold 0.5):")
    for name, acc, f1, rate_k in rows:
        print(f"  {name:8s} acc {acc:.3f}  f1 {f1:.3f}  (positive rate {rate_k:.3f})")
    print(f"  yaw MAE {mae[0]:.3f}, pitch MAE {mae[1]:.3f}  (in units of 45 degrees)")

    export_model(args.out, params, mean, std, obs_size)
    # Round-trip check against a Java-style forward pass
    worst = 0.0
    for i in range(5):
        ref = forward(params, xv_n[i:i + 1])[0]
        got = java_style_forward(args.out, xv[i].tolist())
        worst = max(worst, float(np.max(np.abs(ref - np.asarray(got)))))
    print(f"\nexported {args.out}  (export round-trip max difference {worst:.2e})")
    if worst > 1e-3:
        sys.exit("export check failed")
    print("done. If it was written into your Minecraft config/evolution folder the mod loads it by itself within a few seconds.")
    print("Then set Mode to ML in the Evolution menu (Combat > Evolution AI).")


if __name__ == "__main__":
    main()
