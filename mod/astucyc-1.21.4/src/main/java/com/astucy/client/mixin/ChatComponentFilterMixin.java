package com.astucy.client.mixin;

import com.astucy.client.share.ChatSearchState;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Filtra as mensagens exibidas no chat em tempo real quando a busca
 * do ChatScreen está ativa (substitui setVisibleMessageFilter da 26.x).
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentFilterMixin {

    @Inject(method = "addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V",
            at = @At("HEAD"), cancellable = true)
    private void astucy$filterMessage(GuiMessage message, CallbackInfo ci) {
        if (!ChatSearchState.matches(message)) {
            ci.cancel();
        }
    }
}
