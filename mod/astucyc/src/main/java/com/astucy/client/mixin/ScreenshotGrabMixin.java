package com.astucy.client.mixin;

import com.astucy.client.share.ScreenshotShare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F2: marca o instante do grab vanilla — o bacheca::<funcao> guarda a maior
 * print da pasta de screenshots ao persistir, e o chat oferece as ações
 * (copiar a imagem / transformar em link) igual a um client comercial.
 */
@Mixin(value = Screenshot.class, priority = 900)
public abstract class ScreenshotGrabMixin {

    @Inject(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", at = @At("TAIL"))
    private static void astucy$grabbed(Minecraft mc, boolean hideGui, CallbackInfo ci) {
        ScreenshotShare.onGrab();
    }
}
