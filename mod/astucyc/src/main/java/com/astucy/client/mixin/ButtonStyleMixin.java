package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Botões no estilo do Astucy em todas as telas: vidro escuro anti-aliased,
 * micro-borda luminosa e hover interpolado suave.
 */
@Mixin(AbstractButton.class)
public abstract class ButtonStyleMixin {

    @Unique
    private float astucy$hover = 0f;

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
        float target = hover ? 1f : 0f;
        astucy$hover += (target - astucy$hover) * 0.25f;
        if (Math.abs(target - astucy$hover) < 0.01f) {
            astucy$hover = target;
        }

        int bg = Ui.mix(0xD811151C, 0xEE1A222D, astucy$hover);
        Ui.rrect(g, x, y + 1, w, h, 0x38000000, 6);
        Ui.rrect(g, x, y, w, h, bg, 6);
        if (astucy$hover > 0.02f) {
            Ui.rframe(g, x, y, w, h, Ui.mix(0x0038C975, 0x6638C975, astucy$hover), 6);
        }

        Font f = AstucyFonts.ui();
        String label = self.getMessage().getString();
        int color = self.active ? Ui.mix(0xFFE4E8F0, 0xFFFFFFFF, astucy$hover) : 0x66FFFFFF;
        int lw = f.width(label);
        int room = w - 12;
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
