package com.astucy.client.gui;

import com.astucy.client.AstucyFonts;
import com.astucy.client.config.AstucyConfig;
import com.astucy.client.module.Modules;
import com.astucy.client.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Client settings — o que no Feather fica espalhado em "Settings":
 * vídeo, HUD, interface e atalhos, tudo aplicado na hora (sem reiniciar).
 *
 * Aberto pelo canto da sidebar do menu R-SHIFT, pelo botão do menu de pausa
 * e pelo Mod Menu (quando instalado).
 */
public final class ClientSettingsScreen extends Screen {

    /* ------------------------------------------------------------------
     * linhas
     * ------------------------------------------------------------------ */

    private static final class Opt {
        String label = "";
        String tip = "";
        boolean section;
        int kind; // 0 toggle, 1 slider, 2 button, 3 info
        double min, max, step;
        DoubleSupplier getN;
        DoubleConsumer setN;
        BooleanSupplier getB;
        Consumer<Boolean> setB;
        Runnable action;
        Supplier<String> text;
    }

    private final Screen parent;
    private final List<Opt> opts = new ArrayList<>();
    private final java.util.Map<String, Float> anims = new java.util.HashMap<>();

    private int x0, y0, pw, ph, listTop, listH;
    private double mx, my;
    private float scroll, scrollTarget;
    private Opt drag;
    private long openedAt;

    public ClientSettingsScreen() {
        this(null);
    }

