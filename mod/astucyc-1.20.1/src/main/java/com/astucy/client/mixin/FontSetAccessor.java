package com.astucy.client.mixin;

import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(FontSet.class)
public interface FontSetAccessor {
    @Accessor("textures")
    List<FontTexture> astucy$getTextures();
}
