package com.astucy.client.gui;

import com.astucy.client.ui.Skin;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Botãozinho quadrado ao lado dos botões do vanilla que abre o menu do client:
 * exibe a logo oficial do Astucy que gira e acende em esmeralda no hover.
 */
public final class ClientMenuButton extends AbstractWidget {

    private float hoverAnim = 0f;
    private float spinAngle = 0f;
    private long lastMs = System.currentTimeMillis();

    public ClientMenuButton(int x, int y, int size) {
        super(x, y, size, size, Component.literal("Astucy menu"));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.06f, Math.max(0.002f, (now - lastMs) / 1000f));
        lastMs = now;

        int x = getX();
        int y = getY();
        int s = getWidth();
        float target = isHovered() ? 1f : 0f;
        hoverAnim += (target - hoverAnim) * 0.22f;
        spinAngle += dt * (0.8f + hoverAnim * 4.2f);

        int bg = Ui.mix(0xD811151C, 0xEE16241F, hoverAnim);
        Ui.rrect(g, x, y + 1, s, s, 0x38000000, 6);
        Ui.rrect(g, x, y, s, s, bg, 6);
        if (hoverAnim > 0.02f) {
            Skin.glow(g, x + s / 2, y + s / 2, s, ((int) (0x2E * hoverAnim) << 24) | 0x22C55E);
            Ui.rframe(g, x, y, s, s, Ui.mix(0x0022C55E, 0x8822C55E, hoverAnim), 6);
        }

        int c = Ui.mix(0xFFD6DBE1, 0xFF34D399, hoverAnim);
        float logoSz = (s - 6) + hoverAnim * 1.2f;
        Skin.logo(g, x + s / 2f, y + s / 2f, logoSz, spinAngle, c);
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
