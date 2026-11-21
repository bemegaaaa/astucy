package com.astucy.client.mixin;

import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * Player Health Indicator: escreve a vida no nametag dos outros jogadores
 * ("❤ 12.5"), colorida por faixa. Alvo é o getNameTag base — leitura pura,
 * sem tocar em nada do jogo além do texto do nome.
 */
@Mixin(EntityRenderer.class)
public abstract class PlayerHealthMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void astucy$healthTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        Module m = Modules.find("healthtag");
        if (m == null || !m.enabled) {
            return;
        }
        if (!(entity instanceof Player p) || p == Minecraft.getInstance().player) {
            return;
        }
        Component base = cir.getReturnValue();
        if (base == null) {
            return;
        }
        float hp = p.getHealth() + p.getAbsorptionAmount();
        ChatFormatting color = hp > 14f
                ? ChatFormatting.GREEN
                : hp > 7f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        cir.setReturnValue(base.copy().append(
                Component.literal(String.format(Locale.ROOT, "  \u2764 %.1f", hp)).withStyle(color)));
    }
}
