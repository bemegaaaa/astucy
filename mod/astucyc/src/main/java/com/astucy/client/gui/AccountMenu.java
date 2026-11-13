package com.astucy.client.gui;

import com.astucy.client.AstucyFonts;
import com.astucy.client.account.Accounts;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Conta no topo do menu do R-SHIFT: mostra quem está logado e deixa trocar.
 *
 * Trocar de conta não é possível "a quente" — o token da sessão é de uma conta
 * só. Então o menu abre o launcher com {@code --switch-account <uuid>}, ele
 * relança o jogo com a conta escolhida, e este processo fecha logo depois.
 */
public final class AccountMenu {

    private AccountMenu() {
    }

    private static final int PANEL_W = 216;
    private static final int ROW_H = 26;
    private static final int HEAD_H = 24;
    private static final int FOOT_H = 46;
    private static final int[] AVATAR = {
            0xFF3E7BFA, 0xFF3FBF6A, 0xFFD79A34, 0xFFB45CE0,
            0xFFDD5A5A, 0xFF33B6BE, 0xFF6C7BF0, 0xFF7FA83C
    };

    private static boolean open;
    private static boolean quitRequested;
    private static String notice = "";
    private static float chipHoverAnim = 0f;
    private static float openAnim = 0f;
    private static long lastFrameMs = System.currentTimeMillis();

    private static List<Accounts.Acc> list = new ArrayList<>();

    // geometria do último frame (usada no hit-test)
    private static int chipX, chipY, chipW, chipH;
    private static int panelX, panelY;
    private static int rowsY, rowsH;
    private static int minX;

    public static boolean isOpen() {
        return open;
    }

    /* ------------------------------------------------------------------ */
    /* desenho                                                             */
    /* ------------------------------------------------------------------ */

    /** X atual do chip (calculado no drawChip). */
    public static int chipX() {
        return chipX;
    }

    /** Largura do chip (nome atual) — o campo de busca desconta isso. */
    public static int chipWidth(Font f) {
        return Math.min(118, 20 + f.width(Accounts.currentName()) + 16);
    }

