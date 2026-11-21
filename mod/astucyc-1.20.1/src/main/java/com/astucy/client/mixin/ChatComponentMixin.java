package com.astucy.client.mixin;

import com.astucy.client.config.AstucyConfig;
import com.astucy.client.share.ChatSearchState;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Chat infinito (30.000 linhas) + suporte ao filtro da pesquisa no 1.20.1.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Unique
    private static final int CHAT_MAX = 30_000;

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    protected abstract void addMessage(Component component, MessageSignature signature,
                                       int addedTime, GuiMessageTag tag, boolean refresh);

    @ModifyConstant(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            constant = @Constant(intValue = 100))
    private int astucy$chatLimit(int original) {
        return AstucyConfig.prefBool("chat_history", true) ? CHAT_MAX : original;
    }

    @Inject(method = "refreshTrimmedMessage", at = @At("HEAD"), cancellable = true)
    private void astucy$filterRefresh(CallbackInfo ci) {
        if (!ChatSearchState.hasFilter()) {
            return;
        }
        trimmedMessages.clear();
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            GuiMessage msg = allMessages.get(i);
            if (ChatSearchState.matches(msg.content())) {
                addMessage(msg.content(), msg.signature(), msg.addedTime(), msg.tag(), true);
            }
        }
        ci.cancel();
    }
}
