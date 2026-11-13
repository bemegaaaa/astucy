package com.astucy.client.share;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;

import java.util.Locale;

/**
 * Estado da busca no chat (query/foco) compartilhado entre o ChatScreen e
 * o EditBox de pesquisa (padrão do Enhanced Chat: widget REAL do jogo,
 * então acento/IME/caret/foco funcionam com o esquema vanilla).
 * O filtro usa o visibleMessageFilter do próprio ChatComponent — a lista
 * de exibição passa a conter SÓ as mensagens que batem na busca.
 */
public final class ChatSearchState {

    private ChatSearchState() {
    }

    private static java.lang.reflect.Field chatField;

    public static String query = "";
    public static boolean focus;
    private static boolean pending;
    private static long lastApplyMs;

    private static ChatComponent chat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return null;
        }
        try {
            if (chatField == null) {
                for (java.lang.reflect.Field fd : mc.gui.getClass().getDeclaredFields()) {
                    if (fd.getType() == ChatComponent.class) {
                        fd.setAccessible(true);
                        chatField = fd;
                        break;
                    }
                }
            }
            return chatField == null ? null : (ChatComponent) chatField.get(mc.gui);
        } catch (Throwable t) {
            return null;
        }
    }

    /** chamado pelo responder do EditBox a cada caractere. */
    public static void onQueryChanged(String value) {
        query = value == null ? "" : value;
        pending = true;
    }

    /** aplica o filtro (resultados ao vivo, sem Enter) com debounce leve —
     *  recalcular 30k linhas por caractere engasga; Chelsea resetscroll junto. */
    public static void apply() {
        pending = false;
        lastApplyMs = System.currentTimeMillis();
        ChatComponent chat = chat();
        if (chat == null) {
            return;
        }
        try {
            if (query.isBlank()) {
                chat.setVisibleMessageFilter(null);
                return;
            }
            String needle = query.toLowerCase(Locale.ROOT);
            chat.setVisibleMessageFilter(
                    m -> m.content().getString().toLowerCase(Locale.ROOT).contains(needle));
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
        // vazio aplica na hora (Esc/limpeza precisa desfiltra já), texto com
        // debounce de 160ms — a digitação não deve custar um refresh por char
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
    public static void draw(GuiGraphicsExtractor g, String value) {
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
        g.text(f, label, 23, y + 4, has ? Ui.TEXT : 0x88FFFFFF, false);
        boolean caret = focus && (System.currentTimeMillis() / 500L) % 2L == 0L;
        if (caret) {
            Ui.rect(g, 23 + f.width(label), y + 4, 1, f.lineHeight, 0xFF38C975);
        }
    }
}
