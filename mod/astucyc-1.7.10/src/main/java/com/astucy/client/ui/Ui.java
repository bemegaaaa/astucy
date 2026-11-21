package com.astucy.client.ui;

import com.astucy.client.config.AstucyConfig;
import com.mojang.blaze3d.platform.GLX;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

public final class Ui {
    private static final int SCALE_FACTOR = 2;

    private Ui() {}

    public static boolean isLight() {
        return AstucyConfig.get().light_mode;
    }

    public static int accent() {
        return AstucyConfig.get().menu_accent | 0xFF000000;
    }

    public static int bgShell() {
        return isLight() ? 0xF5F4F6F9 : 0xF20B0E13;
    }

    public static int bgPanel() {
        return isLight() ? 0xFFFBFBFC : 0xFF12161E;
    }

    public static int bgCard() {
        return isLight() ? 0xFFFFFFFF : 0xFF171C26;
    }

    public static int bgCardHover() {
        return isLight() ? 0xFFF1F5F9 : 0xFF1D2330;
    }

    public static int bgInput() {
        return isLight() ? 0xFFEAEFF5 : 0xFF0E1218;
    }

    public static int fgPrimary() {
        return isLight() ? 0xFF111827 : 0xFFF3F6FA;
    }

    public static int fgMuted() {
        return isLight() ? 0xFF64748B : 0xFF8B95A7;
    }

    public static int fgDim() {
        return isLight() ? 0xFF94A3B8 : 0xFF566074;
    }

    public static int borderSubtle() {
        return isLight() ? 0x220F172A : 0x1EFFFFFF;
    }

