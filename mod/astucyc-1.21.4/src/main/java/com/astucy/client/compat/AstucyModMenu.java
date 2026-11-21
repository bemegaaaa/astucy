package com.astucy.client.compat;

import com.astucy.client.gui.ClientSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Integração opcional com o Mod Menu (Fabric).
 *
 * O Mod Menu só carrega esta classe quando o mod está instalado — sem ele,
 * nada muda e o Astucy continua abrindo as configurações pelo R-SHIFT, pelo
 * botão do menu de pausa e pelo da tela de título.
 */
public final class AstucyModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClientSettingsScreen::new;
    }
}
