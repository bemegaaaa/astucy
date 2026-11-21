package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * RenderType.text(...) chama setFilter(false, false) via TextureStateShard a cada
 * frame, sobrescrevendo o GL_LINEAR dos atlas TTF. Intercepta setFilter(blur, mipmap)
 * para forçar blur=true sempre que a textura for um FontTexture linear do Astucy.
 */
@Mixin(AbstractTexture.class)
public abstract class AbstractTextureFilterMixin {

    @ModifyVariable(method = "setFilter(ZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean astucy$forceLinearFontFilter(boolean blur) {
        if (!blur && AstucyFonts.isLinearTexture(this)) {
            return true;
        }
        return blur;
    }
}
