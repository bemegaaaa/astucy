package com.astucy.client.mixin;

import com.astucy.client.module.Modules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Redirect(
        method = "render(FZII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(IIIIII)V"
        )
    )
    private void astucy$skipVanillaCrosshair(InGameHud hud, int x, int y, int u, int v, int width, int height) {
        if (Modules.CROSSHAIR.enabled && u == 0 && v == 0 && width == 16 && height == 16) {
            return;
        }
        hud.drawTexture(x, y, u, v, width, height);
    }
}