    public static int withA(int argb, float alphaFactor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp(alphaFactor, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int setA(int rgb, int a) {
        return ((a & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        return Skin.lerpColor(a, b, t);
    }

    public static void rect(float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + w);
        int y2 = Math.round(y + h);
        if (x2 <= x1) x2 = x1 + 1;
        if (y2 <= y1) y2 = y1 + 1;
        GL11.glEnable(GL11.GL_BLEND);
        GLX.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        DrawableHelper.fill(x1, y1, x2, y2, color);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void frame(float x, float y, float w, float h, float t, int color) {
        rect(x, y, w, t, color);
        rect(x, y + h - t, w, t, color);
        rect(x, y + t, t, h - t * 2f, color);
        rect(x + w - t, y + t, t, h - t * 2f, color);
    }

    public static void rrect(float x, float y, float w, float h, float r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0.5f) {
            rect(x, y, w, h, color);
            return;
        }
        int pw = Math.max(2, Math.round(w * SCALE_FACTOR));
        int ph = Math.max(2, Math.round(h * SCALE_FACTOR));
        Identifier tex = Skin.box(pw, ph, r * SCALE_FACTOR, color);
        Skin.drawTex(tex, x, y, w, h, 0xFFFFFFFF);
    }

    public static void rrectGrad(float x, float y, float w, float h, float r, int topColor, int botColor) {
        if (w <= 0 || h <= 0) return;
        int pw = Math.max(2, Math.round(w * SCALE_FACTOR));
        int ph = Math.max(2, Math.round(h * SCALE_FACTOR));
        Identifier tex = Skin.boxGrad(pw, ph, r * SCALE_FACTOR, topColor, botColor);
        Skin.drawTex(tex, x, y, w, h, 0xFFFFFFFF);
    }

    public static void rframe(float x, float y, float w, float h, float r, float thick, int color) {
        if (w <= 0 || h <= 0) return;
        int pw = Math.max(2, Math.round(w * SCALE_FACTOR));
        int ph = Math.max(2, Math.round(h * SCALE_FACTOR));
        Identifier tex = Skin.outline(pw, ph, r * SCALE_FACTOR, Math.max(1f, thick * SCALE_FACTOR), color);
        Skin.drawTex(tex, x, y, w, h, 0xFFFFFFFF);
    }

    public static void disc(float cx, float cy, float radius, int color) {
        int s = Math.max(8, Math.round(radius * 2f * SCALE_FACTOR));
        Identifier tex = Skin.disc(s, color);
        Skin.drawTex(tex, cx - radius, cy - radius, radius * 2f, radius * 2f, 0xFFFFFFFF);
    }

    public static void glow(float cx, float cy, float radius, int color) {
        int s = Math.max(16, Math.round(radius * 2f));
        Identifier tex = Skin.glow(s, color);
        Skin.drawTex(tex, cx - radius, cy - radius, radius * 2f, radius * 2f, setA(0xFFFFFF, (color >>> 24) & 0xFF));
    }

    public static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static float tw(String s) {
        if (s == null || s.isEmpty()) return 0f;
        return tr().getStringWidth(s);
    }

    public static float tw(String s, float scale) {
        return tw(s) * scale;
    }

    public static float th() {
        return tr().fontHeight;
    }

    public static void text(String s, float x, float y, int color) {
        if (s == null || s.isEmpty()) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        tr().draw(s, Math.round(x), Math.round(y), color);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void textS(String s, float x, float y, int color) {
        if (s == null || s.isEmpty()) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        tr().draw(s, Math.round(x), Math.round(y), color, true);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void scaledText(String s, float x, float y, float scale, int color, boolean shadow) {
        if (s == null || s.isEmpty()) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0f);
        GL11.glScalef(scale, scale, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        tr().draw(s, 0, 0, color, shadow);
        GL11.glPopMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void ctext(String s, float cx, float y, int color, boolean shadow) {
        float w = tw(s);
        if (shadow) textS(s, cx - w * 0.5f, y, color);
        else text(s, cx - w * 0.5f, y, color);
    }

    public static void ctextScaled(String s, float cx, float y, float scale, int color, boolean shadow) {
        float w = tw(s) * scale;
        scaledText(s, cx - w * 0.5f, y, scale, color, shadow);
    }

    public static void toggle(float x, float y, float w, float h, float anim, int accentColor) {
        int offBg = isLight() ? 0xFFCBD5E1 : 0xFF28303E;
        int track = mix(offBg, accentColor, clamp(anim, 0f, 1f));
        rrect(x, y, w, h, h * 0.5f, track);
        float knobR = (h - 4f) * 0.5f;
        float kx = x + 2f + knobR + (w - 4f - knobR * 2f) * clamp(anim, 0f, 1f);
        disc(kx, y + h * 0.5f, knobR, 0xFFFFFFFF);
    }

    public static void slider(float x, float y, float w, float norm, int accentColor) {
        float n = clamp(norm, 0f, 1f);
        int track = isLight() ? 0xFFD9E1EB : 0xFF232A36;
        rrect(x, y + 3f, w, 4f, 2f, track);
        float fw = Math.max(4f, w * n);
        rrect(x, y + 3f, fw, 4f, 2f, accentColor);
        disc(x + w * n, y + 5f, 4.5f, 0xFFFFFFFF);
        disc(x + w * n, y + 5f, 2.5f, accentColor);
    }

    public static void swatch(float x, float y, float size, int color, boolean selected) {
        rrect(x, y, size, size, 4f, color);
        if (selected) {
            rframe(x - 1.5f, y - 1.5f, size + 3f, size + 3f, 5f, 1.5f, 0xFFFFFFFF);
        } else {
            rframe(x, y, size, size, 4f, 1f, borderSubtle());
        }
    }

    public static void scrollbar(float x, float y, float h, float viewH, float contentH, float scroll) {
        if (contentH <= viewH + 1f) return;
        float ratio = clamp(viewH / contentH, 0.08f, 1f);
        float thumbH = Math.max(18f, h * ratio);
        float maxScroll = Math.max(1f, contentH - viewH);
        float thumbY = y + (h - thumbH) * clamp(scroll / maxScroll, 0f, 1f);
        rrect(x, y, 3f, h, 1.5f, withA(fgDim(), 0.25f));
        rrect(x, thumbY, 3f, thumbH, 1.5f, withA(accent(), 0.7f));
    }

    public static void pixelIcon(String id, float x, float y, int color) {
        String norm = id != null ? id : "";
        switch (norm) {
            case "cit": {
                frame(x + 2f, y + 4f, 11f, 9f, 1f, color);
                rect(x + 5f, y + 2f, 5f, 2f, color);
                rect(x + 6f, y + 7f, 3f, 3f, color);
                break;
            }
            case "crosshair": {
                rect(x + 7f, y + 2f, 1f, 11f, color);
                rect(x + 2f, y + 7f, 11f, 1f, color);
                break;
            }
            default: {
                frame(x + 3f, y + 3f, 9f, 9f, 1f, color);
                rect(x + 6f, y + 6f, 3f, 3f, color);
                break;
            }
        }
    }

    public static void tooltip(String text, float mx, float my, int sw, int sh) {
        if (text == null || text.isEmpty()) return;
        float w = tw(text) + 12f;
        float h = 16f;
        float x = Math.min(mx + 8f, sw - w - 6f);
        float y = Math.max(6f, my - h - 6f);
        rrect(x, y, w, h, 4f, 0xEE0B0E14);
        rframe(x, y, w, h, 4f, 1f, 0x33FFFFFF);
        text(text, x + 6f, y + 4f, 0xFFF1F5F9);
    }

    public static void scissorPush(float x, float y, float w, float h, int winH, double scaleFactor) {
        int sx = Math.max(0, (int) Math.floor(x * scaleFactor));
        int sw = Math.max(0, (int) Math.ceil(w * scaleFactor));
        int sh = Math.max(0, (int) Math.ceil(h * scaleFactor));
        int sy = Math.max(0, winH - (int) Math.ceil((y + h) * scaleFactor));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    public static void scissorPop() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
