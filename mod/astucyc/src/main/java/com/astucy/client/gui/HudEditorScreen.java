package com.astucy.client.gui;

import com.astucy.client.config.AstucyConfig;
import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import com.astucy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Editor de HUD do Astucy: arrasta os chips pela tela com o estilo
 * real de cada módulo (escala, cores, fundo). Grade de apoio e
 * alinhamento por setas. Âncoras normalizadas 0..1. Salva ao fechar.
 */
public class HudEditorScreen extends Screen {

    private Module sel;
    private boolean dragging = false;
    private boolean resizing = false;
    private boolean resizeTop = false;
    private double rsH0 = 1, rsScale0 = 1, rsScaleY0 = 1, rsMouse0 = 0;
    private double mx = -999, my = -999;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor — Astucy"));
    }

    /** Só o que está LIGADO — o editor mostra exatamente o HUD que você vê. */
    private List<Module> huds() {
        return Modules.all().stream().filter(m -> m.hud && m.enabled).toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        AstucyConfig.save();
        super.onClose();
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
        applyDragTo(x, y);
    }

    /** O Minecraft manda "dragged" (e não "moved") enquanto o botão está preso. */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        applyDragTo(event.x(), event.y());
        return true;
    }

    /** Move o chip ou redimensiona pela alça, conforme o estado atual. */
    private void applyDragTo(double x, double y) {
        if (resizing && sel != null) {
            // arrastar a alça muda TUDO em proporção: largura e altura andam
            // juntas (antes dava pra achatar o texto arrastando só um eixo)
            double d = (y - rsMouse0) * (resizeTop ? -1.0 : 1.0);
            double f = 1.0 + d / Math.max(16.0, rsH0);
            double fMin = Math.max(0.6 / rsScale0, 0.6 / rsScaleY0);
            double fMax = Math.min(2.0 / rsScale0, 3.0 / rsScaleY0);
            f = Math.max(fMin, Math.min(fMax, f));
            sel.scale = Math.round(rsScale0 * f * 100.0) / 100.0;
            sel.scaleY = Math.round(rsScaleY0 * f * 100.0) / 100.0;
        }
        if (dragging && sel != null && mx > -900 && width > 0 && height > 0) {
            double nx = clamp01(sel.anchorX + (x - mx) / width);
            double ny = clamp01(sel.anchorY + (y - my) / height);
            boolean shift = false;
            try {
                long win = Minecraft.getInstance().getWindow().handle();
                shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            } catch (Throwable ignored) {
            }
            if (!shift) {
                Font hud = com.astucy.client.AstucyFonts.ui();
                int[] sz = Modules.chipSize(hud, sel, previewLabel(sel));
                int offX = Modules.drawOffsetX(sel, sz[0]);
                double centerPxX = nx * width + offX + sz[0] / 2.0;
                double centerPxY = ny * height + sz[1] / 2.0;
                if (Math.abs(centerPxX - width / 2.0) <= 4.0) {
                    nx = clamp01((width / 2.0 - offX - sz[0] / 2.0) / width);
                }
                if (Math.abs(centerPxY - height / 2.0) <= 4.0) {
                    ny = clamp01((height / 2.0 - sz[1] / 2.0) / height);
                }
            }
            sel.anchorX = nx;
            sel.anchorY = ny;
        }
        this.mx = x;
        this.my = y;
    }

    /** Multiplica a escala mantendo a proporção entre os dois eixos. */
    private static void applyScale(Module m, double f) {
        double fMin = Math.max(0.6 / m.scale, 0.6 / m.scaleY);
        double fMax = Math.min(2.0 / m.scale, 3.0 / m.scaleY);
        double k = Math.max(fMin, Math.min(fMax, f));
        m.scale = Math.round(m.scale * k * 100.0) / 100.0;
        m.scaleY = Math.round(m.scaleY * k * 100.0) / 100.0;
    }

    /** Modulo sob o cursor (o de cima tem prioridade). */
    private Module chipAt(Font font, double x, double y) {
        List<Module> list = huds();
        for (int i = list.size() - 1; i >= 0; i--) {
            Module m = list.get(i);
            int[] r = hitBox(font, m);
            if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) {
                return m;
            }
        }
        return null;
    }

    /** Engrenagenzinha ao lado do chip: abre as opcoes do modulo. */
    private void drawGear(GuiGraphicsExtractor g, int x, int y) {
        int s = 14;
        boolean h = mx >= x && mx < x + s && my >= y && my < y + s;
        Ui.rrect(g, x, y, s, s, h ? 0xE6131A22 : 0xCC0C1117);
        Ui.rframe(g, x, y, s, s, h ? 0x66FFFFFF : 0x24FFFFFF);
        Ui.icon(g, "options", x + 2, y + 2, 10, h ? 0xFFFFFFFF : 0xB3FFFFFF);
    }

    /**
     * Engrenagem sob o cursor (testa todas, não só o chip hover).
     * A engrenagem é desenhada fora do chip, então olhar só o chip fazia ela
     * sumir no meio do caminho até o clique.
     */
    private Module gearHit(Font font) {
        for (Module m : huds()) {
            int[] r = hitBox(font, m);
            int gx = r[2] + 4;
            int gy = r[1];
            if (mx >= gx - 4 && mx < gx + 18 && my >= gy - 4 && my < gy + 18) {
                return m;
            }
        }
        return null;
    }

    /** Roda do mouse sobre um chip muda a escala na hora (SHIFT = só a altura). */
    @Override
    public boolean mouseScrolled(double x, double y, double dX, double dY) {
        Font font = com.astucy.client.AstucyFonts.ui();
        Module m = chipAt(font, x, y);
        if (m != null) {
            sel = m;
        } else {
            m = sel;
        }
        if (m == null) {
            return super.mouseScrolled(x, y, dX, dY);
        }
        // roda = escala proporcional (os dois eixos juntos)
        applyScale(m, 1.0 + dY * 0.05);
        AstucyConfig.save();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        this.mx = event.x();
        this.my = event.y();
        Font font = com.astucy.client.AstucyFonts.ui();
        Minecraft mc = Minecraft.getInstance();
        // botão do rodapé → volta pro menu
        int bx = this.width / 2 - 65;
        int by = this.height - 26;
        if (mx >= bx && mx < bx + 130 && my >= by && my < by + 18) {
            AstucyConfig.save();
            Minecraft.getInstance().gui.setScreen(new RShiftScreen());
            return true;
        }
        // alças de redimensionar (pílula no topo e embaixo do selecionado)
        Font hud = com.astucy.client.AstucyFonts.ui();
        if (sel != null) {
            int[] box = hitBox(hud, sel);
            int[][] hs = resizeHandles(box);
            for (int i = 0; i < hs.length; i++) {
                int[] hit = {hs[i][0] - 2, hs[i][1] - 3, hs[i][2] + 4, hs[i][3] + 6};
                if (inside(mx, my, hit)) {
                    resizeTop = i == 0;
                    rsH0 = Math.max(8.0, box[3] - box[1]);
                    rsScale0 = sel.scale;
                    rsScaleY0 = sel.scaleY;
                    rsMouse0 = my;
                    resizing = true;
                    dragging = false;
                    return true;
                }
            }
        }
        // painel de escala do selecionado (SIZE / HEIGHT)
        if (clickScalePanel()) {
            return true;
        }
        // engrenagem do chip sob o cursor (ou da engrenagem em si) -> opcoes do modulo
        Module gearOwner = gearHit(font);
        if (gearOwner != null) {
            int[] ur = hitBox(font, gearOwner);
            int gx = ur[2] + 4;
            int gy = ur[1];
            if (mx >= gx - 3 && mx < gx + 17 && my >= gy - 3 && my < gy + 17) {
                AstucyConfig.save();
                Minecraft.getInstance().gui.setScreen(new RShiftScreen(gearOwner));
                return true;
            }
        }
        // seleciona o chip clicado (o último da lista tem prioridade = desenhado por cima)
        List<Module> list = huds();
        for (int i = list.size() - 1; i >= 0; i--) {
            Module m = list.get(i);
            int[] r = hitBox(font, m);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                sel = m;
                dragging = true;
                return true;
            }
        }
        dragging = false;
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (resizing) {
            AstucyConfig.save();
        }
        resizing = false;
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_TAB) {
            AstucyConfig.save();
            Minecraft.getInstance().gui.setScreen(new RShiftScreen());
            return true;
        }
        List<Module> list = huds();
        if (list.isEmpty()) {
            return super.keyPressed(event);
        }
        if (key == GLFW.GLFW_KEY_UP) {
            sel = list.get(Math.floorMod(index(sel, list) - 1, list.size()));
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            sel = list.get(Math.floorMod(index(sel, list) + 1, list.size()));
            return true;
        }
        if (sel == null) {
            sel = list.get(0);
            return true;
        }
        double step = event.modifiers() != 0 ? 0.002 : 0.01; // SHIFT = passo fino
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> sel.anchorX = clamp01(sel.anchorX - step);
            case GLFW.GLFW_KEY_RIGHT -> sel.anchorX = clamp01(sel.anchorX + step);
            case GLFW.GLFW_KEY_PAGE_UP -> sel.anchorY = clamp01(sel.anchorY - step);
            case GLFW.GLFW_KEY_PAGE_DOWN -> sel.anchorY = clamp01(sel.anchorY + step);
            case GLFW.GLFW_KEY_ENTER -> sel.enabled = !sel.enabled;
            case GLFW.GLFW_KEY_DELETE -> sel.resetStyle();
            default -> {
                return super.keyPressed(event);
            }
        }
        AstucyConfig.save();
        return true;
    }

    private static int index(Module m, List<Module> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == m) {
                return i;
            }
        }
        return 0;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Botão discreto de saída, no rodapé (o topo fica limpo). */
    private void drawExit(GuiGraphicsExtractor g, Font font, int gw, int gh) {
        int bx = gw / 2 - 65;
        int by = gh - 26;
        boolean hover = mx >= bx && mx < bx + 130 && my >= by && my < by + 18;
        Ui.button(g, font, bx, by, 130, 18, "BACK TO MENU", false, hover);
    }

    /* ---- escala do módulo selecionado (visível, sem precisar de roda) ---- */

    private static final int BTN = 16;

    /** Painel de escala: [SIZE − 100% + ] [RESET]. Sempre proporcional. */
    private int[] scalePanel() {
        int w = 236;
        int h = 24;
        return new int[]{-1000, -1000, 0, 0};
    }

    /** 0 = −, 1 = +, 2 = RESET. */
    private int[] scaleBtn(int i) {
        int[] p = scalePanel();
        int y = p[1] + 4;
        return switch (i) {
            case 0 -> new int[]{p[0] + 46, y, BTN, BTN};
            case 1 -> new int[]{p[0] + 100, y, BTN, BTN};
            default -> new int[]{p[0] + 126, y, 96, BTN};
        };
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    /** Alças de redimensionar: pílula centrada no topo e embaixo do chip. */
    private int[][] resizeHandles(int[] box) {
        int w = Math.max(20, Math.min(38, box[2] - box[0] - 10));
        int h = 5;
        int cxc = (box[0] + box[2]) / 2;
        return new int[][]{
                {cxc - w / 2, box[1] - h - 2, w, h},
                {cxc - w / 2, box[3] + 4, w, h}
        };
    }

    /** Alça em pílula: fio de luz no meio e inverte de cor no hover. */
    private void drawResizeHandle(GuiGraphicsExtractor g, int[] h, boolean hover) {
        Ui.rrect(g, h[0] - 1, h[1] - 1, h[2] + 2, h[3] + 2,
                hover ? 0x38FFFFFF : 0x22000000, (h[3] + 2) / 2 + 1);
        Ui.rrect(g, h[0], h[1], h[2], h[3], hover ? 0xFFFFFFFF : 0xD90C1117, h[3] / 2 + 1);
        Ui.rect(g, h[0] + 3, h[1] + h[3] / 2, h[2] - 6, 1, hover ? 0xB30C1117 : 0x99FFFFFF);
    }

    private void drawScalePanel(GuiGraphicsExtractor g, Font font) {
        if (sel == null) {
            return;
        }
        int[] p = scalePanel();
        Ui.rrect(g, p[0], p[1], p[2], p[3], 0xCC0B1017, 6);
        Ui.rframe(g, p[0], p[1], p[2], p[3], 0x24FFFFFF);
        g.text(font, "SIZE", p[0] + 10, p[1] + 8, Ui.DIM, false);
        g.text(font, Math.round(sel.scale * 100) + "%", p[0] + 70, p[1] + 8, Ui.TEXT, false);
        for (int i = 0; i < 3; i++) {
            int[] b = scaleBtn(i);
            boolean hov = inside(mx, my, b);
            Ui.rrect(g, b[0], b[1], b[2], b[3], hov ? 0x3AFFFFFF : 0x1AFFFFFF, 5);
            if (i == 2) {
                Ui.ctext(g, font, "RESET", b[0] + b[2] / 2, b[1] + (BTN - font.lineHeight) / 2 + 1,
                        hov ? Ui.WHITE : Ui.DIM);
            } else {
                Ui.ctext(g, font, i == 0 ? "-" : "+", b[0] + BTN / 2,
                        b[1] + (BTN - font.lineHeight) / 2 + 1, Ui.TEXT);
            }
        }
    }

    private boolean clickScalePanel() {
        if (sel == null) {
            return false;
        }
        if (inside(mx, my, scaleBtn(0))) {
            applyScale(sel, 1 / 1.05);
        } else if (inside(mx, my, scaleBtn(1))) {
            applyScale(sel, 1.05);
        } else if (inside(mx, my, scaleBtn(2))) {
            sel.scale = 1.0;
            sel.scaleY = 1.0;
        } else {
            return false;
        }
        AstucyConfig.save();
        return true;
    }

    private int[] hitBox(Font font, Module m) {
        String label = previewLabel(m);
        int[] s = Modules.chipSize(font, m, label);
        if (s[0] <= 0 || s[1] <= 0) {
            // armadura/efeitos vazios ainda precisam de uma caixa para arrastar
            s = new int[]{font.width(m.name) + 7, font.lineHeight + 4};
        }
        int x = (int) Math.round(m.anchorX * width) + Modules.drawOffsetX(m, s[0]);
        int y = (int) Math.round(m.anchorY * height);
        return new int[]{x, y, x + s[0] + 2, y + s[1] + 2};
    }

    private String previewLabel(Module m) {
        Minecraft mc = Minecraft.getInstance();
        String t = Modules.chipText(m, mc);
        return t != null ? t : m.name;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        // prévia "cheia": armadura de dima completa e efeitos de exemplo
        Modules.preview = true;
        Font font = com.astucy.client.AstucyFonts.ui();
        Minecraft mc = Minecraft.getInstance();
        Font hud = com.astucy.client.AstucyFonts.ui();
        int gw = g.guiWidth();
        int gh = g.guiHeight();

        // fundo: blur leve do mundo, sem grade e sem texto no topo
        try {
            g.blurBeforeThisStratum();
        } catch (IllegalStateException alreadyBlurred) {
            // o proprio Screen ja aplicou o blur neste frame: nada a fazer
        }
        Ui.rect(g, 0, 0, gw, gh, 0x55000000);

        // chips no estilo real
        List<Module> list = huds();
        if (list.isEmpty()) {
            Ui.ctext(g, font, "no modules enabled", gw / 2, gh / 2, Ui.DIM);
            Ui.ctext(g, font, "turn modules on in the menu first", gw / 2, gh / 2 + 12, 0x66E7EBF0);
            drawExit(g, font, gw, gh);
            return;
        }
        for (Module m : list) {
            int x = (int) Math.round(m.anchorX * gw);
            int y = (int) Math.round(m.anchorY * gh);
            if (!Modules.hasContent(m)) {
                // armadura/efeitos sem nada equipado: chip fantasma para posicionar
                int[] s = Modules.chipSize(hud, m, previewLabel(m));
                int w = s[0] > 0 ? s[0] : hud.width(m.name) + 7;
                int h = s[1] > 0 ? s[1] : hud.lineHeight + 4;
                Ui.rrect(g, x, y, w, h, 0x2EFFFFFF);
                g.text(hud, m.name, x + 4, y + 2, 0x99E7EBF0, false);
            } else {
                // mesmo desenho do jogo — zero discrepância entre editor e HUD
                Modules.drawHud(g, hud, m, x, y);
            }
        }

        // guias de alinhamento enquanto arrasta (centro da tela + centro do chip)
        if (dragging && sel != null) {
            int[] sz = Modules.chipSize(hud, sel, previewLabel(sel));
            int offX = Modules.drawOffsetX(sel, sz[0]);
            int ccx = (int) Math.round((sel.anchorX + (offX + sz[0] / 2.0) / gw) * gw);
            int ccy = (int) Math.round((sel.anchorY + sz[1] / 2.0 / gh) * gh);
            Ui.rect(g, gw / 2, 0, 1, gh, 0x2E6FE3A2);
            Ui.rect(g, 0, gh / 2, gw, 1, 0x2E6FE3A2);
            Ui.rect(g, ccx, 0, 1, gh, 0x7A6FE3A2);
            Ui.rect(g, 0, ccy, gw, 1, 0x7A6FE3A2);
        }

        drawExit(g, font, gw, gh);


        // engrenagem: chip sob o cursor, senão a engrenagem em si, senão o selecionado
        Module gearM = gearHit(hud);
        if (gearM == null) {
            gearM = chipAt(hud, mx, my);
        }
        if (gearM == null) {
            gearM = sel;
        }
        if (gearM != null) {
            int[] hr = hitBox(hud, gearM);
            drawGear(g, hr[2] + 4, hr[1]);
        }

        if (sel != null) {
            int[] r = hitBox(hud, sel);
            Ui.rframe(g, r[0] - 2, r[1] - 2, r[2] - r[0] + 4, r[3] - r[1] + 4, 0xCC38C975, 6);
            for (int[] hs : resizeHandles(r)) {
                boolean hov = mx >= hs[0] - 2 && mx < hs[0] + hs[2] + 2
                        && my >= hs[1] - 3 && my < hs[1] + hs[3] + 3;
                drawResizeHandle(g, hs, hov);
            }
            boolean sameRatio = Math.abs(sel.scaleY - sel.scale) <= 0.011;
            String badge = sel.name + "  " + Math.round(sel.scale * 100) + "%"
                    + (sameRatio ? "" : " · H " + Math.round(sel.scaleY * 100) + "%");
            int bw = font.width(badge) + 10;
            int bx = r[0] - 2;
            int by = Math.max(4, r[1] - 19);
            Ui.rrect(g, bx, by, bw, 14, 0xCC0A0D12, 5);
            g.text(font, badge, bx + 5, by + 3, Ui.GREEN, false);
        }
    }
}
