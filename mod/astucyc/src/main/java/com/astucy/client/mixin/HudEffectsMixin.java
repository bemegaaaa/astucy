package com.astucy.client.mixin;

import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Efeitos: se o módulo Effects do Astucy está ligado, os ícones de efeito do
 * vanilla (canto superior direito) somem — o HUD do cliente já mostra eles.
 */
@Mixin(Hud.class)
public abstract class HudEffectsMixin {

    @Inject(method = "extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void astucy$hideVanillaEffects(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        Module m = Modules.find("efeitos");
        if (m != null && m.enabled) {
            ci.cancel();
        }
    }
}