    public ClientSettingsScreen(Screen parent) {
        super(Component.literal("Astucy settings"));
        this.parent = parent;
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    /* ------------------------------------------------------------------
     * construção das linhas
     * ------------------------------------------------------------------ */

    private Opt section(String label) {
        Opt o = new Opt();
        o.label = label;
        o.section = true;
        return o;
    }

    private Opt toggle(String label, String tip, BooleanSupplier get, Consumer<Boolean> set) {
        Opt o = new Opt();
        o.label = label;
        o.tip = tip;
        o.kind = 0;
        o.getB = get;
        o.setB = set;
        return o;
    }

    private Opt slider(String label, String tip, double min, double max, double step,
                       DoubleSupplier get, DoubleConsumer set, Supplier<String> text) {
        Opt o = new Opt();
        o.label = label;
        o.tip = tip;
        o.kind = 1;
        o.min = min;
        o.max = max;
        o.step = step;
        o.getN = get;
        o.setN = set;
        o.text = text;
        return o;
    }

    private Opt button(String label, String tip, Runnable action) {
        Opt o = new Opt();
        o.label = label;
        o.tip = tip;
        o.kind = 2;
        o.action = action;
        return o;
    }

    private Opt info(String label, Supplier<String> text) {
        Opt o = new Opt();
        o.label = label;
        o.kind = 3;
        o.text = text;
        return o;
    }

    private void build() {
        opts.clear();
        Minecraft mc = mc();

        opts.add(section("GAME"));
        opts.add(slider("Field of view", "Vertical FOV — 70 is the default",
                30, 110, 1,
                () -> mc.options.fov().get(),
                v -> mc.options.fov().set((int) v),
                () -> mc.options.fov().get() + "\u00B0"));
        opts.add(slider("Render distance", "Chunks drawn around you",
                2, 24, 1,
                () -> mc.options.renderDistance().get(),
                v -> mc.options.renderDistance().set((int) v),
                () -> mc.options.renderDistance().get() + " chunks"));
        opts.add(slider("Simulation distance", "Entities and ticking around you",
                5, 24, 1,
                () -> mc.options.simulationDistance().get(),
                v -> mc.options.simulationDistance().set((int) v),
                () -> mc.options.simulationDistance().get() + " chunks"));
        opts.add(slider("FPS limit", "Cap the framerate — \u221E means unlimited",
                30, 260, 10,
                () -> mc.options.framerateLimit().get(),
                v -> mc.options.framerateLimit().set((int) v),
                () -> mc.options.framerateLimit().get() >= 260
                        ? "\u221E" : mc.options.framerateLimit().get() + " fps"));
        opts.add(toggle("VSync", "Sync frames with the monitor",
                () -> mc.options.enableVsync().get(),
                v -> mc.options.enableVsync().set(v)));
        opts.add(toggle("Entity shadows", "Shadow under every mob",
                () -> mc.options.entityShadows().get(),
                v -> mc.options.entityShadows().set(v)));
        opts.add(toggle("View bobbing", "Camera moves while walking",
                () -> mc.options.bobView().get(),
                v -> mc.options.bobView().set(v)));
        opts.add(toggle("Low particles", "Fewer particles on screen",
                () -> mc.options.particles().get() != ParticleStatus.ALL,
                v -> mc.options.particles().set(v ? ParticleStatus.DECREASED : ParticleStatus.ALL)));
        opts.add(slider("Brightness", "Gamma — 100% sees in the dark",
                0, 1, 0.05,
                () -> mc.options.gamma().get(),
                v -> mc.options.gamma().set(v),
                () -> Math.round(mc.options.gamma().get() * 100) + "%"));
        opts.add(slider("GUI scale", "0 = automatic",
                0, 4, 1,
                () -> mc.options.guiScale().get(),
                v -> mc.options.guiScale().set((int) v),
                () -> {
                    int g = mc.options.guiScale().get();
                    return g == 0 ? "Auto" : String.valueOf(g);
                }));

        opts.add(section("HUD & MENU"));
        opts.add(button("Edit HUD", "Move, scale and restyle every chip",
                () -> mc.gui.setScreen(new HudEditorScreen())));
        opts.add(button("Module menu", "Same as pressing R-SHIFT",
                () -> mc.gui.setScreen(new RShiftScreen())));
        opts.add(toggle("Menu animations", "Smooth slides and fades",
                () -> AstucyConfig.prefBool("anim", true),
                v -> AstucyConfig.setPref("anim", v)));
        opts.add(toggle("UI sounds", "Little click when toggling",
                () -> AstucyConfig.prefBool("sounds", true),
                v -> AstucyConfig.setPref("sounds", v)));

        opts.add(section("CLIENT"));
        opts.add(info("Modules", () -> Modules.all().size() + " available"));
        opts.add(info("Mods (Fabric)", () -> FabricLoader.getInstance().getAllMods().size() + " loaded"));
        opts.add(info("Version", () -> "Astucy 0.1.0"));
        opts.add(button("Config folder", "Where astucyc.json lives", this::openConfigFolder));

        opts.add(section("SHORTCUTS"));
        opts.add(info("Module menu", () -> "R-SHIFT"));
        opts.add(info("Zoom", () -> "C (hold or toggle)"));
        opts.add(info("Account switch", () -> "Menu \u2192 account chip"));
    }

    private void openConfigFolder() {
        try {
            java.awt.Desktop.getDesktop().open(
                    FabricLoader.getInstance().getConfigDir().toFile());
        } catch (Throwable ignored) {
            // sem AWT/desktop: não faz nada
        }
    }

    private void click() {
        if (!AstucyConfig.prefBool("sounds", true)) {
            return;
        }
        try {
            mc().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {
            // som indisponível
        }
    }

    /* ------------------------------------------------------------------
     * geometria
     * ------------------------------------------------------------------ */

    private void layout() {
        pw = Math.min(560, width - 40);
        ph = Math.min(560, height - 40);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2;
        listTop = y0 + 52;
        listH = ph - 52 - 32;
    }

    private static int rowH(Opt o) {
        return o.section ? 26 : 30;
    }

    private int contentH() {
        int h = 0;
        for (Opt o : opts) {
            h += rowH(o);
        }
        return h + 6;
    }

    private int maxScroll() {
        return Math.max(0, contentH() - listH);
    }

    private int rowY(int index) {
        int y = listTop + 2 - Math.round(scroll);
        for (int i = 0; i < index; i++) {
            y += rowH(opts.get(i));
        }
        return y;
    }

    private int sliderX() {
        return x0 + pw - 210;
    }

    private int sliderW() {
        return 124;
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static boolean leftDown() {
        try {
            return GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().handle(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /* ------------------------------------------------------------------
     * ciclo de vida
     * ------------------------------------------------------------------ */

    @Override
    protected void init() {
        super.init();
        layout();
        build();
        openedAt = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        AstucyConfig.save();
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    /* ------------------------------------------------------------------
     * input
     * ------------------------------------------------------------------ */

    @Override
    public boolean mouseScrolled(double x, double y, double dX, double dY) {
        if (inside((int) x, (int) y, x0, listTop, pw, listH)) {
            scrollTarget = Math.max(0, Math.min(maxScroll(),
                    scrollTarget - (float) dY * 26f));
            return true;
        }
        return super.mouseScrolled(x, y, dX, dY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        layout();
        this.mx = event.x();
        this.my = event.y();
        drag = null;
        int px = (int) mx;
        int py = (int) my;

        if (inside(px, py, x0 + pw - 26, y0 + 10, 18, 18)) {
            click();
            onClose();
            return true;
        }

        if (!inside(px, py, x0, listTop, pw, listH)) {
            return super.mouseClicked(event, dbl);
        }

        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            int ry = rowY(i);
            if (ry + rowH(o) < listTop || ry > listTop + listH) {
                continue;
            }
            if (!inside(px, py, x0 + 6, ry, pw - 12, rowH(o))) {
                continue;
            }
            if (o.section) {
                return true;
            }
            switch (o.kind) {
                case 0 -> {
                    boolean v = !o.getB.getAsBoolean();
                    o.setB.accept(v);
                    click();
                }
                case 1 -> {
                    if (px < sliderX() - 8 || px > sliderX() + sliderW() + 8) {
                        return true;
                    }
                    drag = o;
                    applySlider(px);
                    click();
                }
                case 2 -> {
                    click();
                    o.action.run();
                }
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(event, dbl);
    }

    private void applySlider(int px) {
        if (drag == null) {
            return;
        }
        double frac = (px - sliderX()) / (double) sliderW();
        frac = Math.max(0, Math.min(1, frac));
        double raw = drag.min + frac * (drag.max - drag.min);
        double step = drag.step <= 0 ? 1 : drag.step;
        double v = Math.round(raw / step) * step;
        drag.setN.accept(Math.max(drag.min, Math.min(drag.max, v)));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        drag = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /* ------------------------------------------------------------------
     * desenho
     * ------------------------------------------------------------------ */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        layout();
        Font f = AstucyFonts.ui();

        // cursor ao vivo (durante o arrasto o frame congela)
        Minecraft mc = mc();
        this.mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
        this.my = mc.mouseHandler.getScaledYPos(mc.getWindow());

        if (drag != null) {
            if (leftDown()) {
                applySlider((int) mx);
            } else {
                drag = null;
            }
        }

        float k = 0.28f;
        scroll += (scrollTarget - scroll) * k;
        if (Math.abs(scrollTarget - scroll) < 0.02f) {
            scroll = scrollTarget;
        }

        // fundo desfocado + painel glass
        try {
            g.blurBeforeThisStratum();
        } catch (IllegalStateException ignored) {
        }
        Ui.rect(g, 0, 0, width, height, 0x4D000000);
        Ui.glass(g, x0, y0, pw, ph);

        // cabeçalho
        Ui.spaced(g, AstucyFonts.brand(), "ASTUCY", x0 + 18,
                y0 + (46 - AstucyFonts.brand().lineHeight) / 2 + 1, Ui.TEXT, 2);
        String sub = "CLIENT SETTINGS";
        g.text(AstucyFonts.ui8(), sub,
                x0 + 18 + Ui.spacedWidth(AstucyFonts.brand(), "ASTUCY", 2) + 10,
                y0 + 20, 0xFF6FE3A2, false);

        boolean closeHover = inside((int) mx, (int) my, x0 + pw - 26, y0 + 10, 18, 18);
        Ui.rrect(g, x0 + pw - 26, y0 + 10, 18, 18,
                closeHover ? 0x38E25661 : 0x18FFFFFF, 6);
        Ui.rframe(g, x0 + pw - 26, y0 + 10, 18, 18,
                closeHover ? 0x88E25661 : 0x22FFFFFF, 6);
        Ui.ctext(g, f, "X", x0 + pw - 17, y0 + 15, closeHover ? Ui.WHITE : 0xB8E4E9EE);

        // lista
        g.enableScissor(x0 + 2, listTop, x0 + pw - 2, listTop + listH);
        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            int ry = rowY(i);
            int rh = rowH(o);
            if (ry + rh < listTop - 2 || ry > listTop + listH + 2) {
                continue;
            }
            if (o.section) {
                g.text(AstucyFonts.ui8(), o.label, x0 + 20, ry + 12, 0xFF6FE3A2, false);
                continue;
            }

            boolean hover = inside((int) mx, (int) my, x0 + 6, ry, pw - 12, rh)
                    && inside((int) mx, (int) my, x0, listTop, pw, listH);
            if (hover) {
                Ui.rrect(g, x0 + 8, ry + 1, pw - 16, rh - 4, 0x14FFFFFF, 6);
                Ui.rrect(g, x0 + 10, ry + 5, 2, rh - 12, 0xFF38C975, 1);
            }

            g.text(f, o.label, x0 + 20,
                    ry + (rh - f.lineHeight) / 2 + 1, hover ? Ui.TEXT : Ui.NAME, false);

            switch (o.kind) {
                case 0 -> {
                    boolean on = o.getB.getAsBoolean();
                    float a = anims.getOrDefault(o.label, on ? 1f : 0f);
                    a += ((on ? 1f : 0f) - a) * 0.3f;
                    if (Math.abs(a - (on ? 1f : 0f)) < 0.01f) {
                        a = on ? 1f : 0f;
                    }
                    anims.put(o.label, a);
                    Ui.toggle(g, x0 + pw - 58,
                            ry + (rh - 18) / 2, 32, 18, on, a);
                }
                case 1 -> {
                    double v = o.getN.getAsDouble();
                    double frac = (v - o.min) / (o.max - o.min);
                    Ui.sliderV3(g, sliderX(), ry + (rh - 12) / 2, sliderW(),
                            (float) frac, hover ? 0.7f : 0.25f);
                    String val = o.text.get();
                    g.text(f, val, x0 + pw - 18 - f.width(val),
                            ry + (rh - f.lineHeight) / 2 + 1, Ui.TEXT, false);
                }
                case 2 -> {
                    int bx = x0 + pw - 158;
                    int bw = 140;
                    int by = ry + (rh - 22) / 2;
                    Ui.rrect(g, bx, by, bw, 22,
                            Ui.mix(0x18FFFFFF, 0x2C38C975, hover ? 1f : 0f), 6);
                    Ui.rframe(g, bx, by, bw, 22,
                            Ui.mix(0x22FFFFFF, 0x8838C975, hover ? 1f : 0f), 6);
                    Ui.ctext(g, f, formatButton(o.label), bx + bw / 2, by + 6,
                            hover ? Ui.TEXT : Ui.NAME);
                }
                default -> {
                    String val = o.text.get();
                    g.text(f, val, x0 + pw - 20 - f.width(val),
                            ry + (rh - f.lineHeight) / 2 + 1, Ui.DIM, false);
                }
            }
        }
        g.disableScissor();

        // barra de rolagem
        int track = listH - 8;
        int thumb = Math.max(24, Math.round(track * (listH / (float) Math.max(listH, contentH()))));
        int ty = listTop + 4 + Math.round((track - thumb) * (maxScroll() == 0 ? 0
                : scroll / (float) maxScroll()));
        if (maxScroll() > 0) {
            boolean hot = inside((int) mx, (int) my, x0 + pw - 12, ty, 12, thumb);
            Ui.scrollbar(g, x0 + pw - 6, listTop + 4, track, ty, thumb,
                    hot ? 1f : 0f, drag == null ? 0f : 1f);
        }

        // rodapé
        Font small = AstucyFonts.ui8();
        g.text(small, "Astucy Client \u00B7 applies instantly", x0 + 18,
                y0 + ph - 16, 0x669AA2AB, false);
        String hint = "ESC closes";
        g.text(small, hint, x0 + pw - 18 - small.width(hint),
                y0 + ph - 16, 0x4E9AA2AB, false);

        // fade curto na abertura
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
        if (open < 1f) {
            Ui.rect(g, 0, 0, width, height, ((int) ((1f - open) * 190)) << 24);
        }
    }

    /** "Edit HUD" vira "EDIT HUD" para combinar com o resto do menu. */
    private static String formatButton(String label) {
        return label.toUpperCase(java.util.Locale.ROOT);
    }
}
