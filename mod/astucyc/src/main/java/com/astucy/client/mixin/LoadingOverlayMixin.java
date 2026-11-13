package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.AstucyTextures;
import com.astucy.client.ui.Skin;
import com.astucy.client.ui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tela de abertura (carregando recursos) do Astucy:
 * atmosfera obsidiana com partículas e aura esmeralda, logo oficial do app
 * girando com anel energético e viajando suavemente até o topo ao concluir
 * o carregamento para encaixar na TitleScreen.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void astucy$loadingBrand(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        int gw = g.guiWidth();
        int gh = g.guiHeight();
        long now = System.currentTimeMillis();

        float fadeOutT = 0f;
        float fade = 1f;
        if (fadeOutStart > 0L) {
            fadeOutT = Math.min(1f, (now - fadeOutStart) / 900f);
            fade = 1f - fadeOutT * fadeOutT;
        }
        if (fade <= 0.02f) {
            return;
        }

        int cx = gw / 2;
        int cy = gh / 2;

        // Fundo obsidiana + auras esmeralda/ciano respirando
        Ui.rect(g, 0, 0, gw, gh, alpha(0xFF05070B, fade));
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 700.0);
        Skin.glow(g, cx, cy - 28, Math.max(130, gw / 3), alpha(((int) (0x28 * pulse) << 24) | 0x22C55E, fade));
        Skin.glow(g, cx + 44, cy + 16, Math.max(100, gw / 4), alpha(((int) (0x16 * pulse) << 24) | 0x0EA5E9, fade));
        Skin.noise(g, 0, 0, gw, gh);

        // Partículas flutuantes sutis subindo no fundo
        for (int i = 0; i < 14; i++) {
            float seed = i * 73.1f;
            int px = Math.round(((seed * 37f) % Math.max(1, gw)));
            float speed = 14f + (i % 5) * 5f;
            int py = gh - Math.round(((now / speed + seed * 19f) % (gh + 20)));
            int pa = (int) ((18 + (i % 4) * 10) * fade);
            int pcol = (i % 3 == 0) ? 0x6EE7B7 : 0x22C55E;
            Skin.disc(g, px, py, (i % 3 == 0) ? 3 : 2, (pa << 24) | pcol);
        }

        // Logo oficial do app girando no centro e viajando para o topo durante o fade-out
        float startX = cx;
        float startY = cy - 58f;
        float destX = cx - 64f;
        float destY = Math.round(gh * 0.20f) + 14f;
        float spin = (now % 60000L) / 420f;
        Skin.logoTravel(
                g,
                startX, startY,
                destX, destY,
                fadeOutT,
                40f, 26f,
                spin,
                alpha(0xFFFFFFFF, fade),
                alpha(0xFF22C55E, fade)
        );

        Font f = AstucyFonts.label();
        int my = cy - 22;
        int lh;
        int lw = AstucyTextures.fitWidth(g);
        if (lw > 0) {
            lh = AstucyTextures.heightFor(lw);
            AstucyTextures.draw(g, cx, my, lw, alpha(0xFFFFFFFF, fade));
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
                g.text(display, ch, mx, my, alpha(col, fade), false);
                mx += display.width(ch) + 3;
            }
            lh = 34;
        }

        String sub = "C L I E N T   0 . 1";
        int sw = f.width(sub);
        g.text(f, sub, cx - sw / 2, my + lh + 6, alpha(0x9994A3B8, fade), false);

        // Barra de progresso arredondada com gradiente esmeralda + ponta luminosa
        int bw = Math.min(240, gw - 80);
        int bx = cx - bw / 2;
        int by = cy + 34;
        Ui.rrect(g, bx, by, bw, 4, alpha(0x26FFFFFF, fade), 2);
        int fill = Math.max(0, Math.min(bw, Math.round(bw * currentProgress)));
        if (fill > 0) {
            if (!Skin.boxGrad(g, bx, by, Math.max(4, fill), 4, alpha(0xFF34D399, fade), alpha(0xFF22C55E, fade), 2)) {
                Ui.rrect(g, bx, by, fill, 4, alpha(0xFF22C55E, fade), 2);
            }
            Skin.glow(g, bx + fill, by + 2, 10, alpha(0x6634D399, fade));
        }
        String pct = Math.round(currentProgress * 100f) + "%";
        g.text(f, pct, cx - f.width(pct) / 2, by + 10, alpha(0x8894A3B8, fade), false);

        String foot = "ASTUCY ENGINE";
        g.text(AstucyFonts.ui8(), foot, 12, gh - 14, alpha(0x4D64748B, fade), false);
    }

    private static int alpha(int argb, float fade) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, fade)));
        return (Math.min(a, 0xFF) << 24) | (argb & 0xFFFFFF);
    }
}
