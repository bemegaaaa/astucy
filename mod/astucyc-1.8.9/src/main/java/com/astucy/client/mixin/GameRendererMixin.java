package com.astucy.client.mixin;

import com.astucy.client.module.Modules;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void astucy$applyZoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) {
            float fov = cir.getReturnValue();
            cir.setReturnValue(Modules.applyZoomFov(fov));
        }
    }

    @Inject(method = "bobViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void astucy$noHurtCam(float tickDelta, CallbackInfo ci) {
        if (Modules.HURTCAM.enabled) {
            ci.cancel();
        }
    }
}
