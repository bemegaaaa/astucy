package com.astucy.client.share;

import com.astucy.client.AstucyFonts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;

import java.util.Locale;

/**
 * Estado da busca no chat (query/foco/pill) compartilhado entre os mixins
 * do ChatScreen (clique/teclado/desenho) e do EditBox (caracteres reais).
 * Morar num classe comum é necessário: mixim classes não podem expor
 * helpers estáticos públicos.
 */
public final class ChatSearchState {

    private ChatSearchState() {
    }

    private static java.lang.reflect.Field chatField;

    public static String query = "";
    public static boolean focus;
    public static int pillX, pillY, pillW, pillH;

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

    /** aplica o filtro nativo (resultados ao vivo, sem Enter). */
    public static void apply() {
        ChatComponent chat = chat();
        if (chat == null) {
            return;
        }
        if (query.isBlank()) {
            chat.setVisibleMessageFilter(null);
            return;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        chat.setVisibleMessageFilter(m -> m.content().getString().toLowerCase(Locale.ROOT).contains(needle));
    }

    /** chamado pelo ChatInputCharsMixin: consome o caractere quando a busca
     *  tem foco (o texto NÃO entra no campo do chat). */
    public static boolean tryChar(String cp) {
        if (!focus || cp == null || cp.isEmpty()) {
            return false;
        }
        if (query.length() < 28) {
            query += cp;
            apply();
        }
        return true;
    }

    public static void clear() {
        query = "";
        apply();
        focus = false;
    }

    /** pílula da busca (desenho + rect pro clique). */
    public static void draw(GuiGraphicsExtractor g) {
        Font f = AstucyFonts.menu();
        int h = g.guiHeight();
        int y = h - 46;
        boolean has = !query.isEmpty();
        String label;
        if (focus || has) {
            label = has ? query : "";
        } else {
            label = "Search - click to filter";
        }
        int w = Math.max(110, f.width(label) + 16);
        pillX = 4;
        pillY = y;
        pillW = w;
        pillH = 14;
        Ui.rrect(g, 4, y, w, 14, 0xC8080A0D, 5);
        g.text(f, label, 9, y + 3, has ? Ui.TEXT : 0x99FFFFFF, false);
        if (focus && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            Ui.rect(g, 9 + f.width(label), y + 3, 1, f.lineHeight, 0xFFFFFFFF);
        }
    }
}
