package dev.pvpai.ui;

import dev.pvpai.config.ModConfig;
import dev.pvpai.ui.Modules.Cat;
import dev.pvpai.ui.Modules.Kind;
import dev.pvpai.ui.Modules.Module;
import dev.pvpai.ui.Modules.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Evolution menu. Categories on the left, a module list in the middle with a switch per module,
 * right-click a module for its settings. Immediate-mode: everything is drawn and hit-tested right here each frame, and the mouse
 * buttons are read straight from GLFW, so nothing depends on version-specific widget or click APIs.
 * Only the two text boxes (search, watermark text) are vanilla widgets, because typing needs them.
 */
public final class PvpAiScreen extends Screen {
    private static final int RH = 24, SRH = 27, SW = 106;
    private static final Cat[] TOP = {Cat.COMBAT, Cat.MOVEMENT, Cat.MODERN, Cat.MACE, Cat.VISUAL};
    private static final Cat[] GEN = {Cat.ML, Cat.SETTINGS};

    private final Screen parent;
    private List<Module> modules = new ArrayList<>();
    private Cat cat = Cat.COMBAT;
    private Module open;
    private int scroll;
    private String search = "";
    private TextFieldWidget searchField, textField;
    private Setting textSetting, dragging;
    private boolean wasL, wasR, dragScroll;
    private int px, py, pw, ph, cx, cw;

