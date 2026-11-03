package com.astucy.client.mixin;

import com.astucy.client.config.AstucyConfig;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Chat infinito: o vanilla poda a lista de EXIBICAO em 100 linhas — isso
 * limitava o scroll pra cima (√___. O retail do mod aumentava so o
 * allMessages (guarda interna), e o jogo ainda so ia 100 linhas pra cima.
 * Aqui: a poda da lista de exibicao passa a 30.000 linhas (~sensibly
 * "infinito" — dezenas de horas de chat + ~MB de RAM) e a guarda interna
 * acompanha o mesmo limite.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Inject(method = "addMessageToQueue", at = @At("HEAD"), cancellable = true)
    private void astucy$infiniteHistory(GuiMessage message, CallbackInfo ci) {
        if (!AstucyConfig.prefBool("chat_history", true)) {
            return;
        }
        allMessages.addFirst(message);
        while (allMessages.size() > CHAT_MAX) {
            allMessages.removeLast();
        }
        ci.cancel();
    }

    @Unique
    private static final int CHAT_MAX = 30_000;

    /** Poda da lista de exibicao: 100 -> 30.000 linhas (o limite do scroll). */
    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
    private int astucy$displayQueueLimit(int original) {
        return CHAT_MAX;
    }
}
