package com.astucy.client.mixin;

import com.astucy.client.module.Modules;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak alternado: o KeyboardInput reescreve as teclas a cada tick — aqui
 * seguramos o sneak quando o módulo está travado.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void astucy$toggleSneak(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (!Modules.isSneakLatched()) {
            return;
        }
        KeyboardInput self = (KeyboardInput) (Object) this;
        if (!self.shiftKeyDown) {
            self.shiftKeyDown = true;
        }
    }
}
