package dev.pvpai.ml;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pvpai.act.Action;
import dev.pvpai.config.ModConfig;
import dev.pvpai.observe.Ctx;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pure-Java MLP inference (no native libraries). Loads config/evolution/model.json written by ml/train.py.
 * A network this small runs in well under a millisecond, so the 50 ms tick budget is not a concern.
 *
 * Output layout (ACT_SIZE = 10): logits for forward, back, left, right, jump, sprint, attack, use,
 * then yaw and pitch turn amounts (tanh, times 45 degrees).
 */
public final class MlBackend {
    public static final MlBackend INSTANCE = new MlBackend();
    public static final int ACT_SIZE = 10;

    private float[] mean, std;
    private float[][][] w;      // [layer][in][out]
    private float[][] b;        // [layer][out]
    private boolean[] relu;
    private volatile boolean ready;
    public volatile String status = "no model loaded";

    private MlBackend() { }

    public boolean ready() { return ready; }

    private long stamp = Long.MIN_VALUE;
    private int pollTick;

    /** Call every tick. Every ~2 seconds it checks model.json and reloads it if it changed (so training needs no clicks). */
    public void poll() {
        if (++pollTick < 40) return;
        pollTick = 0;
        try {
            Path p = modelFile();
            long m = Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : -1L;
            if (m != stamp) { stamp = m; load(); }
        } catch (java.io.IOException ignored) { }
    }

    public static Path modelFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("evolution").resolve("model.json");
    }

    public void load() {
        Path p = modelFile();
        try {
            if (!Files.exists(p)) { ready = false; status = "no model (config/evolution/model.json)"; return; }
            try (Reader r = Files.newBufferedReader(p)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                int obs = o.get("obs_size").getAsInt(), act = o.get("act_size").getAsInt();
                if (obs != Ctx.OBS_SIZE || act != ACT_SIZE) {
                    ready = false; status = "size mismatch: model " + obs + "/" + act + ", mod " + Ctx.OBS_SIZE + "/" + ACT_SIZE;
                    return;
                }
                float[] nm = floats(o.getAsJsonArray("mean")), ns = floats(o.getAsJsonArray("std"));
                JsonArray layers = o.getAsJsonArray("layers");
                int n = layers.size();
                float[][][] nw = new float[n][][];
                float[][] nb = new float[n][];
                boolean[] nr = new boolean[n];
                int prev = obs;
                for (int l = 0; l < n; l++) {
                    JsonObject lo = layers.get(l).getAsJsonObject();
                    JsonArray wa = lo.getAsJsonArray("w");
                    float[][] wl = new float[wa.size()][];
                    for (int i = 0; i < wl.length; i++) wl[i] = floats(wa.get(i).getAsJsonArray());
                    float[] bl = floats(lo.getAsJsonArray("b"));
                    if (wl.length != prev || wl[0].length != bl.length) throw new IllegalStateException("bad shape in layer " + l);
                    nw[l] = wl; nb[l] = bl; nr[l] = "relu".equals(lo.get("act").getAsString());
                    prev = bl.length;
                }
                if (prev != act || nm.length != obs || ns.length != obs) throw new IllegalStateException("bad output or normalisation size");
                mean = nm; std = ns; w = nw; b = nb; relu = nr;
                ready = true; status = "loaded, " + n + " layers";
            }
        } catch (Exception e) {
            ready = false; status = "load failed: " + e.getMessage();
        }
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    public float[] infer(float[] obs) {
        float[] x = new float[obs.length];
        for (int i = 0; i < x.length; i++) x[i] = MathHelper.clamp((obs[i] - mean[i]) / std[i], -5f, 5f);
        for (int l = 0; l < w.length; l++) {
            float[] y = b[l].clone();
            float[][] wl = w[l];
            for (int i = 0; i < wl.length; i++) {
                float xi = x[i];
                if (xi == 0f) continue;
                float[] row = wl[i];
                for (int j = 0; j < y.length; j++) y[j] += xi * row[j];
            }
            if (relu[l]) for (int j = 0; j < y.length; j++) if (y[j] < 0f) y[j] = 0f;
            x = y;
        }
        return x;
    }

    private static boolean on(float logit) { return logit >= 0f; }   // sigmoid(logit) >= 0.5

    /** Turns the network output into an Action (keys + camera goal). Safety gates are applied by the caller. */
    public Action decide(Ctx c, ModConfig cfg) {
        float[] out = infer(c.toFloats(cfg));
        Action a = new Action();
        a.forward = on(out[0]); a.back = on(out[1]); a.left = on(out[2]); a.right = on(out[3]);
        if (a.forward && a.back) { if (out[0] >= out[1]) a.back = false; else a.forward = false; }
        if (a.left && a.right) { if (out[2] >= out[3]) a.right = false; else a.left = false; }
        a.jump = on(out[4]); a.sprint = on(out[5]); a.attack = on(out[6]); a.use = on(out[7]);
        a.aim = true;
        a.goalYaw = c.yaw + (float) Math.tanh(out[8]) * 45f;
        a.goalPitch = MathHelper.clamp(c.pitch + (float) Math.tanh(out[9]) * 45f, -90f, 90f);
        if (!a.sprint && c.sprinting) a.stopSprint = true;
        return a;
    }
}
