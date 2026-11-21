package com.astucy.client.mixin;

import com.astucy.client.module.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom sem tocar no options.fov: multiplica o FOV que GameRenderer.getFov calcula por frame.
 */
@Mixin(value = GameRenderer.class, priority = 900)
public abstract class CameraFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void astucy$zoomFov(Camera camera, float partial, boolean useFovSetting,
                                CallbackInfoReturnable<Double> cir) {
        float f = Modules.zoomFovFactor();
        float rush = Modules.moveRushFactor();
        if (f < 0.999f || rush > 0.001f) {
            float mult = f < 0.999f ? f : (1f + rush);
            cir.setReturnValue(Math.max(1.5D, cir.getReturnValueD() * (double) mult));
        }
    }
}
