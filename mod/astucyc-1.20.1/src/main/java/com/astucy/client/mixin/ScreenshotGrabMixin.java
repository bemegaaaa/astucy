package com.astucy.client.mixin;

import com.astucy.client.share.ScreenshotShare;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/**
 * F2: marca o instante do grab vanilla para oferecer as ações no chat.
 */
@Mixin(value = Screenshot.class, priority = 900)
public abstract class ScreenshotGrabMixin {

    @Inject(method = "_grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
            at = @At("TAIL"))
    private static void astucy$grabbed(File gameDirectory, String fileName, RenderTarget buffer,
                                       Consumer<Component> consumer, CallbackInfo ci) {
        ScreenshotShare.onGrab();
    }
}
