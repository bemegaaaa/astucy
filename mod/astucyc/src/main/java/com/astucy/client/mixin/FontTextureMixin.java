package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Corrige o recorte/achatamento das bordas curvas ('O', 'o', 'C', 'e', 'S')
 * nos atlas de fontes TTF com {@code FilterMode.LINEAR}:
 *
 * O {@link FontTexture} empacota os glifos do FreeType sem margem (0px) —
 * começando em (0, 0) da textura 256x256. Com filtro LINEAR, o topo dos
 * glifos na primeira linha sofre clamp na borda da textura enquanto a base e
 * as laterais amostram texels fora do glifo (memória não inicializada).
 *
 * Envolvemos cada {@link GlyphBitmap} TTF com 1 texel transparente (zerado)
 * nos 4 lados e compensamos os bearings em 1/oversample, garantindo que a
 * interpolação bilinear 2x2 tenha uma margem limpa em volta de todo o glifo.
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin {

    @Shadow
    @Final
    private boolean colored;

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true)
    private GlyphBitmap astucy$padTtfGlyph(GlyphBitmap bitmap) {
        if (this.colored || bitmap == null || bitmap.isColored() || bitmap.getOversample() <= 1.0f) {
            return bitmap;
        }
        int w = bitmap.getPixelWidth();
        int h = bitmap.getPixelHeight();
        if (w <= 0 || h <= 0 || w > 250 || h > 250) {
            return bitmap;
        }
        return new GlyphBitmap() {
            @Override
            public int getPixelWidth() {
                return w + 2;
            }

            @Override
            public int getPixelHeight() {
                return h + 2;
            }

            @Override
            public void upload(int x, int y, GpuTexture texture) {
                try (NativeImage clear = new NativeImage(NativeImage.Format.LUMINANCE, w + 2, h + 2, true)) {
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, clear, 0, 0, x, y);
                } catch (Throwable ignored) {
                }
                bitmap.upload(x + 1, y + 1, texture);
            }

            @Override
            public boolean isColored() {
                return false;
            }

            @Override
            public float getOversample() {
                return bitmap.getOversample();
            }

            @Override
            public float getBearingLeft() {
                return bitmap.getBearingLeft() - (1.0f / bitmap.getOversample());
            }

            @Override
            public float getBearingTop() {
                return bitmap.getBearingTop() + (1.0f / bitmap.getOversample());
            }
        };
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void astucy$registerLinearView(GlyphInfo info, GlyphBitmap bitmap, CallbackInfoReturnable<BakedSheetGlyph> cir) {
        if (!this.colored && bitmap != null && bitmap.getOversample() > 1.0f) {
            FontTexture self = (FontTexture) (Object) this;
            AstucyFonts.markLinearView(self.getTextureView());
        }
    }
}
