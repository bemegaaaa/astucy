package com.astucy.client.mixin;

import com.astucy.client.share.ChatSearchState;
import com.astucy.client.share.MessageFeedback;
import com.astucy.client.share.ScreenshotShare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pesquisa no chat (mecÃ¢nica do Enhanced Chat, 557k downloads):
 * um EditBox REAL adicionado Ã  tela â€” acento/IME/backspace/paste via
 * pipeline vanilla (o foco do teclado vai pra ele, o charTyped natural
 * alimenta o responder). Um clique abre; Esc fecha e limpa; Ctrl+F ativa.
 * O filtro Ã© o visibleMessageFilter do vanilla sobre TODO o histÃ³rico.
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
        astucy$box.setVisible(false); // o desenho Ã© nosso (a box Ã© sÃ³ o coletor)
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

    /** UM clique abre a busca; cliques seguintes NÃƒO fecham (Esc fecha â€”
     *  nada de perder a busca num clique descuidado). A linha de aÃ§Ãµes da
     *  print consome o seu prÃ³prio clique antes (ClickAction). */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void astucy$clickOpen(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        int mods = Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0;
        // CTRL + botÃ£o direito: copia a mensagem inteira sob o cursor
        if (button == 1 && Screen.hasControlDown()
                && ScreenshotShare.copyMessageUnderCursor(mc, mx, my, mods)) {
            MessageFeedback.say();
            cir.setReturnValue(true);
            return;
        }
        if (button != 0) {
            return;
        }
        // cliques nas linhas de aÃ§Ã£o da print ([Copy image] / [Link] no chat)
        if (ScreenshotShare.clickAction(mc, mx, my)) {
            cir.setReturnValue(true);
            return;
        }
        boolean onPill = ChatSearchState.hitBox(height, mx, my);
        if (onPill) {
            if (!astucy$boxFocusedInThisScreen) {
                astucy$open();
            }
            cir.setReturnValue(true);
            return;
        }
        if (astucy$boxFocusedInThisScreen) {
            // clicou fora da pÃ­lula de busca (ex: na barra de chat normal ou num link):
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
    private void astucy$keys(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || Screen.hasControlDown();
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
        // backspace/letras: o EditBox focado (child) recebe antes de nÃ³s
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void astucy$draw(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        ChatSearchState.drain();
        ChatSearchState.draw(g, astucy$box == null ? ChatSearchState.query : astucy$box.getValue());
        MessageFeedback.draw(g);
    }

}
