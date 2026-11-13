package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.AstucyTextures;
import com.astucy.client.ui.Skin;
import com.astucy.client.ui.Ui;
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
 * Carregamento de MUNDO (LevelLoadingScreen):
 * mesma identidade do Astucy com logo oficial girando, partículas orbitais e
 * barra de progresso esmeralda.
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

        Ui.rect(g, 0, 0, gw, gh, 0xFF05070B);
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 650.0);
        Skin.glow(g, cx, cy - 28, Math.max(130, gw / 3), ((int) (0x26 * pulse) << 24) | 0x22C55E);
        Skin.noise(g, 0, 0, gw, gh);

        // Logo oficial girando acima do wordmark
        float spin = (now % 60000L) / 380f;
        Skin.logoTravel(g, cx, cy - 58f, cx, cy - 58f, 1f, 38f, 38f, spin, 0xFFFFFFFF, 0xFF22C55E);

        Font f = AstucyFonts.label();

        int lw = AstucyTextures.fitWidth(g);
        int my = cy - 22;
        int lh;
        if (lw > 0) {
            lh = AstucyTextures.heightFor(lw);
            AstucyTextures.draw(g, cx, my, lw, 0xFFFFFFFF);
        } else {
            var display = AstucyFonts.display();
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

        String sub = "L O A D I N G   W O R L D";
        int sw = f.width(sub);
        g.text(f, sub, cx - sw / 2, my + lh + 6, 0x9994A3B8, false);

        float prog = Mth.clamp(smoothedProgress, 0f, 1f);
        int bw = Math.min(240, gw - 80);
        int bx = cx - bw / 2;
        int by = cy + 34;
        Ui.rrect(g, bx, by, bw, 4, 0x26FFFFFF, 2);
        if (prog <= 0.001f) {
            int pw = 52;
            int peak = (int) (now / 8L) % (bw + pw);
            int x0 = Math.max(bx, bx + peak - pw);
            int x1 = Math.min(bx + bw, bx + peak);
            if (x1 > x0) {
                Ui.rrect(g, x0, by, x1 - x0, 4, 0xFF22C55E, 2);
                Skin.glow(g, x1, by + 2, 10, 0x6634D399);
            }
        } else {
            int fill = Math.max(0, Math.min(bw, Math.round(bw * prog)));
            if (fill > 0) {
                if (!Skin.boxGrad(g, bx, by, Math.max(4, fill), 4, 0xFF34D399, 0xFF22C55E, 2)) {
                    Ui.rrect(g, bx, by, fill, 4, 0xFF22C55E, 2);
                }
                Skin.glow(g, bx + fill, by + 2, 10, 0x6634D399);
            }
        }
        String pct = Math.round(prog * 100f) + "%";
        g.text(f, pct, cx - f.width(pct) / 2, by + 10, 0x8894A3B8, false);

        String foot = "ASTUCY ENGINE";
        g.text(AstucyFonts.ui8(), foot, 12, gh - 14, 0x4D64748B, false);
    }
}
