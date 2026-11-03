package com.astucy.client.mixin;

import com.astucy.client.AstucyFonts;
import com.astucy.client.share.ChatSearchState;
import com.astucy.client.share.MessageFeedback;
import com.astucy.client.share.ScreenshotShare;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Pesquisa no chat: um clique abre a busca, digitar JÁ FILTRA na medida
 * (os caracteres chegam pelo ChatInputCharsMixin — textinho real, com
 * acento e ABNT2), Esc fecha, Ctrl+F abre por teclado. A barra da print
 * (Copy image / Link) tem prioridade de clique, e CTRL+botão direito
 * copia a mensagem inteira sob o cursor.
 */
@Mixin(value = ChatScreen.class, priority = 900)
public abstract class ChatScreenSearchMixin extends Screen {

    protected ChatScreenSearchMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void astucy$init(CallbackInfo ci) {
        ChatSearchState.clear();
    }

    /** UM clique abre a busca; cliques seguintes NÃO fecham (Esc fecha —
     *  nada de perder a busca num clique descuidado). A barra da print
     *  consome o seu próprio clique antes. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void astucy$clickOpen(MouseButtonEvent event, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        // CTRL + botão direito: copia a mensagem inteira sob o cursor
        if (event.button() == 1 && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && ScreenshotShare.copyMessageUnderCursor(mc, event.x(), event.y(),
                        event.modifiers())) {
            MessageFeedback.say();
            cir.setReturnValue(true);
            return;
        }
        if (event.button() != 0) {
            return;
        }
        int mx = (int) Math.floor(event.x());
        int my = (int) Math.floor(event.y());
        if (ScreenshotShare.clickAction(mc, event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
        }
        if (!ChatSearchState.focus) {
            // qualquer clique abre a busca (consumido pra não dar ação de link)
            ChatSearchState.focus = true;
            cir.setReturnValue(true);
            return;
        }
        // já aberta: mantém o foco (sai com Esc ou fechando o chat)
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void astucy$keys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            ChatSearchState.focus = true;
            cir.setReturnValue(true);
            return;
        }
        if (!ChatSearchState.focus) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
            ChatSearchState.focus = false;
            cir.setReturnValue(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            ChatSearchState.clear();
            cir.setReturnValue(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!ChatSearchState.query.isEmpty()) {
                ChatSearchState.query = ChatSearchState.query.substring(0, ChatSearchState.query.length() - 1);
                ChatSearchState.apply();
            }
            cir.setReturnValue(true);
        }
        // caracteres imprimíveis chegam pelo ChatInputCharsMixin (charTyped)
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0, expect = 0)
    private void astucy$draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        ChatSearchState.draw(g);
        MessageFeedback.draw(g);
    }
}
