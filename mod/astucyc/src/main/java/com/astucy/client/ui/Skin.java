package com.astucy.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * RenderizaÃ§Ã£o HD do Astucy: caixas, pills e Ã­cones anti-alias gerados como
 * textura (SDF + supersampling 4x, alfa premultiplicado) e desenhados por
 * blit â€” nada de pixel art. Cada (forma, cor) vira textura cacheada.
 */
public final class Skin {

    private static final int BOX_TEX = 32;
    private static final int BOX_R = 10;
    private static final int ICON_TEX = 48;

    private static boolean broken = false;
    private static long brokenUntil = 0L;
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private Skin() {}

    public static boolean ok() {
        return !broken;
    }

    /* ------------------------------------------------------------------
     * desenho
     * ------------------------------------------------------------------ */

    /** Caixa arredondada suave (9-slice). false = sem textura, use fill. */
    public static boolean box(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        return box(g, x, y, w, h, color, BOX_R);
    }

    /** Mesma caixa, mas com raio de canto explícito (ex.: botões do menu = 4). */
    public static boolean box(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, int radius) {
        if (broken || w <= 0 || h <= 0) {
            return false;
        }
        color = quant(color);
        int col = color;
        int s = texScale();
        try {
            Identifier id = texture("box:" + s, col, () -> boxImage(col, s));
            int tex = BOX_TEX * s;
            int rc = BOX_R * s;
            int mid = tex - 2 * rc;
            int c = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
            int mw = w - 2 * c;
            int mh = h - 2 * c;
            blit(g, id, x, y, 0, 0, c, c, rc, rc, tex);
            blit(g, id, x + w - c, y, tex - rc, 0, c, c, rc, rc, tex);
            blit(g, id, x, y + h - c, 0, tex - rc, c, c, rc, rc, tex);
            blit(g, id, x + w - c, y + h - c, tex - rc, tex - rc, c, c, rc, rc, tex);
            if (mw > 0) {
                blit(g, id, x + c, y, rc, 0, mw, c, mid, rc, tex);
                blit(g, id, x + c, y + h - c, rc, tex - rc, mw, c, mid, rc, tex);
            }
            if (mh > 0) {
                blit(g, id, x, y + c, 0, rc, c, mh, rc, mid, tex);
                blit(g, id, x + w - c, y + c, tex - rc, rc, c, mh, rc, mid, tex);
            }
            if (mw > 0 && mh > 0) {
                blit(g, id, x + c, y + c, rc, rc, mw, mh, mid, mid, tex);
            }
            return true;
        } catch (Throwable t) {
            markBroken();
            return false;
        }
    }

    /** Ãcone HD esticado para sizeÃ—size px de GUI. false = sem textura. */
    public static boolean icon(GuiGraphicsExtractor g, String name, int x, int y, int size, int color) {
        if (broken && System.currentTimeMillis() < brokenUntil) {
            return false;
        }
        broken = false;
        if (size <= 0) {
            return false;
        }
        color = quant(color);
        int col = color;
        try {
            // resolução generosa (4x o tamanho) — nunca upscala, sempre nítido
            int px = Math.max(48, Math.min(256, size * 4));
            Identifier id = texture("icon:" + name + ":" + px, col, () -> iconImage(name, col, px));
            blit(g, id, x, y, 0, 0, size, size, px, px, px);
            return true;
        } catch (Throwable t) {
            markBroken();
            return false;
        }
    }

    /**
     * Uma falha de textura degrada os próprios ícones por 15s e depois se
     * auto-recupera (ex.: gerador de texturas ainda não pronto no boot).
     */
    private static void markBroken() {
        broken = true;
        brokenUntil = System.currentTimeMillis() + 15_000L;
    }

    private static float guiScale() {
        try {
            var w = Minecraft.getInstance().getWindow();
            int gw = w.getGuiScaledWidth();
            if (gw <= 0) {
                return 2f;
            }
            // escala REAL: pixels do framebuffer por unidade de GUI
            return Math.max(1f, (float) (w.getWidth() / (double) gw));
        } catch (Throwable t) {
            return 2f;
        }
    }

