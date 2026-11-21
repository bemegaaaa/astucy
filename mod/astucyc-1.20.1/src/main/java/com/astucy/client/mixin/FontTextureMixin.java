package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Margem de 1px transparente nos 4 lados dos glifos TTF + filtro GL_LINEAR
 * permanente no FontTexture (1.20.1).
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin {

    @Shadow
    @Final
    private boolean colored;

    @ModifyVariable(
            method = "add(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at = @At("HEAD"),
            argsOnly = true)
    private SheetGlyphInfo astucy$padTtfGlyph(SheetGlyphInfo info) {
        if (this.colored || info == null || info.isColored() || info.getOversample() <= 1.0f) {
            return info;
        }
        int w = info.getPixelWidth();
        int h = info.getPixelHeight();
        if (w <= 0 || h <= 0 || w > 250 || h > 250) {
            return info;
        }
        float pad = 1.0f / info.getOversample();
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
                    clear.upload(0, x, y, 0, 0, w + 2, h + 2, true, true, false, false);
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
            public float getBearingX() {
                return info.getBearingX() - pad;
            }

            @Override
            public float getBearingY() {
                return info.getBearingY() - pad;
            }

            @Override
            public float getLeft() {
                return info.getLeft() - pad;
            }

            @Override
            public float getRight() {
                return info.getRight() + pad;
            }

            @Override
            public float getUp() {
                return info.getUp() - pad;
            }

            @Override
            public float getDown() {
                return info.getDown() + pad;
            }
        };
    }

    @Inject(
            method = "add(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at = @At("RETURN"))
    private void astucy$keepLinear(SheetGlyphInfo info, CallbackInfoReturnable<BakedGlyph> cir) {
        if (!this.colored && info != null && info.getOversample() > 1.0f) {
            AbstractTexture self = (AbstractTexture) (Object) this;
            AstucyFonts.markLinearTexture(self);
            self.bind();
            self.setFilter(true, false);
        }
    }
}
