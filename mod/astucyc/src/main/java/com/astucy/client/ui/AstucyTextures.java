package com.astucy.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Texturas próprias do Astucy carregadas na mão.
 *
 * O wordmark é um PNG em alta (gradiente + glow já assados). Desenhar textura
 * em vez de fonte garante borda lisa em qualquer GUI scale — e o upload()
 * explícito é obrigatório: registrar sem subir pra GPU desenha vazio.
 */
public final class AstucyTextures {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("astucy", "textures/gui/logo.png");
    private static boolean tried;
    private static int tries;
    private static int logoW;
    private static int logoH;

    private AstucyTextures() {}

    public static Identifier logo() {
        ensure();
        return LOGO;
    }

    /** true se a textura existe e já subiu pra GPU. */
    public static boolean ready() {
        ensure();
        return logoW > 0 && Minecraft.getInstance().getTextureManager().getTexture(LOGO) != null;
    }

    /**
     * Largura ideal em unidades de GUI: 1 texel = 1 pixel de tela.
     * Assim a textura é desenhada em 1:1 e fica nítida em qualquer GUI scale
     * (com NEAREST ou LINEAR, tanto faz).
     */
    public static int fitWidth(GuiGraphicsExtractor g) {
        if (!ready()) {
            return 0;
        }
        double scale = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        int w = (int) Math.round(logoW / scale);
        return Math.max(120, Math.min(w, g.guiWidth() - 40));
    }

    public static int heightFor(int width) {
        return Math.max(1, Math.round(width * (logoH / (float) logoW)));
    }

    public static int logoW() {
        return logoW;
    }

    public static int logoH() {
        return logoH;
    }

    /**
     * Desenha o wordmark centralizado em {@code cx}, topo em {@code y}, com a
     * largura pedida. {@code color} aplica alfa/tinta (0xFFFFFFFF = original).
     */
    public static boolean draw(GuiGraphicsExtractor g, int cx, int y, int width, int color) {
        if (!ready()) {
            return false;
        }
        int w = width;
        int h = Math.max(1, Math.round(width * (logoH / (float) logoW)));
        int x = cx - w / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, w, h, logoW, logoH, logoW, logoH, color);
        return true;
    }

    /** DynamicTexture com filtro linear (o padrão é NEAREST e serrilha). */
    private static final class LinearTexture extends DynamicTexture {
        LinearTexture(NativeImage img) {
            super(() -> "astucy-logo", img);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private static void ensure() {
        // tenta de novo algumas vezes: se a primeira chamada cair antes do
        // resource manager ficar pronto, o logo carrega na próxima (antes
        // marcava "tentado" e ficava sem logo a sessão inteira)
        if (tried || tries > 80) {
            return;
        }
        tries++;
        try {
            Minecraft mc = Minecraft.getInstance();
            var found = mc.getResourceManager().getResource(LOGO);
            if (found.isEmpty()) {
                return;
            }
            try (var in = found.get().open()) {
                NativeImage img = NativeImage.read(in);
                logoW = img.getWidth();
                logoH = img.getHeight();
                // DynamicTexture nasce com sampler NEAREST (serrilha ao reduzir):
                // trocamos por LINEAR pra textura ficar lisa em qualquer escala.
                DynamicTexture tex = new LinearTexture(img);
                mc.getTextureManager().register(LOGO, tex);
                tex.upload();
                tried = true;
            }
        } catch (Throwable ignored) {
            logoW = 0;
            logoH = 0;
        }
    }
}
