package com.astucy.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Correção óptica de gama (sRGB stem darkening, exp 0.72) nos glifos TTF rasterizados
 * pelo STB TrueType em NativeImage.copyFromFont (1.20.1).
 */
@Mixin(NativeImage.class)
public abstract class NativeImageFontMixin {

    @Unique
    private static final byte[] ASTUCY_GAMMA_LUT = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            double norm = i / 255.0;
            int boosted = (int) Math.round(Math.pow(norm, 0.72) * 255.0);
            ASTUCY_GAMMA_LUT[i] = (byte) Math.max(0, Math.min(255, boosted));
        }
    }

    @Shadow
    @Final
    private NativeImage.Format format;

    @Shadow
    @Final
    private int width;

    @Shadow
    @Final
    private int height;

    @Shadow
    private long pixels;

    @Inject(method = "copyFromFont(Lorg/lwjgl/stb/STBTTFontinfo;IIIFFFFII)V", at = @At("RETURN"))
    private void astucy$opticalGammaForTtf(STBTTFontinfo info, int glyphIndex, int w, int h,
                                           float scaleX, float scaleY, float shiftX, float shiftY,
                                           int x, int y, CallbackInfo ci) {
        if (this.format != NativeImage.Format.LUMINANCE || this.pixels == 0L) {
            return;
        }
        int total = this.width * this.height;
        if (total <= 0) {
            return;
        }
        try {
            long ptr = this.pixels;
            for (int i = 0; i < total; i++) {
                int a = MemoryUtil.memGetByte(ptr + i) & 0xFF;
                if (a > 0 && a < 255) {
                    MemoryUtil.memPutByte(ptr + i, ASTUCY_GAMMA_LUT[a]);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
