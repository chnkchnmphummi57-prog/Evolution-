package dev.pvpai.bridge;

import dev.pvpai.act.Action;

/** Step 4 plugs OnnxBackend / SocketBackend in here. The Java mod never contains training code. */
public interface InferenceBackend {
    boolean ready();
    String name();
    Action infer(float[] observation);
}
