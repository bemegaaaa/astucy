package com.astucy.client.mixin;

import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carregamento de MUNDO (26.x moviu o overlay de mundo pra LevelLoadingScreen):
 * mesma marca da tela de abertura do client — fundo quase preto, wordmark,
 * barra de progresso do serviço de chunks. Desenha POR CIMA da tela vanilla;
 * a lógica de progresso/fim continua sendo a do jogo.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {

    @Shadow private float smoothedProgress;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void astucy$levelBrand(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        int gw = g.guiWidth();
        int gh = g.guiHeight();
        int cx = gw / 2;
        int cy = gh / 2;

        Ui.rect(g, 0, 0, gw, gh, 0xFC000000);

        Font f = com.astucy.client.AstucyFonts.label();

        // wordmark em alta (cai pra letra-a-letra se a textura ainda não chegou)
        int lw = com.astucy.client.ui.AstucyTextures.fitWidth(g);
        int my = cy - 70;
        int lh;
        if (lw > 0) {
            lh = com.astucy.client.ui.AstucyTextures.heightFor(lw);
            com.astucy.client.ui.AstucyTextures.draw(g, cx, my, lw, 0xFFFFFFFF);
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
                int col = Ui.mix(0xFFFFFFFF, 0xFFA8E8C6, i / (float) (mark.length() - 1));
                g.text(display, ch, mx, my, col, false);
                mx += display.width(ch) + 3;
            }
            lh = 34;
        }

        String sub = "C A R R E G A N D O   M U N D O";
        int sw = f.width(sub);
        g.text(f, sub, cx - sw / 2, my + lh + 8, 0x8C99A2AD, false);

        // barra: progresso suavizado do vanilla (0 = indeterminado, com pulso)
        float prog = Mth.clamp(smoothedProgress, 0f, 1f);
        int bw = Math.min(260, gw - 80);
        int bx = cx - bw / 2;
        int by = cy + 8;
        Ui.rect(g, bx, by, bw, 2, 0x33FFFFFF);
        int fill;
        if (prog <= 0.001f) {
            // indeterminado: pulso deslizante
            int pw = 46;
            int peak = (int) (now / 9L) % (bw + pw);
            fill = 0;
            int x0 = Math.max(bx, peak - pw);
            int x1 = Math.min(bx + bw, peak);
            if (x1 > x0) {
                Ui.rect(g, x0, by, x1 - x0, 2, 0xFFFFFFFF);
            }
        } else {
            fill = Math.max(0, Math.min(bw, Math.round(bw * prog)));
            if (fill > 0) {
                Ui.rect(g, bx, by, fill, 2, 0xFFFFFFFF);
            }
        }
        String pct = Math.round(prog * 100f) + "%";
        g.text(f, pct, cx - f.width(pct) / 2, by + 8, 0x66A9B2BC, false);

        String foot = "astucy client";
        g.text(f, foot, 10, gh - 14, 0x4D9AA2AD, false);
    }
}
