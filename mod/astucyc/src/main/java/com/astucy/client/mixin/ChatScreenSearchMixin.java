package com.astucy.client.mixin;

import com.astucy.client.share.ChatSearchState;
import com.astucy.client.share.MessageFeedback;
import com.astucy.client.share.ScreenshotShare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pesquisa no chat (mecânica do Enhanced Chat, 557k downloads):
 * um EditBox REAL adicionado à tela — acento/IME/backspace/paste via
 * pipeline vanilla (o foco do teclado vai pra ele, o charTyped natural
 * alimenta o responder). Um clique abre; Esc fecha e limpa; Ctrl+F ativa.
 * O filtro é o visibleMessageFilter do vanilla sobre TODO o histórico.
 */
@Mixin(value = ChatScreen.class, priority = 900)
public abstract class ChatScreenSearchMixin extends Screen {

    @Unique
    private EditBox astucy$box;
    @Unique
    private boolean astucy$boxFocusedInThisScreen;

    protected ChatScreenSearchMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void astucy$init(CallbackInfo ci) {
        ChatSearchState.clear();
        this.astucy$box = new EditBox(this.font, 4, height - 46, 220, 12, Component.literal("Search"));
        astucy$box.setMaxLength(28);
        astucy$box.setBordered(false);
        astucy$box.setVisible(false); // o desenho é nosso (a box é só o coletor)
        astucy$box.setCanLoseFocus(false);
        astucy$box.setResponder(ChatSearchState::onQueryChanged);
        addRenderableWidget(astucy$box);
        astucy$boxFocusedInThisScreen = false;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void astucy$removed(CallbackInfo ci) {
        // fecha a tela = limpa a busca: nada de chat "vazio" voltando pro jogo
        ChatSearchState.clear();
        this.astucy$box = null;
        astucy$boxFocusedInThisScreen = false;
    }

    /** UM clique abre a busca; cliques seguintes NÃO fecham (Esc fecha —
     *  nada de perder a busca num clique descuidado). A linha de ações da
     *  print consome o seu próprio clique antes (ClickAction). */
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
        // cliques nas linhas de ação da print ([Copy image] / [Link] no chat)
        if (ScreenshotShare.clickAction(mc, event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
        }
        boolean onPill = ChatSearchState.hitBox(height, event.x(), event.y());
        if (onPill) {
            if (!astucy$boxFocusedInThisScreen) {
                astucy$open();
            }
            cir.setReturnValue(true);
            return;
        }
        if (astucy$boxFocusedInThisScreen) {
            // clicou fora da pílula de busca (ex: na barra de chat normal ou num link):
            // tira o foco da busca mantendo o filtro ativo e deixa o ChatScreen processar o clique
            astucy$close(false);
        }
    }

    @Unique
    private void astucy$open() {
        ChatSearchState.focus();
        astucy$boxFocusedInThisScreen = true;
        if (astucy$box != null) {
            astucy$box.setFocused(true);
            astucy$box.setValue(ChatSearchState.query); // producer + responder
            setFocused(astucy$box);
        }
    }

    @Unique
    private void astucy$close(boolean clear) {
        ChatSearchState.unfocus();
        if (clear) {
            ChatSearchState.onQueryChanged("");
            ChatSearchState.apply();
        }
        if (astucy$box != null) {
            astucy$box.setFocused(false);
        }
        setFocused(null);
        astucy$boxFocusedInThisScreen = false;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void astucy$keys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            astucy$open();
            cir.setReturnValue(true);
            return;
        }
        if (!astucy$boxFocusedInThisScreen) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
            astucy$close(false);
            cir.setReturnValue(true);
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            astucy$close(true);
            cir.setReturnValue(true);
        }
        // backspace/letras: o EditBox focado (child) recebe antes de nós
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0, expect = 0)
    private void astucy$draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        ChatSearchState.drain();
        ChatSearchState.draw(g, astucy$box == null ? ChatSearchState.query : astucy$box.getValue());
        MessageFeedback.draw(g);
    }

}
