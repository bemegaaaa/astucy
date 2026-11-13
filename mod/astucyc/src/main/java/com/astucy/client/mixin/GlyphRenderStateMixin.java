package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Minecraft 26.2, {@link GlyphRenderState#textureSetup()} ignora o
 * {@code FontTexture.sampler} e força {@link FilterMode#NEAREST} em todo texto
 * de GUI, deixando fontes TTF serrilhadas e com falhas nos traços finos.
 *
 * Este mixin aplica {@link FilterMode#LINEAR} nos atlas de fontes do Astucy,
 * preservando {@link FilterMode#NEAREST} na fonte bitmap vanilla.
 */
@Mixin(GlyphRenderState.class)
public class GlyphRenderStateMixin {

    @Shadow
    @Final
    private TextRenderable renderable;

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
    private void astucy$useLinearSamplerForTtf(CallbackInfoReturnable<TextureSetup> cir) {
        GpuTextureView view = this.renderable.textureView();
        if (AstucyFonts.isLinearView(view)) {
            cir.setReturnValue(TextureSetup.singleTextureWithLightmap(
                    view,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            ));
        }
    }
}
