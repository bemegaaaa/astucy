package com.astucy.client.share;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Aviso minÃºsculo pÃ³s-cÃ³pia (Ctrl+botÃ£o direito no chat) â€” some sozinho. */
public final class MessageFeedback {
    private static long until;

    private MessageFeedback() {
    }

    public static void say() {
        until = System.currentTimeMillis() + 1600L;
    }

    public static void draw(GuiGraphics g) {
        if (!isShowing()) {
            return;
        }
        Font f = AstucyFonts.menu();
        int h = g.guiHeight();
        int y = h - 62;
        String label = "Copied to clipboard";
        int w = f.width(label) + 12;
        Ui.rrect(g, 4, y, w, 14, Ui.PANEL, 5);
        g.drawString(f, label, 10, y + 3, Ui.DIM, false);
    }

    private static boolean isShowing() {
        return System.currentTimeMillis() < until;
    }
}