    public PvpAiScreen(Screen parent) {
        super(Text.literal("Evolution"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        clearChildren();
        ModConfig cfg = ModConfig.get();
        String openName = open == null ? null : open.name;
        modules = Modules.build(cfg, () -> client.setScreen(YaclScreenFactory.create(this)));
        open = null;
        if (openName != null) for (Module m : modules) if (m.name.equals(openName)) open = m;

        pw = Math.min(460, width - 8);
        ph = Math.min(264, height - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SW + 14;
        cw = pw - SW - 28;

        searchField = new TextFieldWidget(textRenderer, cx + cw - 138, py + 14, 116, 10, Text.literal("Search"));
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(24);
        searchField.setText(search);
        searchField.setChangedListener(s -> { search = s; scroll = 0; });
        addDrawableChild(searchField);

        textField = new TextFieldWidget(textRenderer, 0, 0, 110, 10, Text.literal("Text"));
        textField.setDrawsBackground(false);
        textField.setMaxLength(24);
        textField.setChangedListener(s -> { if (textSetting != null && textSetting.setS != null) textSetting.setS.accept(s); });
        textField.visible = false;
        addDrawableChild(textField);

        wasL = down(GLFW.GLFW_MOUSE_BUTTON_LEFT);     // a button already held when the menu opens must not click anything
        wasR = down(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    private boolean down(int button) {
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public void close() {
        ModConfig.save();
        client.setScreen(parent);
    }

    /** Nothing here: render() draws the whole menu (this also prevents the vanilla blur). */
    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) { }

    /** Mouse wheel scrolls the list (also reachable by dragging the scrollbar). */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.signum(verticalAmount);
        return true;
    }

    private List<Module> visible() {
        List<Module> out = new ArrayList<>();
        String q = search.trim().toLowerCase();
        for (Module m : modules) {
            if (q.isEmpty() ? m.cat == cat : (m.name.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        ModConfig cfg = ModConfig.get();
        int ac = Theme.accent(cfg);
        boolean l = down(GLFW.GLFW_MOUSE_BUTTON_LEFT), r = down(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean cl = l && !wasL, cr = r && !wasR;
        wasL = l; wasR = r;
        if (!l) { dragging = null; dragScroll = false; }

        g.fill(0, 0, width, height, 0xB0050612);
        Theme.rounded(g, px, py, pw, ph, 8, Theme.STROKE);
        Theme.rounded(g, px + 1, py + 1, pw - 2, ph - 2, 7, Theme.BG);
        Theme.rounded(g, px + 1, py + 1, SW, ph - 2, 7, Theme.SIDEBAR);
        g.fill(px + SW - 6, py + 1, px + SW, py + ph - 1, Theme.SIDEBAR);
        g.fill(px + SW, py + 10, px + SW + 1, py + ph - 10, Theme.STROKE);
        Theme.rounded(g, px + SW + 12, py + 1, pw - SW - 24, 2, 1, ac);

        sidebar(g, mx, my, cl, ac);
        header(g, mx, my, cl, ac);
        textField.visible = false;
        if (open == null) moduleList(g, mx, my, cl, cr, l, ac);
        else settingList(g, mx, my, cl, cr, l, cfg, ac);

        super.render(g, mx, my, delta);          // the two text boxes, on top
    }

    // ------------------------------------------------------------------ sidebar and header

    private void sidebar(DrawContext g, int mx, int my, boolean cl, int ac) {
        TextRenderer tr = textRenderer;
        int x = px + 8, y = py + 12;
        Theme.rounded(g, x, y + 1, 4, 16, 2, ac);
        g.drawText(tr, Text.literal("Evolution").formatted(Formatting.BOLD), x + 9, y, Theme.TEXT, true);
        g.drawText(tr, "BETA " + Watermark.version(), x + 9, y + 11, Theme.MUTED, false);

        y = py + 46;
        g.drawText(tr, "MODULES", x + 2, y, Theme.MUTED, false);
        y += 12;
        for (Cat c : TOP) { navItem(g, mx, my, cl, ac, x, y, c); y += 22; }
        y += 8;
        g.drawText(tr, "GENERAL", x + 2, y, Theme.MUTED, false);
        y += 12;
        for (Cat c : GEN) { navItem(g, mx, my, cl, ac, x, y, c); y += 22; }
    }

    private void navItem(DrawContext g, int mx, int my, boolean cl, int ac, int x, int y, Cat c) {
        TextRenderer tr = textRenderer;
        int w = SW - 16, h = 20;
        boolean sel = cat == c && search.isBlank(), hov = Theme.in(mx, my, x, y, w, h);
        if (sel) Theme.rounded(g, x, y, w, h, 5, Theme.alpha(ac, 24));
        else if (hov) Theme.rounded(g, x, y, w, h, 5, Theme.CARD);
        Theme.rounded(g, x + 6, y + 6, 8, 8, 3, sel ? ac : Theme.MUTED);
        g.drawText(tr, c.label, x + 20, y + 6, sel ? ac : hov ? Theme.TEXT : Theme.DIM, false);
        int count = 0;
        for (Module m : modules) if (m.cat == c) count++;
        String n = String.valueOf(count);
        g.drawText(tr, n, x + w - 6 - tr.getWidth(n), y + 6, Theme.MUTED, false);
        if (hov && cl) {
            cat = c; open = null; scroll = 0;
            searchField.setText("");
        }
    }

    private void header(DrawContext g, int mx, int my, boolean cl, int ac) {
        TextRenderer tr = textRenderer;
        int hy = py + 12;
        if (open == null) {
            String first = search.isBlank() ? cat.label : "Search";
            Text t1 = Text.literal(first).formatted(Formatting.BOLD, Formatting.ITALIC);
            g.drawText(tr, t1, cx, hy, ac, true);
            g.drawText(tr, Text.literal(" Modules").formatted(Formatting.BOLD), cx + tr.getWidth(t1), hy, Theme.TEXT, true);
            List<Module> v = visible();
            long on = v.stream().filter(m -> m.on != null && m.on.get()).count();
            g.drawText(tr, v.size() + " modules  |  " + on + " enabled  |  right-click a module for settings", cx, hy + 13, Theme.MUTED, false);
            Theme.card(g, cx + cw - 146, py + 9, 146, 17, 5, Theme.CARD, Theme.STROKE);
            if (searchField.getText().isEmpty() && !searchField.isFocused()) g.drawText(tr, "Search modules", cx + cw - 138, py + 14, Theme.MUTED, false);
        } else {
            Text t1 = Text.literal(open.name).formatted(Formatting.BOLD, Formatting.ITALIC);
            g.drawText(tr, t1, cx, hy, ac, true);
            g.drawText(tr, Text.literal(" Settings").formatted(Formatting.BOLD), cx + tr.getWidth(t1), hy, Theme.TEXT, true);
            g.drawText(tr, tr.trimToWidth(open.desc, cw), cx, hy + 13, Theme.MUTED, false);
            boolean hov = Theme.in(mx, my, cx, py + 38, 48, 12);
            g.drawText(tr, "< Back", cx, py + 40, hov ? ac : Theme.DIM, false);
            if (hov && cl) { open = null; scroll = 0; }
            g.drawText(tr, "right-click to go back", cx + cw - tr.getWidth("right-click to go back"), py + 40, Theme.MUTED, false);
        }
        searchField.visible = open == null;
    }

    // ------------------------------------------------------------------ module list

    private void moduleList(DrawContext g, int mx, int my, boolean cl, boolean cr, boolean l, int ac) {
        TextRenderer tr = textRenderer;
        List<Module> v = visible();
        int top = py + 42, bot = py + ph - 12;
        Theme.card(g, cx, top, cw, bot - top, 7, Theme.CARD, Theme.STROKE);
        int rows = Math.max(1, (bot - top - 8) / RH);
        scroll = scrollbar(g, mx, my, cl, l, v.size(), rows, top, bot, ac);
        int rx = cx + 4, rw = cw - 16;
        for (int i = 0; i < rows && scroll + i < v.size(); i++) {
            Module m = v.get(scroll + i);
            int y = top + 4 + i * RH;
            boolean hov = Theme.in(mx, my, rx, y, rw, RH) && !dragScroll;
            if (hov) Theme.rounded(g, rx, y, rw, RH - 1, 5, Theme.CARD_HOVER);
            g.drawText(tr, Text.literal(m.name).formatted(Formatting.BOLD), rx + 10, y + 4, Theme.TEXT, false);
            g.drawText(tr, tr.trimToWidth(m.desc, rw - 96), rx + 10, y + 14, Theme.MUTED, false);
            if (m.on != null) {
                if (!m.settings.isEmpty()) g.drawText(tr, "...", rx + rw - 54, y + 6, Theme.MUTED, false);
                toggleSwitch(g, rx + rw - 36, y + 6, m.on.get(), ac);
            } else {
                g.drawText(tr, ">", rx + rw - 14, y + 8, Theme.MUTED, false);
            }
            if (i < rows - 1 && scroll + i < v.size() - 1) g.fill(rx + 8, y + RH - 1, rx + rw - 8, y + RH, Theme.STROKE);
            if (hov && cl) {
                if (m.on != null) m.setOn.accept(!m.on.get());
                else if (!m.settings.isEmpty()) { open = m; scroll = 0; return; }
            }
            if (hov && cr && !m.settings.isEmpty()) { open = m; scroll = 0; return; }
        }
        if (v.isEmpty()) g.drawText(tr, "No modules match.", cx + 14, top + 14, Theme.MUTED, false);
    }

    // ------------------------------------------------------------------ settings

    private void settingList(DrawContext g, int mx, int my, boolean cl, boolean cr, boolean l, ModConfig cfg, int ac) {
        TextRenderer tr = textRenderer;
        List<Setting> sl = open.settings;
        int top = py + 54, bot = py + ph - 12;
        Theme.card(g, cx, top, cw, bot - top, 7, Theme.CARD, Theme.STROKE);
        int rows = Math.max(1, (bot - top - 8) / SRH);
        scroll = scrollbar(g, mx, my, cl, l, sl.size(), rows, top, bot, ac);
        int rx = cx + 4, rw = cw - 16;
        boolean usedRight = false;
        for (int i = 0; i < rows && scroll + i < sl.size(); i++) {
            Setting s = sl.get(scroll + i);
            int y = top + 4 + i * SRH;
            boolean hov = Theme.in(mx, my, rx, y, rw, SRH) && !dragScroll;
            g.drawText(tr, Text.literal(s.name).formatted(Formatting.BOLD), rx + 10, y + 4, Theme.TEXT, false);
            g.drawText(tr, tr.trimToWidth(s.desc, rw - 190), rx + 10, y + 15, Theme.MUTED, false);
            int right = rx + rw - 10;
            switch (s.kind) {
                case TOGGLE -> {
                    toggleSwitch(g, right - 26, y + 7, s.getB.get(), ac);
                    if (hov && cl) s.setB.accept(!s.getB.get());
                }
                case SLIDER -> {
                    int val = s.getI.get();
                    String vt = s.fmt.apply(val);
                    int vw = tr.getWidth(vt);
                    int tw = 84, tx = right - vw - 10 - tw, ty = y + SRH / 2 - 1;
                    Theme.rounded(g, tx, ty, tw, 3, 1, Theme.OFF);
                    int fw = (int) Math.round(tw * (val - s.min) / (double) Math.max(1, s.max - s.min));
                    if (fw > 0) Theme.rounded(g, tx, ty, Math.max(2, fw), 3, 1, ac);
                    Theme.rounded(g, tx + fw - 3, ty - 2, 7, 7, 3, Theme.TEXT);
                    g.drawText(tr, vt, right - vw, y + 9, Theme.DIM, false);
                    if (cl && Theme.in(mx, my, tx - 5, y, tw + 10, SRH)) dragging = s;
                    if (dragging == s && l) {
                        int nv = s.min + (int) Math.round((mx - tx) / (double) tw * (s.max - s.min));
                        s.setI.accept(Math.max(s.min, Math.min(s.max, nv)));
                    }
                }
                case CYCLE -> {
                    int bw = 104, bx = right - bw;
                    boolean chov = Theme.in(mx, my, bx, y + 5, bw, 16);
                    Theme.card(g, bx, y + 5, bw, 16, 5, Theme.mix(ac, Theme.BG, chov ? 0.78f : 0.86f), Theme.alpha(ac, 55));
                    String vt = tr.trimToWidth(s.getS.get(), bw - 10);
                    g.drawText(tr, vt, bx + (bw - tr.getWidth(vt)) / 2, y + 9, ac, false);
                    if (chov && cl) s.next.run();
                    if (chov && cr) { s.prev.run(); usedRight = true; }
                }
                case ACTION -> {
                    int bw = 60, bx = right - bw;
                    boolean chov = Theme.in(mx, my, bx, y + 5, bw, 16);
                    Theme.rounded(g, bx, y + 5, bw, 16, 5, chov ? Theme.mix(ac, Theme.TEXT, 0.25f) : ac);
                    g.drawText(tr, "Run", bx + (bw - tr.getWidth("Run")) / 2, y + 9, Theme.BG, false);
                    if (chov && cl) s.run.run();
                }
                case INFO -> {
                    String vt = tr.trimToWidth(s.getS.get(), 200);
                    g.drawText(tr, vt, right - tr.getWidth(vt), y + 9, Theme.DIM, false);
                }
                case TEXT -> {
                    int bw = 130, bx = right - bw;
                    Theme.card(g, bx, y + 5, bw, 16, 5, Theme.BG, textField.isFocused() ? ac : Theme.STROKE);
                    textSetting = s;
                    if (!textField.isFocused() && !textField.getText().equals(s.getS.get())) textField.setText(s.getS.get());
                    textField.setX(bx + 6);
                    textField.setY(y + 9);
                    textField.setWidth(bw - 12);
                    textField.visible = true;
                }
            }
            if (i < rows - 1 && scroll + i < sl.size() - 1) g.fill(rx + 8, y + SRH - 1, rx + rw - 8, y + SRH, Theme.STROKE);
        }
        if (cr && !usedRight) { open = null; scroll = 0; }       // right-click anywhere else goes back
    }

    // ------------------------------------------------------------------ pieces

    private void toggleSwitch(DrawContext g, int x, int y, boolean on, int ac) {
        Theme.rounded(g, x, y, 26, 12, 6, on ? ac : Theme.OFF);
        Theme.rounded(g, x + (on ? 16 : 2), y + 2, 8, 8, 4, on ? Theme.TEXT : Theme.MUTED);
    }

    /** Draws a scrollbar when the list is longer than the window and returns the clamped scroll position. */
    private int scrollbar(DrawContext g, int mx, int my, boolean cl, boolean l, int count, int rows, int top, int bot, int ac) {
        int max = Math.max(0, count - rows);
        int s = Math.max(0, Math.min(max, scroll));
        if (max == 0) return 0;
        int x = cx + cw - 9, trackTop = top + 6, trackH = bot - top - 12;
        int handleH = Math.max(14, trackH * rows / count);
        if (cl && Theme.in(mx, my, x - 4, trackTop, 12, trackH)) dragScroll = true;
        if (dragScroll && l) s = Math.max(0, Math.min(max, (int) Math.round((my - trackTop - handleH / 2.0) / Math.max(1, trackH - handleH) * max)));
        int handleY = trackTop + (trackH - handleH) * s / max;
        Theme.rounded(g, x, trackTop, 3, trackH, 1, Theme.OFF);
        Theme.rounded(g, x, handleY, 3, handleH, 1, ac);
        return s;
    }
}
