package com.astucy.client.mixin;

import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tela de abertura (carregando recursos/mundo) com a cara do Astucy:
 * fundo quase preto, wordmark e barra de progresso. Desenha POR CIMA da
 * tela vanilla — a lógica de progresso e fade continua sendo a do jogo.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void astucy$loadingBrand(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int gw = g.guiWidth();
        int gh = g.guiHeight();
        long now = System.currentTimeMillis();

        // sem fade-in: o logo da Mojang não aparece nem por um frame
        float fade = 1f;
        if (fadeOutStart > 0L) {
            fade = 1f - Math.min(1f, (now - fadeOutStart) / 900f);
        }
        if (fade <= 0.02f) {
            return;
        }

        int bgAlpha = Math.round(0xFC * fade);
        Ui.rect(g, 0, 0, gw, gh, bgAlpha << 24);

        Font f = com.astucy.client.AstucyFonts.label();
        int cx = gw / 2;
        int cy = gh / 2;

        // wordmark: textura em alta (borda lisa) — cai pra fonte display se faltar
        int lw = com.astucy.client.ui.AstucyTextures.fitWidth(g);
        int my = cy - 70;
        int lh;
        if (lw > 0) {
            lh = com.astucy.client.ui.AstucyTextures.heightFor(lw);
            com.astucy.client.ui.AstucyTextures.draw(g, cx, my, lw, alpha(0xFFFFFFFF, fade));
        } else {
            var display = com.astucy.client.AstucyFonts.display();
            String mark = "ASTUCY";
            int total = 0;
            for (int i = 0; i < mark.length(); i++) {
                total += display.width(String.valueOf(mark.charAt(i))) + 3;
            }
            total -= 3;
            int mx = cx - total / 2;
            for (int i = 0; i < mark.length(); i++) {
                String ch = String.valueOf(mark.charAt(i));
                int col = com.astucy.client.ui.Ui.mix(0xFFFFFFFF, 0xFFA8E8C6, i / (float) (mark.length() - 1));
                g.text(display, ch, mx, my, alpha(col, fade), false);
                mx += display.width(ch) + 3;
            }
            lh = 34;
        }

        String sub = "C L I E N T   " + version();
        int sw = f.width(sub);
        g.text(f, sub, cx - sw / 2, my + lh + 8, alpha(0x8C99A2AD, fade), false);

        // barra de progresso fina
        int bw = Math.min(260, gw - 80);
        int bx = cx - bw / 2;
        int by = cy + 8;
        Ui.rect(g, bx, by, bw, 2, alpha(0x33FFFFFF, fade));
        int fill = Math.max(0, Math.min(bw, Math.round(bw * currentProgress)));
        if (fill > 0) {
            Ui.rect(g, bx, by, fill, 2, alpha(0xFFFFFFFF, fade));
        }
        String pct = Math.round(currentProgress * 100f) + "%";
        g.text(f, pct, cx - f.width(pct) / 2, by + 8, alpha(0x66A9B2BC, fade), false);

        // rodapé discreto
        String foot = "astucy client";
        g.text(f, foot, 10, gh - 14, alpha(0x4D9AA2AD, fade), false);
    }

    private static int alpha(int argb, float fade) {
        int a = Math.round(((argb >>> 24) & 0xFF) * fade);
        return (Math.min(a, 0xFF) << 24) | (argb & 0xFFFFFF);
    }

    private static String version() {
        return "0.1";
    }
}
