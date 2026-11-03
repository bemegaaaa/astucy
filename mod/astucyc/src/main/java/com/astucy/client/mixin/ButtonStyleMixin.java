package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Botões no estilo do Astucy em todas as telas: vidro escuro, fio fino de luz
 * e hover neutro (sem barra verde — só clareamento no hover). Texto pequeno
 * em DM Sans 9px, igual ao resto da interface.
 */
@Mixin(AbstractButton.class)
public abstract class ButtonStyleMixin {

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void astucy$style(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        AbstractButton self = (AbstractButton) (Object) this;
        int w = self.getWidth();
        int h = self.getHeight();
        // 20x20 = botão de ícone do vanilla (idioma/acessibilidade): deixa o sprite
        if (w < 40 || h < 16) {
            return;
        }

        int x = self.getX();
        int y = self.getY();
        boolean hover = self.isHovered() && self.active;

        // botão chapado, canto quase reto (raio 4), só clareia no hover
        Ui.rrect(g, x, y, w, h, hover ? 0xFF232529 : 0xFF191A1D, 4);

        Font f = AstucyFonts.ui8();
        String label = self.getMessage().getString();
        int color = self.active ? 0xFFFFFFFF : 0x66FFFFFF;
        int lw = f.width(label);
        int room = w - 10;
        if (lw > room) {
            // rótulo longo: reduz a escala em vez de trocar de fonte
            float fit = Math.max(0.6f, room / (float) lw);
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2f, y + h / 2f);
            g.pose().scale(fit, fit);
            g.text(f, label, -lw / 2, Math.round(-(f.lineHeight / 2f) + 1f), color, false);
            g.pose().popMatrix();
        } else {
            g.text(f, label, x + w / 2 - lw / 2, y + (h - f.lineHeight) / 2 + 1, color, false);
        }

        ci.cancel();
    }
}
