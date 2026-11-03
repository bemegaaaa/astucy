package com.astucy.client.gui;

import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Botãozinho quadrado ao lado dos botões do vanilla que abre o menu do client.
 * Desenhado por nós (nada de sprite do jogo) — três traços que ficam verdes no
 * hover, no mesmo vidro escuro do resto da interface.
 */
public final class ClientMenuButton extends AbstractWidget {

    public ClientMenuButton(int x, int y, int size) {
        super(x, y, size, size, Component.literal("Astucy menu"));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = getX();
        int y = getY();
        int s = getWidth();
        boolean hover = isHovered();

        Ui.rrect(g, x, y, s, s, hover ? 0xFF232529 : 0xFF191A1D, 5);

        int c = hover ? 0xFFFFFFFF : 0xFFD6DBE1;
        Ui.rect(g, x + 6, y + 6, s - 12, 1, c);
        Ui.rect(g, x + 6, y + 10, s - 12, 1, c);
        Ui.rect(g, x + 6, y + 14, s - 12, 1, c);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new RShiftScreen());
    }
}
