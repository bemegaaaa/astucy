package com.astucy.client.mixin;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caracteres REAIS do teclado (codepoint, layout-certo, acentos ABNT2):
 * o EditBox do chat usa charTyped, e a busca com foco consome aqui antes.
 */
@Mixin(value = EditBox.class, priority = 950)
public abstract class ChatInputCharsMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void astucy$chars(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !(mc.gui.screen() instanceof ChatScreen)) {
                return;
            }
            if (event.isAllowedChatCharacter()
                    && com.astucy.client.share.ChatSearchState.tryChar(event.codepointAsString())) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
