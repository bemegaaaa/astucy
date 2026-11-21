package com.astucy.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sem o texto amarelo de splash (polui o wordmark do Astucy). */
@Mixin(SplashRenderer.class)
public abstract class SplashRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void astucy$hideSplash(GuiGraphics g, int width, Font font, int color, CallbackInfo ci) {
        ci.cancel();
    }
}
