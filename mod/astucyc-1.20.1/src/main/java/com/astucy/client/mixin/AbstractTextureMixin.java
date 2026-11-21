package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * RenderType.textIntensity(...) chama AbstractTexture.setFilter(false, false)
 * todo frame via TextureStateShard, sobrescrevendo GL_LINEAR com GL_NEAREST.
 * Para FontTextures marcadas como lineares (fontes TTF Geist), preservamos blur = true.
 */
@Mixin(AbstractTexture.class)
public abstract class AbstractTextureMixin {

    @ModifyVariable(method = "setFilter(ZZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean astucy$forceLinearForTtfFont(boolean blur) {
        if (!blur && (Object) this instanceof FontTexture ft && AstucyFonts.isLinearTexture(ft)) {
            return true;
        }
        return blur;
    }
}
