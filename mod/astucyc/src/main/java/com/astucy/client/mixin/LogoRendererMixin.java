package com.astucy.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Esconde o logo vanilla do Minecraft (o Astucy desenha o próprio). */
@Mixin(LogoRenderer.class)
public abstract class LogoRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V",
            at = @At("HEAD"), cancellable = true)
    private void astucy$hideLogo(GuiGraphicsExtractor g, int width, float alpha, int heightOffset, CallbackInfo ci) {
        ci.cancel();
    }
}
