package com.astucy.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Correção óptica de gama (sRGB stem darkening) nos glifos TTF gerados pelo
 * FreeType: sem correção de gama, pixels de cobertura 50% nas curvas superior
 * e inferior do 'O' e 'o' caem para ~22% de luminância física no blend linear,
 * parecendo afinados ou irregulares.
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

    @Inject(method = "copyFromFont", at = @At("RETURN"))
    private void astucy$opticalGammaForTtf(FT_Face face, int glyphIndex, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        try {
            NativeImage self = (NativeImage) (Object) this;
            long ptr = self.getPointer();
            int total = self.getWidth() * self.getHeight();
            if (ptr == 0L || total <= 0) {
                return;
            }
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