    /** Chip da conta, ao lado do X de fechar. */
    public static void drawChip(GuiGraphicsExtractor g, Font f, int x0, int y0, int pw, int topH) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, Math.max(0.002f, (now - lastFrameMs) / 1000f));
        lastFrameMs = now;

        String name = Accounts.currentName();
        int h = 18;
        int w = chipWidth(f);
        int x = x0 + pw - 26 - 8 - w;
        int y = y0 + (topH - h) / 2;
        chipX = x;
        chipY = y;
        chipW = w;
        chipH = h;
        minX = x0 + 8;

        boolean hov = inside((int) cursorX, (int) cursorY, x, y, w, h);
        chipHoverAnim += ((hov ? 1f : 0f) - chipHoverAnim) * Math.min(1f, dt * 16f);
        openAnim += ((open ? 1f : 0f) - openAnim) * Math.min(1f, dt * 18f);

        int bg = Ui.mix(Ui.mix(0x18FFFFFF, 0x2AFFFFFF, chipHoverAnim), 0x2C38C975, openAnim);
        int border = Ui.mix(Ui.mix(0x1EFFFFFF, 0x3AFFFFFF, chipHoverAnim), 0x8838C975, openAnim);
        Ui.rrect(g, x, y, w, h, bg, 6);
        Ui.rframe(g, x, y, w, h, border, 6);

        avatar(g, f, Accounts.currentUuid(), name, x + 3, y + 3, 12);

        String shown = name;
        int maxTextW = w - 19 - 13;
        if (f.width(shown) > maxTextW) {
            while (!shown.isEmpty() && f.width(shown + "…") > maxTextW) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown = shown + "…";
        }
        g.text(f, shown, x + 19, y + (h - f.lineHeight) / 2 + 1, Ui.TEXT, false);

        // chevron 4x3 desenhado em pixels
        int cx = x + w - 10;
        int cy = y + h / 2 - 1;
        int cc = open ? 0xFF38C975 : (hov ? Ui.TEXT : Ui.DIM);
        Ui.rect(g, cx, cy, 1, 1, cc);
        Ui.rect(g, cx + 1, cy + 1, 1, 1, cc);
        Ui.rect(g, cx + 2, cy + 1, 1, 1, cc);
        Ui.rect(g, cx + 3, cy, 1, 1, cc);
    }

    /** Dropdown, por cima de tudo (chamado no fim do frame). */
    public static void drawPanel(GuiGraphicsExtractor g, Font f) {
        if (!open) {
            return;
        }
        String cur = Accounts.currentUuid();
        int total = list.size();
        int h = HEAD_H + Math.max(1, total) * ROW_H + FOOT_H;
        int w = PANEL_W;
        int x = Math.max(chipX + chipW - w, minX);
        int y = chipY + chipH + 6;
        panelX = x;
        panelY = y;
        rowsY = y + HEAD_H;
        rowsH = Math.max(1, total) * ROW_H;

        Ui.glass(g, x, y, w, h);
        g.text(AstucyFonts.label(), "ACCOUNT", x + 10, y + 8, Ui.DIM, false);

        int mouseX = (int) cursorX;
        int mouseY = (int) cursorY;

        if (total == 0) {
            g.text(f, "No accounts synced", x + 10, rowsY + 8, Ui.DIM, false);
        } else {
            for (int i = 0; i < total; i++) {
                Accounts.Acc a = list.get(i);
                int ry = rowsY + i * ROW_H;
                boolean hov = inside(mouseX, mouseY, x + 4, ry, w - 8, ROW_H);
                if (hov) {
                    Ui.rrect(g, x + 4, ry, w - 8, ROW_H, 0x14FFFFFF, 6);
                }
                avatar(g, f, a.uuid(), a.name(), x + 10, ry + 6, 14);
                g.text(f, a.name(), x + 30, ry + 5, Ui.TEXT, false);
                String sub = a.uuid().equals(cur)
                        ? "in use"
                        : (a.source() == null || a.source().isBlank() ? "Microsoft" : sourceLabel(a.source()));
                g.text(AstucyFonts.ui8(), sub, x + 30, ry + 15,
                        a.uuid().equals(cur) ? Ui.GREEN : Ui.DIM, false);
                if (a.uuid().equals(cur)) {
                    Ui.rect(g, x + w - 12, ry + ROW_H / 2 - 1, 4, 4, Ui.GREEN);
                }
            }
        }

        // rodapé: gerenciar contas no launcher
        int fy = rowsY + rowsH + 4;
        Ui.rect(g, x + 8, fy - 2, w - 16, 1, 0x1AFFFFFF);
        boolean mh = inside(mouseX, mouseY, x + 4, fy + 2, w - 8, 18);
        if (mh) {
            Ui.rrect(g, x + 4, fy + 2, w - 8, 18, 0x14FFFFFF, 6);
        }
        g.text(f, "Manage accounts", x + 10, fy + 6, mh ? Ui.TEXT : Ui.DIM, false);

        String hint = notice.isEmpty()
                ? "Switching account restarts the game"
                : notice;
        g.text(AstucyFonts.ui8(), hint, x + 10, fy + 26,
                notice.isEmpty() ? Ui.DIM : 0xFFFFC46B, false);
    }

    private static void avatar(GuiGraphicsExtractor g, Font f, String uuid, String name, int x, int y, int size) {
        int c = AVATAR[Math.abs(uuid.hashCode()) % AVATAR.length];
        Ui.rrect(g, x, y, size, size, c, 4);
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Font sf = AstucyFonts.ui8();
        Ui.ctext(g, sf, initial, x + size / 2, y + (size - sf.lineHeight) / 2 + 1, 0xFF0F1318);
    }

    private static String sourceLabel(String source) {
        return switch (source) {
            case "modrinth" -> "Modrinth App";
            case "official" -> "Official launcher";
            default -> "Microsoft";
        };
    }

    /* ------------------------------------------------------------------ */
    /* interação                                                           */
    /* ------------------------------------------------------------------ */

    /** O cursor do menu (o RShiftScreen atualiza a cada frame). */
    private static double cursorX = -1;
    private static double cursorY = -1;

    public static void cursor(double x, double y) {
        cursorX = x;
        cursorY = y;
    }

    public static void toggle() {
        open = !open;
        notice = "";
        if (open) {
            list = Accounts.list();
        }
    }

    public static void close() {
        open = false;
    }

    /** true = o clique foi consumido pelo menu. */
    public static boolean click(int px, int py, int button) {
        if (button != 0) {
            return open;
        }
        if (inside(px, py, chipX, chipY, chipW, chipH)) {
            toggle();
            return true;
        }
        if (!open) {
            return false;
        }
        int idx = rowAt(px, py);
        if (idx >= 0 && idx < list.size()) {
            Accounts.Acc a = list.get(idx);
            if (a.uuid().equals(Accounts.currentUuid())) {
                open = false;
                return true;
            }
            String err = Accounts.switchTo(a.uuid());
            if (err.isEmpty()) {
                notice = "Switching to " + a.name() + "…";
                open = false;
                quitRequested = true;
            } else {
                notice = err;
            }
            return true;
        }
        // "Manage accounts"
        int fy = rowsY + rowsH + 6;
        if (inside(px, py, panelX + 4, fy, PANEL_W - 8, 18)) {
            String err = Accounts.openLauncher();
            notice = err.isEmpty() ? "Opening launcher…" : err;
            open = false;
            return true;
        }
        open = false;
        return true;
    }

    /** true = o ESC foi consumido (só fechou o dropdown). */
    public static boolean key(int key) {
        if (open && key == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
            return true;
        }
        return false;
    }

    private static int rowAt(int px, int py) {
        if (!open || py < rowsY || py >= rowsY + rowsH) {
            return -1;
        }
        if (px < panelX + 4 || px >= panelX + PANEL_W - 4) {
            return -1;
        }
        return (py - rowsY) / ROW_H;
    }

    /** Chamado todo tick: fecha o jogo quando o launcher já foi avisado. */
    public static void tick(Minecraft mc) {
        if (quitRequested) {
            quitRequested = false;
            open = false;
            mc.stop();
        }
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
