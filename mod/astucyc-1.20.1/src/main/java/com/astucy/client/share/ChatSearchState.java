package com.astucy.client.share;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Estado da busca no chat (query/foco) compartilhado entre o ChatScreen e
 * o EditBox de pesquisa (padrão do Enhanced Chat: widget REAL do jogo,
 * então acento/IME/caret/foco funcionam com o esquema vanilla).
 * No 1.20.1 o filtro é aplicado em ChatComponentMixin durante refreshTrimmedMessage.
 */
public final class ChatSearchState {

    private ChatSearchState() {
    }

    public static String query = "";
    public static boolean focus;
    private static boolean pending;
    private static long lastApplyMs;

    private static ChatComponent chat() {
        Minecraft mc = Minecraft.getInstance();
        return (mc == null || mc.gui == null) ? null : mc.gui.getChat();
    }

    public static boolean hasFilter() {
        return query != null && !query.isBlank();
    }

    public static boolean matches(Component content) {
        if (!hasFilter()) {
            return true;
        }
        if (content == null) {
            return false;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return content.getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** chamado pelo responder do EditBox a cada caractere. */
    public static void onQueryChanged(String value) {
        query = value == null ? "" : value;
        pending = true;
    }

    /** aplica o filtro (resultados ao vivo, sem Enter) com debounce leve. */
    public static void apply() {
        pending = false;
        lastApplyMs = System.currentTimeMillis();
        ChatComponent chat = chat();
        if (chat == null) {
            return;
        }
        try {
            chat.rescaleChat();
            chat.resetChatScroll();
        } catch (Throwable ignored) {
        }
    }

    /** drena as pendências (chamado no draw do ChatScreen, por frame). */
    public static void drain() {
        if (!pending) {
            return;
        }
        long now = System.currentTimeMillis();
        if (query.isEmpty() || now - lastApplyMs >= 160L) {
            apply();
        }
    }

    public static void clear() {
        query = "";
        focus = false;
        pending = false;
        apply();
    }

    public static void focus() {
        focus = true;
    }

    public static void unfocus() {
        focus = false;
    }

    private static int lastWidth = 140;

    public static boolean hitBox(int screenH, double mx, double my) {
        int y = screenH - 46;
        return mx >= 4 && mx < 4 + Math.max(140, lastWidth) && my >= y - 2 && my < y + 18;
    }

    /** pill da busca (desenho e área de clique) — o texto é o do EditBox. */
    public static void draw(GuiGraphics g, String value) {
        Font f = AstucyFonts.menu();
        int h = g.guiHeight();
        int y = h - 46;
        boolean has = value != null && !value.isEmpty();
        String label = has ? value : (focus ? "" : "Search chat (Ctrl+F)");
        int w = Math.max(136, f.width(label) + 28);
        lastWidth = w;
        Ui.rrect(g, 4, y, w, 16, focus ? 0xE60B0E14 : 0xCC080A0D, 6);
        Ui.rframe(g, 4, y, w, 16, focus ? 0x5538C975 : 0x22FFFFFF, 6);
        Ui.icon(g, "search", 9, y + 3, 10, focus ? 0xFF38C975 : 0x99FFFFFF);
        g.drawString(f, label, 23, y + 4, has ? Ui.TEXT : 0x88FFFFFF, false);
        boolean caret = focus && (System.currentTimeMillis() / 500L) % 2L == 0L;
        if (caret) {
            Ui.rect(g, 23 + f.width(label), y + 4, 1, f.lineHeight, 0xFF38C975);
        }
    }
}
