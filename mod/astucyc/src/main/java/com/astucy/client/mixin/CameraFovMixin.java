package com.astucy.client.mixin;

import com.astucy.client.module.Modules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom sem tocar no options.fov: multiplica o FOV que o vanilla calcula por
 * frame (calculateFov alimenta a projeção da câmera toda). Assim o zoom
 * ignora o limite 30–110 das opções do jogo e chega a níveis profundos,
 * suaves, com options.txt imune.
 */
@Mixin(value = Camera.class, priority = 900)
public abstract class CameraFovMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void astucy$zoomFov(float partial, CallbackInfoReturnable<Float> cir) {
        float f = Modules.zoomFovFactor();
        if (f < 0.999f) {
            cir.setReturnValue(Math.max(1.5f, cir.getReturnValueF() * f));
        }
    }
}
