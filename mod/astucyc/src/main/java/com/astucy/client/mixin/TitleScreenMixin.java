package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.gui.ClientMenuButton;
import com.astucy.client.ui.Skin;
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
 * Tela de título do Astucy:
 * - Ao abrir, a logo oficial do app surge girando no centro com rastro de
 *   cometa esmeralda e viaja em arco até encaixar à esquerda do wordmark
 *   "ASTUCY", disparando um pulso luminoso e cascata nas letras.
 * - Depois de encaixada, a logo permanece girando suavemente no cabeçalho.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    private static final String WORD = "ASTUCY";
    private static final int TRACKING = 2;
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
        float reveal = Math.min(1f, since / 420f);
        float eased = 1f - (1f - reveal) * (1f - reveal);

        // Progresso do voo da logo (0..1 em 950ms)
        float travelRaw = Math.min(1f, since / 950f);

        // ---- fundo: obsidiana profunda + auras esmeralda/ciano + spotlight do cursor ----
        Ui.rect(g, 0, 0, gw, gh, 0xFF05070B);
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 1100.0);
        int y = Math.round(gh * 0.19f) + Math.round((1f - eased) * 8f);
        Skin.glow(g, cx - 24, y + 8, Math.max(150, gw / 3),
                (Math.round(0x24 * pulse * eased) << 24) | 0x22C55E);
        Skin.glow(g, cx + 48, y + 36, Math.max(120, gw / 4),
                (Math.round(0x16 * pulse * eased) << 24) | 0x0EA5E9);
        Skin.glow(g, mouseX, mouseY, 115, 0x1422C55E);
        Skin.noise(g, 0, 0, gw, gh);

        // Partículas atmosféricas sutis subindo no fundo
        for (int i = 0; i < 16; i++) {
            float seed = i * 61.7f;
            int px = Math.round((seed * 43f + (float) Math.sin(now / 1400.0 + i) * 8f) % Math.max(1, gw));
            if (px < 0) px += gw;
            float speed = 18f + (i % 5) * 6f;
            int py = gh - Math.round(((now / speed + seed * 23f) % (gh + 24)));
            int pa = Math.round((14 + (i % 4) * 9) * eased);
            int pcol = (i % 3 == 0) ? 0x6EE7B7 : 0x22C55E;
            Skin.disc(g, px, py, (i % 4 == 0) ? 3 : 2, (pa << 24) | pcol);
        }

        // Vinheta inferior discreta
        int botBand = Math.min(230, gh / 3);
        for (int vy = 0; vy < botBand; vy += 2) {
            float t = 1f - vy / (float) botBand;
            Ui.rect(g, 0, gh - vy - 2, gw, 2, Math.round(22f * t * t) << 24);
        }

        // ---- lockup: [Logo Oficial Girando] + ASTUCY ----
        int wordW = 0;
        for (int i = 0; i < WORD.length(); i++) {
            wordW += mark.width(String.valueOf(WORD.charAt(i))) + TRACKING;
        }
        wordW -= TRACKING;

        int logoSize = 26;
        int gap = 10;
        int lockupW = logoSize + gap + wordW;
        int lockupX = cx - lockupW / 2;
        float destLogoX = lockupX + logoSize / 2f;
        float destLogoY = y + mark.lineHeight / 2f - 1f;

        // Logo viaja do centro da tela (cx, gh * 0.48) girando até (destLogoX, destLogoY)
        float idleSpin = (now % 120000L) / 1400f;
        int logoAlpha = Math.round(255 * Math.min(1f, since / 220f));
        Skin.logoTravel(
                g,
                cx, gh * 0.48f,
                destLogoX, destLogoY,
                travelRaw,
                48f, logoSize,
                idleSpin,
                (logoAlpha << 24) | 0xFFFFFF,
                (logoAlpha << 24) | 0x22C55E
        );

        // Halo suave atrás do wordmark
        Skin.glow(g, cx + 10, y + mark.lineHeight / 2, lockupW / 2 + 28,
                (Math.round(0x22 * eased) << 24) | 0x22C55E);

        // Letras de ASTUCY entram em cascata conforme a logo se aproxima do encaixe
        int wx = lockupX + logoSize + gap;
        float shimmerPos = ((now % 3600L) / 3600f) * (WORD.length() + 4) - 2f;
        for (int i = 0; i < WORD.length(); i++) {
            float charDelay = 120f + i * 48f;
            float charRaw = Math.max(0f, Math.min(1f, (since - charDelay) / 320f));
            float charEase = 1f - (1f - charRaw) * (1f - charRaw) * (1f - charRaw);
            if (charEase > 0.01f) {
                String ch = String.valueOf(WORD.charAt(i));
                int cyOff = Math.round((1f - charEase) * 6f);
                float dist = Math.abs(i - shimmerPos);
                float shim = Math.max(0f, 1f - dist * 0.85f);
                int rgb = Ui.mix(0xFFFFFFFF, 0xFF6EE7B7, shim * 0.55f) & 0xFFFFFF;
                int ca = Math.round(255 * charEase);
                g.text(mark, ch, wx, y + cyOff, (ca << 24) | rgb, false);
            }
            wx += mark.width(String.valueOf(WORD.charAt(i))) + TRACKING;
        }

        // Subtítulo abaixo do wordmark
        Font subFont = AstucyFonts.ui8();
        String sub = "NEXT-GEN MINECRAFT CLIENT";
        int subA = Math.round(0x90 * Math.max(0f, Math.min(1f, (since - 320f) / 360f)));
        if (subA > 4) {
            g.text(subFont, sub, cx - subFont.width(sub) / 2, y + mark.lineHeight + 5, (subA << 24) | 0x94A3B8, false);
        }

        // ---- widgets (botões + menu do client) ----
        for (Renderable r : inv.astucy$renderables()) {
            if (r instanceof PlainTextButton) {
                continue;
            }
            r.extractRenderState(g, mouseX, mouseY, partial);
        }

        // ---- rodapé: cliente à esquerda (com mini logo girando), versão à direita ----
        Font small = AstucyFonts.ui8();
        int footA = Math.round(0xB4 * eased);
        Skin.logo(g, 15f, gh - 9f, 9f, idleSpin * 1.2f, (footA << 24) | 0x22C55E);
        g.text(small, "Astucy Client 0.1.0", 23, gh - 13, (footA << 24) | 0x6E7781, false);
        String tag = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric";
        g.text(small, tag, gw - small.width(tag) - 10, gh - 13, (footA << 24) | 0x4E565F, false);

        // fade de entrada curto
        int fadeA = Math.round((1f - eased) * 255f);
        if (fadeA > 0) {
            Ui.rect(g, 0, 0, gw, gh, fadeA << 24);
        }
    }
}
