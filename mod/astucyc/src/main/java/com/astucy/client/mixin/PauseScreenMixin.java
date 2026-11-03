package com.astucy.client.mixin;

import com.astucy.client.gui.ClientMenuButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menu de pausa do jogo (ESC) ganha o mesmo atalho da tela de título:
 * o botãozinho do Astucy no canto superior direito. Assim as configurações
 * ficam acessíveis de dentro do mundo, não só antes de entrar.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void astucy$menuButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        ((ScreenInvoker) this).astucy$addWidget(
                new ClientMenuButton(self.width - 26, 6, 18));
    }
}
