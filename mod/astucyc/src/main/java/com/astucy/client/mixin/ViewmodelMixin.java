package com.astucy.client.mixin;

import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Viewmodel: desloca/escala as mãos em primeira pessoa aplicando a
 * transformação na PoseStack que o jogo já passa pro render das mãos —
 * nenhum outro render é afetado (o braço do outro player não muda).
 */
@Mixin(ItemInHandRenderer.class)
public class ViewmodelMixin {

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void astucy$viewmodel(float partialTick, PoseStack pose, SubmitNodeCollector collector,
                                  LocalPlayer player, int light, CallbackInfo ci) {
        Module m = Modules.find("viewmodel");
        if (m == null || !m.enabled) {
            return;
        }
        float x = (float) m.numProp("vmx", 0.0);
        float y = (float) m.numProp("vmy", 0.0);
        float z = (float) m.numProp("vmz", 0.0);
        float s = (float) m.numProp("vmscale", 1.0);
        if (x == 0f && y == 0f && z == 0f && s == 1f) {
            return;
        }
        pose.translate(x, y, z);
        if (s != 1f) {
            pose.scale(s, s, s);
        }
    }
}
