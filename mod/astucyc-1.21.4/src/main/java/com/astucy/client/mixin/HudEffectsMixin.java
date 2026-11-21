package com.astucy.client.mixin;

import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Efeitos: se o mÃ³dulo Effects do Astucy estÃ¡ ligado, os Ã­cones de efeito do
 * vanilla (canto superior direito) somem â€” o HUD do cliente jÃ¡ mostra eles.
 */
@Mixin(Gui.class)
public abstract class HudEffectsMixin {

    @Inject(method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void astucy$hideVanillaEffects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        Module m = Modules.find("efeitos");
        if (m != null && m.enabled) {
            ci.cancel();
        }
    }
}
