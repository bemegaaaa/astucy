package com.astucy.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Primitivas de UI do Astucy — tudo com fill(), sem outline().
 * Marca: branco e preto (Íris).
 */
public final class Ui {

    // paleta da marca (glass premium, identidade própria)
    public static final int INK = 0xFF0C0F13;
    public static final int PANEL = 0xF20A0D11;
    public static final int CARD = 0x2E12161B;
    public static final int CARD_HOVER = 0x42171C23;
    public static final int SOFT = 0x3A191E25;
    public static final int LINE = 0x1EFFFFFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int NAME = 0xFFF5F5F5;
    public static final int DIM = 0xB3FFFFFF;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GREEN = 0xFF33C46E;
    public static final int GREEN_INK = 0xFF07230F;
    public static final int RED = 0xFFCE424E;
    public static final int RED_INK = 0xFFFFFFFF;
    public static final int BLUE = 0xFF3E7BFA;

    private Ui() {}

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) {
            g.fill(x, y, x + w, y + h, color);
        }
    }

    /** Borda 1px construída só com fills (segura em qualquer API). */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, 1, color);
        rect(g, x, y + h - 1, w, 1, color);
        rect(g, x, y, 1, h, color);
        rect(g, x + w - 1, y, 1, h, color);
    }

    /** Retângulo suave: textura AA quando disponível; senão fill uniforme. */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int color) {
        rrect(g, x, y, w, h, color, 10);
    }

    /** Retângulo com raio de canto controlado (4 = botão reto). */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int color, int radius) {
        if (Skin.ok() && Skin.box(g, x, y, w, h, color, radius)) {
            return;
        }
        if (w < 6 || h < 6) {
            rect(g, x, y, w, h, color);
            return;
        }
        int soft = (((color >>> 24) & 0xFF) / 2 << 24) | (color & 0x00FFFFFF);
        rect(g, x, y + 1, w, h - 2, color);
        rect(g, x + 1, y, w - 2, 1, color);
        rect(g, x + 1, y + h - 1, w - 2, 1, color);
        rect(g, x, y, 1, 1, soft);
        rect(g, x + w - 1, y, 1, 1, soft);
        rect(g, x, y + h - 1, 1, 1, soft);
        rect(g, x + w - 1, y + h - 1, 1, 1, soft);
    }

    /** Contorno suave 1px (anel anti-alias); fallback pixel se sem textura. */
    public static void rframe(GuiGraphics g, int x, int y, int w, int h, int color) {
        rframe(g, x, y, w, h, color, 10);
    }

    public static void rframe(GuiGraphics g, int x, int y, int w, int h, int color, int radius) {
        if (isNeutralFaint(color)) {
            return;
        }
        if (Skin.ok() && Skin.outline(g, x, y, w, h, color, radius)) {
            return;
        }
        if (w < 8 || h < 8) {
            frame(g, x, y, w, h, color);
            return;
        }
        rect(g, x + 2, y, w - 4, 1, color);
        rect(g, x + 2, y + h - 1, w - 4, 1, color);
        rect(g, x, y + 2, 1, h - 4, color);
        rect(g, x + w - 1, y + 2, 1, h - 4, color);
    }

    private static boolean isNeutralFaint(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int gr = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int spread = Math.max(r, Math.max(gr, b)) - Math.min(r, Math.min(gr, b));
        return spread < 24 && a < 0xC0;
    }

    public static void hline(GuiGraphics g, int x, int y, int w, int color) {
        rect(g, x, y, w, 1, color);
    }

    /** Texto limpo, centrado, SEM sombra. */
    public static void ctext(GuiGraphics g, Font f, String s, int cx, int y, int color) {
        g.drawString(f, s, cx - f.width(s) / 2, y, color, false);
    }

    /** Sombra macia em duas camadas (glass premium). */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h) {
        rrect(g, x - 3, y + 2, w + 6, h + 6, 0x24000000);
    }

    /** Texto caixa-alta com letter-spacing (estilo "brand"); devolve largura total. */
    public static int spaced(GuiGraphics g, Font f, String s, int x, int y, int color, int spacing) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            g.drawString(f, String.valueOf(c), cx, y, color, false);
            cx += f.width(String.valueOf(c)) + spacing;
        }
        return cx - x - spacing;
    }

    public static int spacedWidth(Font f, String s, int spacing) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += f.width(String.valueOf(s.charAt(i))) + spacing;
        }
        return Math.max(0, w - spacing);
    }

    public static int spacedCentered(GuiGraphics g, Font f, String s, int cx, int y, int color, int spacing) {
        int w = spacedWidth(f, s, spacing);
        spaced(g, f, s, cx - w / 2, y, color, spacing);
        return w;
    }

    public static void glass(GuiGraphics g, int x, int y, int w, int h, int fill, boolean hover, boolean focus) {
        rrect(g, x, y, w, h, focus ? lighten(fill, 0x14) : (hover ? lighten(fill, 0x0C) : fill));
    }

    public static int lighten(int color, int delta) {
        int a = Math.min(255, ((color >>> 24) & 0xFF) + delta);
        int r = Math.min(255, ((color >>> 16) & 0xFF) + delta * 2);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + delta * 2);
        int b = Math.min(255, (color & 0xFF) + delta * 2);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void pill(GuiGraphics g, Font f, String label, int x, int y, int w, int h,
                            boolean active, boolean hover) {
        if (active) {
            if (!Skin.boxGrad(g, x, y, w, h, 0xFF191919, 0xFF0F0F0F)) {
                rrect(g, x, y, w, h, BLUE);
            }
            ctext(g, f, label, x + w / 2, y + (h - f.lineHeight) / 2 + 1, 0xFFFFFFFF);
        } else {
            rrect(g, x, y, w, h, hover ? 0x28FFFFFF : 0x14FFFFFF);
            ctext(g, f, label, x + w / 2, y + (h - f.lineHeight) / 2 + 1, hover ? TEXT : DIM);
        }
    }

    public static boolean glass(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 4; i >= 1; i--) {
            rrect(g, x - i * 2, y - i * 2 + 3, w + i * 4, h + i * 4, 0x06000000, 12);
        }
        rrect(g, x, y, w, h, 0xCC0A0D12, 10);
        rframe(g, x, y, w, h, 0x1EFFFFFF, 10);
        return true;
    }

    public static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + ((((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * k));
        int ar = (int) (((a >>> 16) & 0xFF) + ((((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * k));
        int ag = (int) (((a >>> 8) & 0xFF) + ((((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * k));
        int ab = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * k));
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static void stateBtn(GuiGraphics g, Font f, int x, int y, int w, int h,
                                String label, boolean on) {
        boolean ok = on
                ? Skin.boxGrad(g, x, y, w, h, 0xFF38C975, 0xFF259A56, 6)
                : Skin.boxGrad(g, x, y, w, h, 0xFF232730, 0xFF191C23, 6);
        if (!ok) {
            rrect(g, x, y, w, h, on ? GREEN : 0xFF1F232B, 6);
        }
        ctext(g, f, label, x + w / 2, y + (h - f.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    public static void stateBtnAnim(GuiGraphics g, Font f, int x, int y, int w, int h,
                                    String off, String on, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int top = mix(0xFF232730, 0xFF38C975, k);
        int bot = mix(0xFF191C23, 0xFF259A56, k);
        if (!Skin.boxGrad(g, x, y, w, h, top, bot, 6)) {
            rrect(g, x, y, w, h, k > 0.5f ? GREEN : 0xFF1F232B, 6);
        }
        ctext(g, f, k > 0.5f ? on : off, x + w / 2, y + (h - f.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    public static void blueBtn(GuiGraphics g, Font f, int x, int y, int w, int h, String label) {
        boolean ok = Skin.boxGrad(g, x, y, w, h, 0xFF4B8DF5, 0xFF3571E0, 6);
        if (!ok) {
            rrect(g, x, y, w, h, BLUE, 6);
        }
        ctext(g, f, label, x + w / 2, y + (h - f.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    public static void toggle(GuiGraphics g, int x, int y, int w, int h,
                              boolean on, float anim) {
        float a = Math.max(0f, Math.min(1f, anim));
        int top = mix(0xFF07090E, 0xFF22C55E, a);
        int bot = mix(0xFF05070B, 0xFF16A34A, a);
        int r = Math.max(2, h / 2);
        if (!Skin.boxGrad(g, x, y, w, h, top, bot, r)) {
            rrect(g, x, y, w, h, mix(0xFF06080C, 0xFF22C55E, a), r);
        }
        if (a < 0.85f) {
            rframe(g, x, y, w, h, mix(0x28FFFFFF, 0x00FFFFFF, a), r);
        }
        int knob = h - 4;
        int kx = x + 2 + Math.round((w - 4 - knob) * a);
        int knobCol = mix(0xFF7B8798, 0xFFFFFFFF, a);
        if (!Skin.disc(g, kx, y + 2, knob, knobCol)) {
            rrect(g, kx, y + 2, knob, knob, knobCol, knob / 2);
        }
    }

    public static void sliderV2(GuiGraphics g, int x, int y, int w, float frac) {
        sliderV3(g, x, y, w, frac, 0f);
    }

    public static void sliderV3(GuiGraphics g, int x, int y, int w, float frac, float glow) {
        float f = Math.max(0f, Math.min(1f, frac));
        float gl = Math.max(0f, Math.min(1f, glow));
        int ty = y + 5;

        rrect(g, x, ty, w, 4, 0x26FFFFFF, 2);

        int fill = Math.max(4, Math.round(w * f));
        int fillCol = mix(0xFFE2E8F0, 0xFF5CE69B, gl);
        rrect(g, x, ty, fill, 4, fillCol, 2);

        int kc = x + Math.round(w * f);
        int cy = ty + 2;
        if (gl > 0.05f) {
            int haloA = Math.round(0x38 * gl);
            Skin.disc(g, kc - 7, cy - 7, 14, (haloA << 24) | 0x5CE69B);
        }
        int ks = 10;
        if (!Skin.disc(g, kc - ks / 2, cy - ks / 2, ks, 0xFFFFFFFF)) {
            rrect(g, kc - 4, cy - 4, 8, 8, 0xFFFFFFFF, 4);
        }
    }

    public static void tooltip(GuiGraphics g, Font f, int x, int y, String text) {
        int w = f.width(text) + 16;
        int h = f.lineHeight + 10;
        rrect(g, x - 1, y + 1, w + 2, h + 2, 0x38000000, 6);
        rrect(g, x, y, w, h, 0xF412161D, 6);
        rframe(g, x, y, w, h, 0x28FFFFFF, 6);
        g.drawString(f, text, x + 8, y + 5, 0xFFECEFF4, false);
    }

    public static void scrollbar(GuiGraphics g, int cx, int trackY, int trackH,
                                 int thumbY, int thumbH, float hot, float active) {
        float h = Math.max(0f, Math.min(1f, hot));
        float a = Math.max(0f, Math.min(1f, active));
        float lit = Math.max(h, a);

        int trackA = (int) (0x0A + 0x14 * h);
        rrect(g, cx - 1, trackY, 3, trackH, (trackA << 24) | 0xFFFFFF, 2);

        int w = 4 + Math.round(3.0f * lit);
        int tx = cx - w / 2;
        int th = Math.min(thumbH, trackH);
        int ty = Math.max(trackY, Math.min(trackY + trackH - th, thumbY));

        if (lit > 0.05f) {
            rrect(g, tx - 2, ty - 2, w + 4, th + 4, (((int) (0x14 * lit)) << 24) | 0x6FE3A2, (w + 4) / 2 + 1);
        }

        int rgb = mix(0xFFFFFF, 0x9FF6CC, a) & 0xFFFFFF;
        int alpha = (int) (0x40 + 0x9C * lit);
        rrect(g, tx, ty, w, thumbH, (alpha << 24) | rgb, w / 2 + 1);

        if (w >= 5 && thumbH >= 8) {
            rrect(g, tx + 1, ty + 1, w - 2, Math.max(1, th / 3),
                    (((int) (0x30 * lit)) << 24) | 0xFFFFFF, (w - 2) / 2 + 1);
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, PANEL);
        frame(g, x, y, w, h, LINE);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean focus) {
        rect(g, x, y, w, h, hover ? CARD_HOVER : CARD);
        frame(g, x, y, w, h, focus ? WHITE : LINE);
    }

    public static void button(GuiGraphics g, Font f, int x, int y, int w, int h,
                              String label, boolean active, boolean focus) {
        rrect(g, x, y, w, h, active ? WHITE : (focus ? 0x4E191E25 : SOFT));
        ctext(g, f, label, x + w / 2, y + (h - f.lineHeight) / 2 + 1, active ? INK : TEXT);
    }

    public static void slider(GuiGraphics g, int x, int y, int w, float frac) {
        float f = Math.max(0f, Math.min(1f, frac));
        rrect(g, x, y + 3, w, 4, 0x46FFFFFF);
        rrect(g, x, y + 3, Math.round(w * f), 4, WHITE);
        int kx = x + Math.round(w * f);
        shadow(g, kx - 2, y + 1, 5, 8);
        rrect(g, kx - 2, y + 1, 5, 8, WHITE);
    }

    public static int swatch(GuiGraphics g, int x, int y, int size, int color, boolean selected) {
        rrect(g, x, y, size, size, color, 4);
        if (selected) {
            rframe(g, x - 1, y - 1, size + 2, size + 2, WHITE, 5);
        } else {
            rframe(g, x, y, size, size, 0x38FFFFFF, 4);
        }
        return size + 3;
    }

    public static void icon(GuiGraphics g, String id, int x, int y, int color) {
        icon(g, id, x, y, 18, color);
    }

    public static void icon(GuiGraphics g, String id, int x, int y, int size, int color) {
        if (Skin.ok() && Skin.icon(g, id, x, y, size, color)) {
            return;
        }
        pixelIcon(g, id, x, y, color);
    }

    private static void pixelIcon(GuiGraphics g, String id, int x, int y, int color) {
        switch (id) {
            case "fps" -> {
                rect(g, x + 1, y + 8, 3, 5, color);
                rect(g, x + 6, y + 5, 3, 8, color);
                rect(g, x + 11, y + 2, 3, 11, color);
            }
            case "ping" -> {
                rect(g, x + 1, y + 10, 3, 3, color);
                rect(g, x + 6, y + 7, 3, 6, color);
                rect(g, x + 11, y + 3, 3, 10, color);
            }
            case "coords", "reach" -> {
                rect(g, x + 7, y + 1, 1, 13, color);
                rect(g, x + 1, y + 7, 13, 1, color);
            }
            case "server" -> {
                rect(g, x + 2, y + 3, 11, 4, color);
                rect(g, x + 2, y + 9, 11, 4, color);
            }
            case "memory" -> {
                rect(g, x + 2, y + 3, 11, 3, color);
                rect(g, x + 2, y + 8, 11, 3, color);
                rect(g, x + 2, y + 13, 7, 1, color);
            }
            case "speed" -> {
                rect(g, x + 1, y + 7, 8, 1, color);
                rect(g, x + 6, y + 4, 3, 1, color);
                rect(g, x + 6, y + 10, 3, 1, color);
                rect(g, x + 8, y + 7, 6, 1, color);
                rect(g, x + 11, y + 4, 3, 1, color);
                rect(g, x + 11, y + 10, 3, 1, color);
            }
            case "cps" -> {
                frame(g, x + 4, y + 2, 7, 11, color);
                rect(g, x + 7, y + 3, 1, 4, color);
            }
            case "keystrokes" -> {
                rect(g, x + 1, y + 1, 5, 5, color);
                rect(g, x + 9, y + 1, 5, 5, color);
                rect(g, x + 1, y + 9, 5, 5, color);
                rect(g, x + 9, y + 9, 5, 5, color);
            }
            case "totem" -> {
                rect(g, x + 6, y + 1, 3, 3, color);
                rect(g, x + 4, y + 4, 7, 3, color);
                rect(g, x + 2, y + 7, 11, 3, color);
                rect(g, x + 4, y + 10, 7, 2, color);
                rect(g, x + 6, y + 12, 3, 2, color);
            }
            case "armor" -> {
                rect(g, x + 2, y + 2, 11, 2, color);
                rect(g, x + 2, y + 4, 11, 6, color);
                rect(g, x + 5, y + 10, 5, 3, color);
            }
            case "hora" -> {
                frame(g, x + 2, y + 2, 11, 11, color);
                rect(g, x + 7, y + 4, 1, 5, color);
                rect(g, x + 7, y + 8, 3, 1, color);
            }
            case "bioma" -> {
                rect(g, x + 6, y + 8, 2, 5, color);
                frame(g, x + 3, y + 2, 10, 7, color);
                rect(g, x + 4, y + 3, 8, 5, color);
            }
            case "direcao" -> {
                frame(g, x + 2, y + 2, 11, 11, color);
                rect(g, x + 7, y + 4, 1, 4, color);
                rect(g, x + 7, y + 9, 1, 2, color);
                rect(g, x + 6, y + 2, 3, 1, color);
            }
            case "sessao" -> {
                rect(g, x + 3, y + 2, 9, 1, color);
                rect(g, x + 3, y + 12, 9, 1, color);
                rect(g, x + 5, y + 4, 1, 1, color);
                rect(g, x + 9, y + 4, 1, 1, color);
                rect(g, x + 6, y + 5, 1, 1, color);
                rect(g, x + 8, y + 5, 1, 1, color);
                rect(g, x + 7, y + 6, 1, 1, color);
                rect(g, x + 6, y + 7, 1, 1, color);
                rect(g, x + 8, y + 7, 1, 1, color);
                rect(g, x + 5, y + 8, 1, 1, color);
                rect(g, x + 9, y + 8, 1, 1, color);
            }
            case "efeitos" -> {
                rect(g, x + 6, y + 1, 3, 2, color);
                rect(g, x + 7, y + 4, 1, 2, color);
                rect(g, x + 5, y + 6, 5, 3, color);
                rect(g, x + 3, y + 9, 9, 4, color);
            }
            case "options" -> {
                rect(g, x + 1, y + 2, 13, 1, color);
                rect(g, x + 1, y + 7, 13, 1, color);
                rect(g, x + 1, y + 12, 13, 1, color);
                rect(g, x + 3, y + 1, 2, 3, color);
                rect(g, x + 9, y + 6, 2, 3, color);
                rect(g, x + 5, y + 11, 2, 3, color);
            }
            case "fullbright" -> {
                rect(g, x + 5, y + 5, 5, 5, color);
                rect(g, x + 6, y + 1, 3, 2, color);
                rect(g, x + 6, y + 12, 3, 2, color);
                rect(g, x + 1, y + 6, 2, 3, color);
                rect(g, x + 12, y + 6, 2, 3, color);
            }
            case "blur" -> {
                rect(g, x + 1, y + 3, 12, 2, color);
                rect(g, x + 3, y + 7, 9, 2, color);
                rect(g, x + 5, y + 11, 6, 2, color);
            }
            case "cit" -> {
                frame(g, x + 2, y + 4, 11, 9, color);
                rect(g, x + 5, y + 2, 5, 2, color);
                rect(g, x + 6, y + 7, 3, 3, color);
            }
            default -> {
                frame(g, x + 3, y + 3, 9, 9, color);
                rect(g, x + 6, y + 6, 3, 3, color);
            }
        }
    }

    public static void closeBox(GuiGraphics g, Font f, int x, int y, int s) {
        rrect(g, x, y, s, s, SOFT);
        ctext(g, f, "X", x + s / 2, y + (s - f.lineHeight) / 2 + 1, TEXT);
    }
}