    /** Quantiza a cor (passos de 8) — não cria textura nova a cada frame de animação. */
    private static int quant(int c) {
        int a = Math.min(255, Math.round(((c >>> 24) & 0xFF) / 8f) * 8);
        int r = Math.min(255, Math.round(((c >>> 16) & 0xFF) / 8f) * 8);
        int g2 = Math.min(255, Math.round(((c >>> 8) & 0xFF) / 8f) * 8);
        int b = Math.min(255, Math.round((c & 0xFF) / 8f) * 8);
        return (a << 24) | (r << 16) | (g2 << 8) | b;
    }

    /** Escala inteira das texturas de forma (1..4) — cantos e bordas na resolução da tela. */
    private static int texScale() {
        return Math.max(1, Math.min(4, Math.round(guiScale())));
    }

    /** Caixa arredondada com degradÃª vertical (topâ†’bottom) â€” botÃµes e painÃ©is. */
    public static boolean boxGrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        if (broken || w <= 0 || h <= 0) {
            return false;
        }
        top = quant(top);
        bottom = quant(bottom);
        int t1 = top;
        int t2 = bottom;
        int s = texScale();
        try {
            Identifier id = texture("grad:" + s, (t1 << 1) ^ t2, () -> gradImage(t1, t2, s));
            int tex = BOX_TEX * s;
            int rc = BOX_R * s;
            int mid = tex - 2 * rc;
            int c = Math.max(1, Math.min(BOX_R, Math.min(w, h) / 2));
            int mw = w - 2 * c;
            int mh = h - 2 * c;
            blit(g, id, x, y, 0, 0, c, c, rc, rc, tex);
            blit(g, id, x + w - c, y, tex - rc, 0, c, c, rc, rc, tex);
            blit(g, id, x, y + h - c, 0, tex - rc, c, c, rc, rc, tex);
            blit(g, id, x + w - c, y + h - c, tex - rc, tex - rc, c, c, rc, rc, tex);
            if (mw > 0) {
                blit(g, id, x + c, y, rc, 0, mw, c, mid, rc, tex);
                blit(g, id, x + c, y + h - c, rc, tex - rc, mw, c, mid, rc, tex);
            }
            if (mh > 0) {
                blit(g, id, x, y + c, 0, rc, c, mh, rc, mid, tex);
                blit(g, id, x + w - c, y + c, tex - rc, rc, c, mh, rc, mid, tex);
            }
            if (mw > 0 && mh > 0) {
                blit(g, id, x + c, y + c, rc, rc, mw, mh, mid, mid, tex);
            }
            return true;
        } catch (Throwable t) {
            markBroken();
            return false;
        }
    }

    /** Disco suave (knobs, pupila, pontos). */
    public static boolean disc(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        if (broken || size <= 0) {
            return false;
        }
        color = quant(color);
        int col = color;
        try {
            int s = texScale();
            int px = Math.max(8, Math.min(256, size * s));
            Identifier id = texture("disc:" + px, col, () -> {
                NativeImage img = new NativeImage(px, px, true);
                float ctr = px / 2f;
                float rr = px / 2f - Math.max(0.6f, px * 0.02f);
                paint(img, col, (qx, qy) -> sdDisc(qx, qy, ctr, ctr, rr));
                return img;
            });
            blit(g, id, x, y, 0, 0, size, size, px, px, px);
            return true;
        } catch (Throwable t) {
            markBroken();
            return false;
        }
    }

    /** Contorno anti-alias 1px (anel suave — nada de degrau de pixel). */
    public static boolean outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (broken || w < 6 || h < 6) {
            return false;
        }
        color = quant(color);
        int col = color;
        int s = texScale();
        try {
            Identifier id = texture("ring:" + s, col, () -> {
                int tex = BOX_TEX * s;
                NativeImage img = new NativeImage(tex, tex, true);
                float half = tex / 2f;
                paint(img, col, (px, py) -> Math.abs(sdBox(px, py, half, half, 15f * s, 15f * s, 9f * s)) - 0.9f * s);
                return img;
            });
            int tex = BOX_TEX * s;
            int rc = BOX_R * s;
            int mid = tex - 2 * rc;
            int c = Math.max(1, Math.min(BOX_R, Math.min(w, h) / 2));
            int mw = w - 2 * c;
            int mh = h - 2 * c;
            blit(g, id, x, y, 0, 0, c, c, rc, rc, tex);
            blit(g, id, x + w - c, y, tex - rc, 0, c, c, rc, rc, tex);
            blit(g, id, x, y + h - c, 0, tex - rc, c, c, rc, rc, tex);
            blit(g, id, x + w - c, y + h - c, tex - rc, tex - rc, c, c, rc, rc, tex);
            if (mw > 0) {
                blit(g, id, x + c, y, rc, 0, mw, c, mid, rc, tex);
                blit(g, id, x + c, y + h - c, rc, tex - rc, mw, c, mid, rc, tex);
            }
            if (mh > 0) {
                blit(g, id, x, y + c, 0, rc, c, mh, rc, mid, tex);
                blit(g, id, x + w - c, y + c, tex - rc, rc, c, mh, rc, mid, tex);
            }
            return true;
        } catch (Throwable t) {
            markBroken();
            return false;
        }
    }

    private static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y,
                             float u, float v, int w, int h, int rw, int rh, int tex) {
        g.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, id, x, y, u, v, w, h, rw, rh, tex, tex);
    }

    /* ------------------------------------------------------------------
     * texturas
     * ------------------------------------------------------------------ */

    /** Grão sutil de vidro (tiles de 96px, alfa já embutido na textura). */
    private static void noise(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (broken || w <= 0 || h <= 0) {
            return;
        }
        try {
            Identifier id = grainTex();
            int t = 96;
            for (int ty = 0; ty < h; ty += t) {
                int ch = Math.min(t, h - ty);
                for (int tx = 0; tx < w; tx += t) {
                    int cw = Math.min(t, w - tx);
                    blit(g, id, x + tx, y + ty, 0, 0, cw, ch, cw, ch, t);
                }
            }
        } catch (Throwable t) {
            markBroken();
        }
    }

    private static Identifier grainTex() {
        return texture("grain", 0, () -> {
            int s = 96;
            NativeImage img = new NativeImage(s, s, true);
            java.util.Random rnd = new java.util.Random(1337L);
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    // alfa premultiplicado: RGB nunca passa do alfa
                    int a = 5 + rnd.nextInt(7);
                    img.setPixel(x, y, (a << 24) | (a << 16) | (a << 8) | a);
                }
            }
            return img;
        });
    }

    /* ------------------------------------------------------------------
     * cor: roda de matiz + barra de brilho (estilo seletor do sistema)
     * ------------------------------------------------------------------ */

    private static float[] astucyHsv(float h, float s, float v) {
        float f = h * 6f;
        int i = (int) f;
        f -= i;
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = v; g = t; b = v * (1f - s); }
            case 1 -> { r = q; g = v; b = v * (1f - s); }
            case 2 -> { r = v * (1f - s); g = v; b = t; }
            case 3 -> { r = v * (1f - s); g = q; b = v; }
            case 4 -> { r = t; g = v * (1f - s); b = v; }
            default -> { r = v; g = v * (1f - s); b = q; }
        }
        return new float[]{r, g, b};
    }

    /** Roda de cores: matiz pelo ângulo, saturação pelo raio, centro claro. */
    public static boolean hueWheel(GuiGraphicsExtractor g, int x, int y, int size) {
        if (broken || size <= 0) {
            return false;
        }
        int s = texScale();
        int tex = Math.max(48, Math.min(128, size * s));
        try {
            Identifier id = texture("huewheel:" + tex, 0, () -> {
                NativeImage img = new NativeImage(tex, tex, true);
                float c = tex / 2f;
                for (int py = 0; py < tex; py++) {
                    for (int qx = 0; qx < tex; qx++) {
                        float dx = (qx - c + 0.5f) / c;
                        float dy = (py - c + 0.5f) / c;
                        float rr = (float) Math.sqrt(dx * dx + dy * dy);
                        if (rr > 1.0f) {
                            continue;
                        }
                        float h = (float) (Math.atan2(dy, dx) / (Math.PI * 2));
                        if (h < 0) {
                            h += 1f;
                        }
                        float sat = Math.min(1f, Math.max(0f, (rr - 0.10f) / 0.90f));
                        float a = Math.max(0f, Math.min(1f, (1.04f - rr) * 10f));
                        float[] rgb = astucyHsv(h, sat, 1f);
                        int al = (int) (a * 255);
                        int rr8 = (int) (rgb[0] * a * 255) & 0xFF;
                        int gg8 = (int) (rgb[1] * a * 255) & 0xFF;
                        int bb8 = (int) (rgb[2] * a * 255) & 0xFF;
                        img.setPixel(qx, py, (al << 24) | (rr8 << 16) | (gg8 << 8) | bb8);
                    }
                }
                return img;
            });
            blit(g, id, x, y, 0, 0, size, size, tex, tex, tex);
            return true;
        } catch (Throwable bad) {
            markBroken();
            return false;
        }
    }

    /** Barra de brilho: preto à esquerda, cor cheia no matiz escolhido à direita. */
    public static boolean hueValueBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float hue, float sat) {
        if (broken || w <= 0 || h <= 0) {
            return false;
        }
        int s = texScale();
        int tex = Math.max(16, Math.min(256, w * s));
        int texH = Math.max(2, Math.min(32, h * s));
        int key = (((int) (hue * 255) & 0xFF) << 8) | ((int) (sat * 255) & 0xFF);
        try {
            Identifier id = texture("valbar:" + tex, key, () -> {
                NativeImage img = new NativeImage(tex, texH, true);
                for (int qx = 0; qx < tex; qx++) {
                    float v = qx / (float) (tex - 1);
                    float[] rgb = astucyHsv(hue, sat, v);
                    int rr8 = (int) (rgb[0] * 255) & 0xFF;
                    int gg8 = (int) (rgb[1] * 255) & 0xFF;
                    int bb8 = (int) (rgb[2] * 255) & 0xFF;
                    int px = (255 << 24) | (rr8 << 16) | (gg8 << 8) | bb8;
                    for (int ty = 0; ty < texH; ty++) {
                        img.setPixel(qx, ty, px);
                    }
                }
                return img;
            });
            blit(g, id, x, y, 0, 0, w, h, tex, texH, tex);
            return true;
        } catch (Throwable bad) {
            markBroken();
            return false;
        }
    }

    private static Identifier texture(String key, int color, Supplier<NativeImage> painter) {
        String k = key + "#" + Integer.toHexString(color);
        Identifier id = CACHE.get(k);
        if (id != null) {
            return id;
        }
        Identifier nid = Identifier.fromNamespaceAndPath("astucy", "skin/" + Integer.toHexString(k.hashCode()));
        DynamicTexture tex = new AstucyTexture(painter.get());
        tex.upload();
        Minecraft.getInstance().getTextureManager().register(nid, tex);
        CACHE.put(k, nid);
        return nid;
    }

    /** Textura com filtro LINEAR — bordas suaves em qualquer escala (sem serrilhado). */
    private static final class AstucyTexture extends DynamicTexture {
        AstucyTexture(NativeImage img) {
            super(() -> "astucy-skin", img);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private static NativeImage boxImage(int color, int s) {
        int tex = BOX_TEX * s;
        NativeImage img = new NativeImage(tex, tex, true);
        float half = tex / 2f;
        paint(img, color, (px, py) -> sdBox(px, py, half, half, 15f * s, 15f * s, 9f * s));
        return img;
    }

    private static NativeImage gradImage(int top, int bottom, int s) {
        int tex = BOX_TEX * s;
        NativeImage img = new NativeImage(tex, tex, true);
        float half = tex / 2f;
        paintGrad(img, top, bottom, (px, py) -> sdBox(px, py, half, half, 15f * s, 15f * s, 9f * s));
        return img;
    }

    /* ------------------------------------------------------------------
     * rasterizador SDF (anti-alias por supersampling 4x, alfa premultiplicado)
     * ------------------------------------------------------------------ */

    @FunctionalInterface
    private interface Sd {
        float at(float px, float py);
    }

    private static void paint(NativeImage img, int color, Sd sd) {
        paintGrad(img, color, color, sd);
    }

    /** Pinta com degradÃª vertical topâ†’bottom (alfa premultiplicado, AA 4x). */
    private static void paintGrad(NativeImage img, int top, int bottom, Sd sd) {
        int size = img.getWidth();
        int ta = (top >>> 24) & 0xFF, tr = (top >>> 16) & 0xFF, tg = (top >>> 8) & 0xFF, tb = top & 0xFF;
        int ba = (bottom >>> 24) & 0xFF, br = (bottom >>> 16) & 0xFF, bg = (bottom >>> 8) & 0xFF, bb = bottom & 0xFF;
        // texturas grandes (ícones 136px) já têm AA de 1 texel: 2x2 basta e custa 4x menos
        int n = size >= 128 ? 2 : 4;
        float invN = 1f / (n * n);
        for (int y = 0; y < size; y++) {
            float t = y / (float) Math.max(1, size - 1);
            int a = Math.round(ta + (ba - ta) * t);
            int r = Math.round(tr + (br - tr) * t);
            int g = Math.round(tg + (bg - tg) * t);
            int b = Math.round(tb + (bb - tb) * t);
            for (int x = 0; x < size; x++) {
                float cov = 0f;
                for (int sy = 0; sy < n; sy++) {
                    for (int sx = 0; sx < n; sx++) {
                        float d = sd.at(x + (sx + 0.5f) / n, y + (sy + 0.5f) / n);
                        cov += d <= 0f ? 1f : (d >= 1f ? 0f : 1f - d);
                    }
                }
                cov *= invN;
                if (cov <= 0f) {
                    continue;
                }
                int sa = Math.round(a * cov);
                if (sa <= 0) {
                    continue;
                }
                int sr = Math.round(r * sa / 255f);
                int sg = Math.round(g * sa / 255f);
                int sb = Math.round(b * sa / 255f);
                int dst = img.getPixel(x, y);
                int da = (dst >>> 24) & 0xFF;
                int dr = (dst >>> 16) & 0xFF;
                int dg = (dst >>> 8) & 0xFF;
                int db = dst & 0xFF;
                int inv = 255 - sa;
                int oa = sa + da * inv / 255;
                int or = sr + dr * inv / 255;
                int og = sg + dg * inv / 255;
                int ob = sb + db * inv / 255;
                img.setPixel(x, y, (Math.min(255, oa) << 24) | (Math.min(255, or) << 16)
                        | (Math.min(255, og) << 8) | Math.min(255, ob));
            }
        }
    }

    private static float sdBox(float px, float py, float cx, float cy, float hw, float hh, float r) {
        float dx = Math.abs(px - cx) - hw + r;
        float dy = Math.abs(py - cy) - hh + r;
        float ox = Math.max(dx, 0f);
        float oy = Math.max(dy, 0f);
        return Math.min(Math.max(dx, dy), 0f) + (float) Math.sqrt(ox * ox + oy * oy) - r;
    }

    private static float sdDisc(float px, float py, float cx, float cy, float r) {
        return (float) Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy)) - r;
    }

    private static float sdSeg(float px, float py, float x0, float y0, float x1, float y1, float t) {
        float vx = x1 - x0;
        float vy = y1 - y0;
        float wx = px - x0;
        float wy = py - y0;
        float c2 = vx * vx + vy * vy;
        float tt = c2 <= 0f ? 0f : Math.max(0f, Math.min(1f, (vx * wx + vy * wy) / c2));
        float dx = px - (x0 + tt * vx);
        float dy = py - (y0 + tt * vy);
        return (float) Math.sqrt(dx * dx + dy * dy) - t;
    }

    /* ------------------------------------------------------------------
     * primitivas de Ã­cone (canvas 48x48)
     * ------------------------------------------------------------------ */

    /** Pinta em coordenadas de design 48x48 escalando pro tamanho real
     *  (o AA fica em 1 texel — nítido em qualquer resolução). */
    private static void design(NativeImage img, int color, Sd sd) {
        int size = img.getWidth();
        float k = 48f / size;   // design = imagem * k
        float m = size / 48f;   // distancia em texels = design * m
        paint(img, color, (px, py) -> sd.at(px * k, py * k) * m);
    }

    private static void disc(NativeImage img, int color, float cx, float cy, float r) {
        design(img, color, (px, py) -> sdDisc(px, py, cx, cy, r));
    }

    private static void ring(NativeImage img, int color, float cx, float cy, float r, float t) {
        design(img, color, (px, py) -> Math.abs(sdDisc(px, py, cx, cy, r)) - t);
    }

    private static void capsule(NativeImage img, int color, float x, float y, float w, float h, float r) {
        design(img, color, (px, py) -> sdBox(px, py, x + w / 2f, y + h / 2f, w / 2f, h / 2f, r));
    }

    private static void ringBox(NativeImage img, int color, float x, float y, float w, float h, float r, float t) {
        design(img, color, (px, py) -> Math.abs(sdBox(px, py, x + w / 2f, y + h / 2f, w / 2f, h / 2f, r)) - t);
    }

    private static void line(NativeImage img, int color, float x0, float y0, float x1, float y1, float t) {
        design(img, color, (px, py) -> sdSeg(px, py, x0, y0, x1, y1, t));
    }

    /** Arco (anel cortado por ângulo) — wifi, medidores. */
    private static void arc(NativeImage img, int color, float cx, float cy, float r, float t, float a0, float a1) {
        design(img, color, (px, py) -> {
            float ang = (float) Math.atan2(py - cy, px - cx);
            if (ang < a0 || ang > a1) {
                return 1e6f;
            }
            return Math.abs(sdDisc(px, py, cx, cy, r)) - t;
        });
    }

    /* ------------------------------------------------------------------
     * conjunto de Ã­cones (limpo, sem pixel art)
     * ------------------------------------------------------------------ */

    private static NativeImage iconImage(String name, int color, int px) {
        NativeImage img = new NativeImage(px, px, true);
        switch (name) {
            case "fps" -> {
                capsule(img, color, 13, 27, 6, 11, 3);
                capsule(img, color, 21, 22, 6, 16, 3);
                capsule(img, color, 29, 17, 6, 21, 3);
            }
            case "ping" -> {
                disc(img, color, 24, 35, 2.2f);
                arc(img, color, 24, 35, 9.5f, 1.9f, -2.36f, -0.79f);
                arc(img, color, 24, 35, 17f, 1.9f, -2.36f, -0.79f);
            }
            case "server" -> {
                capsule(img, color, 10, 11, 28, 9, 4.5f);
                capsule(img, color, 10, 28, 28, 9, 4.5f);
                disc(img, color, 31, 15.5f, 2.2f);
                disc(img, color, 31, 32.5f, 2.2f);
            }
            case "memory" -> {
                ringBox(img, color, 14, 14, 20, 20, 5, 2.8f);
                line(img, color, 19, 34, 19, 39, 2.2f);
                line(img, color, 29, 34, 29, 39, 2.2f);
                line(img, color, 19, 9, 19, 14, 2.2f);
                line(img, color, 29, 9, 29, 14, 2.2f);
            }
            case "speed" -> {
                line(img, color, 10, 14, 21, 24, 2.8f);
                line(img, color, 21, 24, 10, 34, 2.8f);
                line(img, color, 26, 14, 37, 24, 2.8f);
                line(img, color, 37, 24, 26, 34, 2.8f);
            }
            case "coords", "reach" -> {
                line(img, color, 24, 9, 24, 39, 3);
                line(img, color, 9, 24, 39, 24, 3);
                disc(img, color, 24, 24, 5);
            }
            case "cps" -> {
                ringBox(img, color, 15, 9, 18, 30, 9, 2.8f);
                line(img, color, 24, 9, 24, 18, 2.4f);
            }
            case "keystrokes" -> {
                capsule(img, color, 10, 10, 11, 11, 4);
                capsule(img, color, 27, 10, 11, 11, 4);
                capsule(img, color, 10, 27, 11, 11, 4);
                capsule(img, color, 27, 27, 11, 11, 4);
            }
            case "totem" -> {
                disc(img, color, 24, 12, 4.2f);
                capsule(img, color, 18, 19, 12, 13, 5);
                line(img, color, 12, 22, 18, 26, 2.4f);
                line(img, color, 36, 22, 30, 26, 2.4f);
            }
            case "health" -> {
                // coração: dois discos + losango (união)
                design(img, color, (qx, qy) -> Math.min(Math.min(
                        sdDisc(qx, qy, 17f, 20f, 6.6f),
                        sdDisc(qx, qy, 31f, 20f, 6.6f)),
                        Math.abs(qx - 24) + Math.abs(qy - 28) - 11f));
            }
            case "hunger" -> {
                disc(img, color, 19f, 29f, 8.5f);
                line(img, color, 24f, 24f, 33f, 15f, 3.2f);
                disc(img, color, 34.5f, 13.5f, 3.6f);
            }
            case "combo" -> {
                line(img, color, 15f, 33f, 33f, 15f, 2.4f);
                line(img, color, 33f, 33f, 15f, 15f, 2.4f);
                line(img, color, 11f, 27f, 19f, 35f, 2.2f);
                line(img, color, 37f, 27f, 29f, 35f, 2.2f);
            }
            case "armor" -> {
                ringBox(img, color, 13, 11, 22, 19, 5, 2.8f);
                line(img, color, 13, 30, 24, 39, 2.6f);
                line(img, color, 35, 30, 24, 39, 2.6f);
            }
            case "hora" -> {
                ring(img, color, 24, 24, 15, 3);
                line(img, color, 24, 24, 24, 13, 2.4f);
                line(img, color, 24, 24, 32, 28, 2.4f);
            }
            case "bioma" -> {
                ring(img, color, 24, 19, 11, 4.5f);
                line(img, color, 24, 27, 24, 41, 3.6f);
            }
            case "direcao" -> {
                ring(img, color, 24, 24, 15, 2.6f);
                design(img, color, (qx, qy) -> Math.abs(qx - 24) + Math.abs(qy - 24) - 7f);
            }
            case "sessao" -> {
                ring(img, color, 24, 24, 14, 2.8f);
                line(img, color, 21, 17, 31, 24, 2.4f);
                line(img, color, 31, 24, 21, 31, 2.4f);
                line(img, color, 21, 31, 21, 17, 2.4f);
            }
            case "efeitos" -> {
                capsule(img, color, 21, 7, 6, 10, 2.5f);
                ringBox(img, color, 14, 16, 20, 24, 6, 2.6f);
                line(img, color, 15, 29, 33, 29, 2.2f);
            }
            case "fullbright" -> {
                disc(img, color, 24, 24, 10);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * 2 * i / 8;
                    float dx = (float) Math.cos(ang);
                    float dy = (float) Math.sin(ang);
                    line(img, color, 24 + dx * 14, 24 + dy * 14, 24 + dx * 19, 24 + dy * 19, 2.6f);
                }
            }
            case "zoom", "search" -> {
                ring(img, color, 21, 21, 11, 3.2f);
                line(img, color, 29, 29, 39, 39, 3.8f);
            }
            case "hurtcam" -> {
                ringBox(img, color, 10, 14, 28, 22, 6, 3);
                ring(img, color, 24, 25, 6, 2.6f);
                capsule(img, color, 15, 9, 7, 6, 3);
            }
            case "sprint" -> {
                line(img, color, 26, 15, 35, 24, 3.2f);
                line(img, color, 35, 24, 26, 33, 3.2f);
                line(img, color, 11, 18, 20, 18, 2.8f);
                line(img, color, 11, 24, 16, 24, 2.8f);
                line(img, color, 11, 30, 20, 30, 2.8f);
            }
            case "sneak" -> {
                line(img, color, 24, 9, 24, 25, 2.6f);
                line(img, color, 17, 19, 24, 26, 2.6f);
                line(img, color, 31, 19, 24, 26, 2.6f);
                line(img, color, 13, 36, 35, 36, 2.6f);
            }
            case "itens" -> {
                design(img, color, (qx, qy) -> Math.abs(Math.abs(qx - 24) + Math.abs(qy - 24) - 15f) - 2.6f);
                line(img, color, 9, 24, 39, 24, 2.2f);
            }
            case "pack" -> {
                ringBox(img, color, 11, 16, 26, 19, 4, 3);
                capsule(img, color, 16, 9, 16, 7, 3.5f);
            }
            case "options" -> {
                line(img, color, 10, 15, 38, 15, 2.6f);
                line(img, color, 10, 24, 38, 24, 2.6f);
                line(img, color, 10, 33, 38, 33, 2.6f);
                disc(img, color, 17, 15, 4.2f);
                disc(img, color, 30, 24, 4.2f);
                disc(img, color, 21, 33, 4.2f);
            }
            case "target" -> {
                // mira: anel + centro + 4 traços
                ring(img, color, 24, 24, 13, 2.6f);
                disc(img, color, 24, 24, 3.2f);
                line(img, color, 24, 5, 24, 10, 2.6f);
                line(img, color, 24, 38, 24, 43, 2.6f);
                line(img, color, 5, 24, 10, 24, 2.6f);
                line(img, color, 38, 24, 43, 24, 2.6f);
            }
            case "armordur" -> {
                // escudo com barras de durabilidade
                ringBox(img, color, 13, 9, 22, 17, 5, 2.6f);
                line(img, color, 13, 27, 24, 39, 2.6f);
                line(img, color, 35, 27, 24, 39, 2.6f);
                line(img, color, 19, 17, 19, 27, 2.2f);
                line(img, color, 24, 17, 24, 27, 2.2f);
                line(img, color, 29, 17, 29, 27, 2.2f);
            }
            case "daycounter" -> {
                // sol nascendo sobre o horizonte
                design(img, color, (qx, qy) -> Math.min(
                        sdDisc(qx, qy, 24f, 26f, 7.5f),
                        (qy > 26f ? 1e6f : Math.abs(qy - 34f) - 1.4f)));
                line(img, color, 7, 34, 17, 34, 2.6f);
                line(img, color, 31, 34, 41, 34, 2.6f);
                line(img, color, 24, 9, 24, 14, 2.4f);
                line(img, color, 11, 16, 14.5f, 19.5f, 2.4f);
                line(img, color, 37, 16, 33.5f, 19.5f, 2.4f);
            }
            case "itemcount" -> {
                // pilha de itens
                capsule(img, color, 11, 28, 26, 8, 4);
                capsule(img, color, 14, 18, 20, 8, 4);
                capsule(img, color, 17, 8, 14, 8, 4);
            }
            case "chat" -> {
                // balão de fala com 3 pontos
                ringBox(img, color, 9, 13, 30, 21, 6, 2.6f);
                disc(img, color, 18, 24, 2.2f);
                disc(img, color, 24, 24, 2.2f);
                disc(img, color, 30, 24, 2.2f);
                line(img, color, 16, 33, 12, 40, 2.4f);
            }
            case "healthtag" -> {
                // medidor de vida acima da cabeça
                disc(img, color, 24, 11, 4.4f);
                line(img, color, 24, 21, 24, 27, 3.2f);
                ringBox(img, color, 12, 30, 24, 10, 4, 2.6f);
                line(img, color, 17, 35, 24, 35, 2.2f);
            }
            case "watermark" -> {
                // chip da marca: pílula + núcleo
                capsule(img, color, 8, 17, 32, 14, 7);
                disc(img, color, 15, 24, 3.2f);
            }
            case "realtime" -> {
                // relógio digital
                ringBox(img, color, 9, 14, 30, 20, 5, 2.6f);
                line(img, color, 18, 19, 18, 29, 2.2f);
                line(img, color, 24, 19, 24, 29, 2.2f);
                line(img, color, 30, 19, 30, 29, 2.2f);
                line(img, color, 19, 37, 24, 37, 2.2f);
            }
            case "fpsgraph" -> {
                // sparkline com índice
                line(img, color, 8, 30, 17, 25, 2.8f);
                line(img, color, 17, 25, 26, 28, 2.8f);
                line(img, color, 26, 28, 34, 16, 2.8f);
                disc(img, color, 34, 16, 2.4f);
                line(img, color, 8, 37, 40, 37, 2.2f);
            }
            case "blur" -> {
                // rastro de movimento em 3 trilhas diagonais
                line(img, color, 10, 24, 26, 8, 2.6f);
                line(img, color, 18, 34, 34, 18, 2.6f);
                line(img, color, 26, 40, 40, 26, 2.6f);
            }
            case "viewmodel" -> {
                // mão/braço em primeira pessoa
                ringBox(img, color, 14, 20, 16, 18, 5, 2.8f);
                capsule(img, color, 15, 26, 5, 10, 2.5f);
                capsule(img, color, 21, 26, 5, 10, 2.5f);
                capsule(img, color, 27, 26, 4, 9, 2f);
                capsule(img, color, 18, 9, 9, 11, 3);
            }
            default -> {
                ringBox(img, color, 10, 10, 28, 28, 7, 3.2f);
                disc(img, color, 24, 24, 4);
            }
        }
        return img;
    }

    /** Reduz 2x com box filter (alfa premultiplicado) — reserva para usos futuros. */
    private static NativeImage downsample(NativeImage big) {
        int s = big.getWidth() / 2;
        NativeImage out = new NativeImage(s, s, true);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = big.getPixel(x * 2 + dx, y * 2 + dy);
                        a += (p >>> 24) & 0xFF;
                        r += (p >>> 16) & 0xFF;
                        g += (p >>> 8) & 0xFF;
                        b += p & 0xFF;
                    }
                }
                out.setPixel(x, y, ((a / 4) << 24) | ((r / 4) << 16) | ((g / 4) << 8) | (b / 4));
            }
        }
        return out;
    }
}
