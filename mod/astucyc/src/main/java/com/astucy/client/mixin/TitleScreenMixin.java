package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.gui.ClientMenuButton;
import com.astucy.client.ui.Ui;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tela de título do Astucy — enxuta:
 * panorama do jogo escurecido, wordmark em DM Sans (texto, sem PNG reduzido),
 * uma linha miúda de versão e os botões de vidro escuro com texto pequeno.
 * Nada de logo vanilla, splash amarelo, grade, partículas ou sweep.
 *
 * Desenhamos a tela inteira (panorama + scrim + wordmark + widgets) para não
 * herdar o rodapé/copyright do vanilla e para o scrim ficar na ordem certa.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    private static final String WORD = "ASTUCY";
    private static final int TRACKING = 0;
    private static long firstDraw = 0L;

    @Inject(method = "init", at = @At("TAIL"))
    private void astucy$widgets(CallbackInfo ci) {
        firstDraw = System.currentTimeMillis();
        Screen self = (Screen) (Object) this;
        AbstractWidget target = null;
        int seen = 0;
        for (var child : self.children()) {
            if (child instanceof AbstractWidget w && w.getWidth() == 200 && w.getHeight() == 20) {
                seen++;
                if (seen == 2) {
                    target = w;
                    break;
                }
            }
        }
        if (target == null) {
            return;
        }
        int size = 20;
        int bx = target.getX() + target.getWidth() + 4;
        int by = target.getY();
        ((ScreenInvoker) this).astucy$addWidget(new ClientMenuButton(bx, by, size));
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void astucy$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        ci.cancel();

        ScreenInvoker inv = (ScreenInvoker) this;
        Font mark = AstucyFonts.wordmark();

        int gw = g.guiWidth();
        int gh = g.guiHeight();
        int cx = gw / 2;

        long now = System.currentTimeMillis();
        if (firstDraw == 0L) {
            firstDraw = now;
        }
        long since = now - firstDraw;
        float reveal = Math.min(1f, since / 380f);
        float eased = 1f - (1f - reveal) * (1f - reveal);

        // ---- fundo: preto chapado (sem panorama atrás) ----
        Ui.rect(g, 0, 0, gw, gh, 0xFF050506);

        // vinheta inferior discreta: os botões ganham contraste sem faixa visível
        int botBand = Math.min(230, gh / 3);
        for (int y = 0; y < botBand; y += 2) {
            float t = 1f - y / (float) botBand;
            Ui.rect(g, 0, gh - y - 2, gw, 2, Math.round(22f * t * t) << 24);
        }

        // ---- wordmark: texto em DM Sans Bold (nítido em qualquer escala) ----
        int total = 0;
        for (int i = 0; i < WORD.length(); i++) {
            total += mark.width(String.valueOf(WORD.charAt(i))) + TRACKING;
        }
        total -= TRACKING;

        int y = Math.round(gh * 0.20f) + Math.round((1f - eased) * 6f);
        int alpha = Math.round(255 * eased);

        // halo branco bem discreto atrás do logo (profundidade, não glow)
        int x = cx - total / 2;
        for (int i = 0; i < WORD.length(); i++) {
            String ch = String.valueOf(WORD.charAt(i));
            g.text(mark, ch, x, y, (alpha << 24) | 0xFFFFFF, false);
            x += mark.width(ch) + TRACKING;
        }

        // ---- widgets (botões + menu do client) ----
        for (Renderable r : inv.astucy$renderables()) {
            // o "Copyright Mojang AB..." do vanilla é um PlainTextButton no
            // canto inferior direito — não entra numa tela limpa
            if (r instanceof PlainTextButton) {
                continue;
            }
            r.extractRenderState(g, mouseX, mouseY, partial);
        }

        // ---- rodapé: cliente à esquerda, versão do jogo à direita ----
        Font small = AstucyFonts.ui8();
        int footA = Math.round(0xB4 * eased);
        g.text(small, "Astucy Client 0.1.0", 10, gh - 13, (footA << 24) | 0x6E7781, false);
        String tag = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric";
        g.text(small, tag, gw - small.width(tag) - 10, gh - 13, (footA << 24) | 0x4E565F, false);

        // fade de entrada curto
        int fadeA = Math.round((1f - eased) * 255f);
        if (fadeA > 0) {
            Ui.rect(g, 0, 0, gw, gh, fadeA << 24);
        }
    }
}
