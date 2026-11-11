package dev.pvpai.ml;

import dev.pvpai.act.Action;
import dev.pvpai.config.ModConfig;
import dev.pvpai.observe.Ctx;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes (observation, action) rows to config/evolution/demos/*.csv while a RULE mode (Strict/Smart) fights.
 * ml/train.py reads these files. Row = OBS_SIZE observation floats + ACT_SIZE action floats.
 * First line: "#obs=64,act=10".
 */
public final class Recorder {
    public static final Recorder INSTANCE = new Recorder();

    private BufferedWriter out;
    public volatile int rows;
    public volatile String file = "";

    private Recorder() { }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("evolution").resolve("demos");
    }

    public void record(Ctx c, ModConfig cfg, Action a) {
        if (!cfg.recordDemos) { close(); return; }
        try {
            if (out == null) {
                Files.createDirectories(folder());
                String name = "demo_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
                out = Files.newBufferedWriter(folder().resolve(name));
                out.write("#obs=" + Ctx.OBS_SIZE + ",act=" + MlBackend.ACT_SIZE + "\n");
                file = name; rows = 0;
            }
            float[] obs = c.toFloats(cfg);
            StringBuilder sb = new StringBuilder(512);
            for (float v : obs) sb.append(v).append(',');
            float[] act = {
                    a.forward ? 1 : 0, a.back ? 1 : 0, a.left ? 1 : 0, a.right ? 1 : 0, a.jump ? 1 : 0,
                    a.sprint && !a.stopSprint ? 1 : 0, a.attack ? 1 : 0, a.use ? 1 : 0,
                    MathHelper.clamp(MathHelper.wrapDegrees(a.goalYaw - c.yaw) / 45f, -1f, 1f),
                    MathHelper.clamp((a.goalPitch - c.pitch) / 45f, -1f, 1f)
            };
            for (int i = 0; i < act.length; i++) { sb.append(act[i]); if (i + 1 < act.length) sb.append(','); }
            sb.append('\n');
            out.write(sb.toString());
            if (++rows % 100 == 0) out.flush();
        } catch (IOException e) {
            cfg.recordDemos = false;
            close();
        }
    }

    public void close() {
        if (out == null) return;
        try { out.flush(); out.close(); } catch (IOException ignored) { }
        out = null;
    }
}
