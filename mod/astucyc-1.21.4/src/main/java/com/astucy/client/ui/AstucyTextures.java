package com.astucy.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Texturas próprias do Astucy carregadas na mão (MC 1.21.4).
 */
public final class AstucyTextures {

    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath("astucy", "textures/gui/logo.png");
    private static boolean tried;
    private static int tries;
    private static int logoW;
    private static int logoH;

    private AstucyTextures() {}

    public static ResourceLocation logo() {
        ensure();
        return LOGO;
    }

    /** true se a textura existe e já subiu pra GPU. */
    public static boolean ready() {
        ensure();
        return logoW > 0 && Minecraft.getInstance().getTextureManager().getTexture(LOGO) != null;
    }

    public static int fitWidth(GuiGraphics g) {
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
    public static boolean draw(GuiGraphics g, int cx, int y, int width, int color) {
        if (!ready()) {
            return false;
        }
        int w = width;
        int h = Math.max(1, Math.round(width * (logoH / (float) logoW)));
        int x = cx - w / 2;
        g.blit(RenderType::guiTextured, LOGO, x, y, 0f, 0f, w, h, logoW, logoH, logoW, logoH, color);
        return true;
    }

    private static void ensure() {
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
                DynamicTexture tex = new DynamicTexture(img);
                tex.setFilter(true, false);
                tex.setClamp(true);
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
