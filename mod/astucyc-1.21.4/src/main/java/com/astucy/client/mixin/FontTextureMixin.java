package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adiciona 1px de margem transparente em volta de cada glifo TTF para
 * evitar recorte/artefatos na amostragem bilinear (GL_LINEAR) e registra
 * o FontTexture como textura de fonte linear.
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin {

    @Shadow
    @Final
    private boolean colored;

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true)
    private SheetGlyphInfo astucy$padTtfGlyph(SheetGlyphInfo info) {
        if (this.colored || info == null || info.isColored() || info.getOversample() <= 1.0f) {
            return info;
        }
        int w = info.getPixelWidth();
        int h = info.getPixelHeight();
        if (w <= 0 || h <= 0 || w > 250 || h > 250) {
            return info;
        }
        return new SheetGlyphInfo() {
            @Override
            public int getPixelWidth() {
                return w + 2;
            }

            @Override
            public int getPixelHeight() {
                return h + 2;
            }

            @Override
            public void upload(int x, int y) {
                try (NativeImage clear = new NativeImage(NativeImage.Format.LUMINANCE, w + 2, h + 2, true)) {
                    clear.upload(0, x, y, false);
                } catch (Throwable ignored) {
                }
                info.upload(x + 1, y + 1);
            }

            @Override
            public boolean isColored() {
                return false;
            }

            @Override
            public float getOversample() {
                return info.getOversample();
            }

            @Override
            public float getBearingLeft() {
                return info.getBearingLeft() - (1.0f / info.getOversample());
            }

            @Override
            public float getBearingTop() {
                return info.getBearingTop() + (1.0f / info.getOversample());
            }
        };
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void astucy$registerLinearTexture(SheetGlyphInfo info, CallbackInfoReturnable<BakedGlyph> cir) {
        if (!this.colored && info != null && info.getOversample() > 1.0f) {
            AstucyFonts.markLinearTexture((FontTexture) (Object) this);
        }
    }
}
