package com.astucy.client.ui;

import com.mojang.blaze3d.platform.GLX;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Procedural SDF texture generator & renderer for MC 1.7.10 using BufferedImage + NativeImageBackedTexture.
 */
public final class Skin {
    private static final Map<String, Identifier> CACHE = new HashMap<String, Identifier>();
    private static int seq = 0;

    private Skin() {}

    public interface PixelShader {
        int shade(float x, float y, int w, int h);
    }

    public static Identifier dynamic(String key, int w, int h, PixelShader shader) {
        Identifier existing = CACHE.get(key);
        if (existing != null) return existing;

        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                img.setRGB(px, py, shader.shade(px + 0.5f, py + 0.5f, w, h));
            }
        }
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        Identifier id = tm.registerDynamicTexture("astucy_" + (++seq), tex);
        tm.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        CACHE.put(key, id);
        return id;
    }

    public static void clearDynamic(String key) {
        CACHE.remove(key);
    }

    public static Identifier box(int w, int h, final float r, final int argb) {
        final int iw = Math.max(2, w);
        final int ih = Math.max(2, h);
        final int rq = Math.round(r * 4f);
        String key = "box:" + iw + "x" + ih + ":" + rq + ":" + argb;
        return dynamic(key, iw, ih, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float d = sdBox(x - bw * 0.5f, y - bh * 0.5f, bw * 0.5f - 0.5f, bh * 0.5f - 0.5f, r);
                float cov = clamp(0.5f - d, 0f, 1f);
                return scaleAlpha(argb, cov);
            }
        });
    }

    public static Identifier boxGrad(int w, int h, final float r, final int topArgb, final int botArgb) {
        final int iw = Math.max(2, w);
        final int ih = Math.max(2, h);
        final int rq = Math.round(r * 4f);
        String key = "boxg:" + iw + "x" + ih + ":" + rq + ":" + topArgb + ":" + botArgb;
        return dynamic(key, iw, ih, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float d = sdBox(x - bw * 0.5f, y - bh * 0.5f, bw * 0.5f - 0.5f, bh * 0.5f - 0.5f, r);
                float cov = clamp(0.5f - d, 0f, 1f);
                float t = clamp(y / bh, 0f, 1f);
                int c = lerpColor(topArgb, botArgb, t);
                return scaleAlpha(c, cov);
            }
        });
    }

    public static Identifier outline(int w, int h, final float r, final float thick, final int argb) {
        final int iw = Math.max(2, w);
        final int ih = Math.max(2, h);
        String key = "out:" + iw + "x" + ih + ":" + Math.round(r * 4f) + ":" + Math.round(thick * 4f) + ":" + argb;
        return dynamic(key, iw, ih, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float d = sdBox(x - bw * 0.5f, y - bh * 0.5f, bw * 0.5f - 0.5f, bh * 0.5f - 0.5f, r);
                float outer = clamp(0.5f - d, 0f, 1f);
                float inner = clamp(0.5f - (d + thick), 0f, 1f);
                float cov = clamp(outer - inner, 0f, 1f);
                return scaleAlpha(argb, cov);
            }
        });
    }

    public static Identifier disc(int size, final int argb) {
        final int s = Math.max(4, size);
        String key = "disc:" + s + ":" + argb;
        return dynamic(key, s, s, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float cx = bw * 0.5f;
                float cy = bh * 0.5f;
                float d = (float) Math.hypot(x - cx, y - cy) - (Math.min(bw, bh) * 0.5f - 0.5f);
                float cov = clamp(0.5f - d, 0f, 1f);
                return scaleAlpha(argb, cov);
            }
        });
    }

    public static Identifier glow(int size, final int rgb) {
        final int s = Math.max(8, size);
        String key = "glow:" + s + ":" + (rgb & 0xFFFFFF);
        return dynamic(key, s, s, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float dx = (x - bw * 0.5f) / (bw * 0.5f);
                float dy = (y - bh * 0.5f) / (bh * 0.5f);
                float r = (float) Math.hypot(dx, dy);
                float t = clamp(1f - r, 0f, 1f);
                float a = t * t * 0.65f;
                return ((int) (a * 255f) << 24) | (rgb & 0xFFFFFF);
            }
        });
    }

    public static Identifier hueWheel(int size) {
        final int s = Math.max(32, size);
        String key = "huewheel:" + s;
        return dynamic(key, s, s, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float cx = bw * 0.5f;
                float cy = bh * 0.5f;
                float dx = x - cx;
                float dy = y - cy;
                float maxR = Math.min(bw, bh) * 0.5f - 1f;
                float dist = (float) Math.hypot(dx, dy);
                float cov = clamp(0.5f - (dist - maxR), 0f, 1f);
                if (cov <= 0f) return 0;
                float sat = clamp(dist / maxR, 0f, 1f);
                float hue = (float) ((Math.atan2(dy, dx) / (2.0 * Math.PI)) + 0.5);
                int rgb = java.awt.Color.HSBtoRGB(hue, sat, 1f) & 0xFFFFFF;
                return ((int) (cov * 255f) << 24) | rgb;
            }
        });
    }

    public static Identifier logoIris(int size, final int accent) {
        final int s = Math.max(16, size);
        String key = "iris:" + s + ":" + accent;
        return dynamic(key, s, s, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float cx = bw * 0.5f;
                float cy = bh * 0.5f;
                float rad = Math.min(bw, bh) * 0.44f;
                float dx = x - cx;
                float dy = y - cy;
                float d = (float) Math.hypot(dx, dy);
                float ring = Math.abs(d - rad) - rad * 0.17f;
                float covRing = clamp(0.5f - ring, 0f, 1f);

                float ang = (float) Math.atan2(dy, dx);
                float sector = (float) (((ang + Math.PI) / (Math.PI / 3.0)) % 1.0);
                float blade = d < rad * 0.84f && d > rad * 0.34f && sector < 0.16f ? 0.15f : 1f;
                covRing *= blade;

                float pupil = d - rad * 0.28f;
                float covPupil = clamp(0.5f - pupil, 0f, 1f);

                float cov = Math.max(covRing, covPupil);
                if (cov <= 0.01f) return 0;
                int c = covPupil > covRing ? 0xFFFFFFFF : accent;
                return scaleAlpha(c, cov);
            }
        });
    }

    public static Identifier iconImage(final String id, final int color) {
        final String normId = id != null ? id : "";
        String key = "icon:" + normId + ":" + color;
        return dynamic(key, 48, 48, new PixelShader() {
            @Override
            public int shade(float x, float y, int bw, int bh) {
                float d;
                switch (normId) {
                    case "cit": {
                        float outer = sdBox(x - 24f, y - 24.5f, 13f, 10.5f, 5f);
                        float ring = Math.abs(outer) - 1.3f;
                        float handle = sdBox(x - 24f, y - 12f, 7f, 3f, 3f);
                        float gem = Math.abs(x - 24f) + Math.abs(y - 24.5f) - 5.5f;
                        d = Math.min(ring, Math.min(handle, gem));
                        break;
                    }
                    case "armor":
                    case "armordur": {
                        float shield = sdBox(x - 24f, y - 24f, 11f, 13f, 6f);
                        d = Math.abs(shield) - 1.5f;
                        break;
                    }
                    case "crosshair": {
                        float h = sdBox(x - 24f, y - 24f, 11f, 1.5f, 1f);
                        float v = sdBox(x - 24f, y - 24f, 1.5f, 11f, 1f);
                        d = Math.min(h, v);
                        break;
                    }
                    case "efeitos": {
                        float flask = (float) Math.hypot(x - 24f, y - 27f) - 9f;
                        float neck = sdBox(x - 24f, y - 14f, 3.5f, 5f, 1.5f);
                        d = Math.min(Math.abs(flask) - 1.4f, neck);
                        break;
                    }
                    case "watermark": {
                        float rad = 12f;
                        float dist = (float) Math.hypot(x - 24f, y - 24f);
                        d = Math.min(Math.abs(dist - rad) - 1.6f, dist - 4f);
                        break;
                    }
                    default: {
                        float outer = sdBox(x - 24f, y - 24f, 12f, 12f, 4.5f);
                        float dot = (float) Math.hypot(x - 24f, y - 24f) - 4f;
                        d = Math.min(Math.abs(outer) - 1.5f, dot);
                        break;
                    }
                }
                float cov = clamp(0.5f - d, 0f, 1f);
                return scaleAlpha(color, cov);
            }
        });
    }

    public static void drawIcon(String id, float x, float y, float size, int color) {
        drawTex(iconImage(id, color), x, y, size, size, 0xFFFFFFFF);
    }

    public static void drawTex(Identifier id, float x, float y, float w, float h, int tint) {
        if (id == null || w <= 0 || h <= 0) return;
        float a = ((tint >>> 24) & 0xFF) / 255f;
        float r = ((tint >>> 16) & 0xFF) / 255f;
        float g = ((tint >>> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;

        MinecraftClient.getInstance().getTextureManager().bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GLX.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        Tessellator tess = Tessellator.INSTANCE;
        tess.begin();
        tess.vertex(x, y + h, 0.0, 0.0, 1.0);
        tess.vertex(x + w, y + h, 0.0, 1.0, 1.0);
        tess.vertex(x + w, y, 0.0, 1.0, 0.0);
        tess.vertex(x, y, 0.0, 0.0, 0.0);
        tess.end();

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    public static float sdBox(float px, float py, float bx, float by, float r) {
        float cr = Math.min(r, Math.min(bx, by));
        float qx = Math.abs(px) - (bx - cr);
        float qy = Math.abs(py) - (by - cr);
        float ox = Math.max(qx, 0f);
        float oy = Math.max(qy, 0f);
        return (float) Math.hypot(ox, oy) + Math.min(Math.max(qx, qy), 0f) - cr;
    }

    public static int scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp(factor, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = clamp(t, 0f, 1f);
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
