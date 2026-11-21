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
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Locale;

/**
 * Player Health Indicator: escreve a vida no nametag dos outros jogadores ("❤ 12.5").
 */
@Mixin(EntityRenderer.class)
public abstract class PlayerHealthMixin {

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component astucy$healthTag(Entity entity) {
        Component base = entity.getDisplayName();
        Module m = Modules.find("healthtag");
        if (m == null || !m.enabled) {
            return base;
        }
        if (!(entity instanceof Player p) || p == Minecraft.getInstance().player || base == null) {
            return base;
        }
        float hp = p.getHealth() + p.getAbsorptionAmount();
        ChatFormatting color = hp > 14f
                ? ChatFormatting.GREEN
                : hp > 7f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return base.copy().append(
                Component.literal(String.format(Locale.ROOT, "  \u2764 %.1f", hp)).withStyle(color));
    }
}
