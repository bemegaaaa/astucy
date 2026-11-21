package com.astucy.client.mixin;

import com.astucy.client.module.Modules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true)
    private void astucy$hideVanillaCrosshair(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.CROSSHAIR.enabled) {
            cir.setReturnValue(false);
        }
    }
}
