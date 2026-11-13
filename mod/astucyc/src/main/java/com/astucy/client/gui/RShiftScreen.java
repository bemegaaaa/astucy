package com.astucy.client.gui;

import com.astucy.client.AstucyFonts;
import com.astucy.client.config.AstucyConfig;
import com.astucy.client.module.Module;
import com.astucy.client.module.ModuleCategory;
import com.astucy.client.module.Modules;
import com.astucy.client.ui.Skin;
import com.astucy.client.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menu R-SHIFT do Astucy — inspirado no layout dos clients premium:
 * painel escuro sobre o mundo desfocado pelo jogo, busca no topo, pills
 * de categoria com quebra dinâmica, sidebar de perfis, cards grandes
 * (ícone + OPÇÕES + barra de estado "ATIVADO"/"DESLIGADO") e scrollbar.
 */
public class RShiftScreen extends Screen {

    private static final String[] PROFILES = {"Profile 1", "Profile 2", "Profile 3"};
    private static final String[] OPT_ROWS = {
        "Scale", "Background", "Opacity", "Background color", "Text color", "Shadow", "Prefix", "Position", "Clean font", "Custom label", "Height"
    };
    private static final String[] OPT_TOOLTIPS = {
        "Chip size on the HUD",
        "Background behind the text",
        "Background transparency",
        "Chip background color",
        "Chip text color",
        "Soft shadow behind the text",
        "Shows the label before the value",
        "Drag the chip in the editor",
        "Smooth anti-aliased Montserrat instead of pixel font",
        "Text shown before the value (e.g. SPRINT)",
        "Chip height only (SHIFT + wheel in the editor)"
    };


    /** Linha de comportamento (não estilo): um toggle ou um slider numérico. */
    private record Row(String label, String tip, String prop, boolean toggle,
                       double min, double max, double step, double def) {}

    private static final Row[] ZOOM_ROWS = {
        new Row("Toggle mode", "Press C once to toggle the zoom", "toggle", true, 0, 0, 0, 0),
        new Row("Smooth transition", "Animates the zoom instead of snapping", "smooth", true, 0, 0, 0, 1),
        new Row("Zoom level", "How much the zoom magnifies", "level", false, 0.1, 1.0, 0.025, 0.4)
    };
    private static final Row[] CHAT_ROWS = {
            new Row("Infinite history", "Keep every message (4000 cap)", "chat_history", true, 0, 0, 0, 1),
            new Row("Chat search", "Filter with /chs <text>", "chat_search", true, 0, 0, 0, 1),
    };

    private static final Row[] BLUR_ROWS = {
        new Row("Strength", "Base motion blur intensity", "strength", false, 0.2, 3.0, 0.05, 1.25),
        new Row("High-speed boost", "Amplifies blur dynamically on fast flicks & sprinting", "speed_boost", true, 0, 0, 0, 1),
        new Row("Hybrid blending", "Combines velocity vectors with frame blending", "hybrid", true, 0, 0, 0, 1),
        new Row("Weighted curve", "Smooth Gaussian-weighted trails without ghosting", "weighted", true, 0, 0, 0, 1)
    };
    private static final Row[] SPEEDFX_ROWS = {
        new Row("Intensity", "Strength of peripheral velocity effects", "intensity", false, 0.2, 2.0, 0.05, 1.0),
        new Row("Dynamic FOV rush", "Subtle FOV acceleration at high speeds", "fov_rush", true, 0, 0, 0, 1)
    };
    private static final Row[] AMBIENCE_ROWS = {
        new Row("Tint intensity", "Atmospheric screen color grading opacity", "strength", false, 0.04, 0.35, 0.01, 0.14),
        new Row("Cyber Emerald", "Use signature emerald tint instead of custom color", "emerald", true, 0, 0, 0, 1)
    };
    private static final Row[] MENUBLUR_ROWS = {
        new Row("Cursor spotlight", "Interactive radial light following the cursor", "spotlight", true, 0, 0, 0, 1),
        new Row("Ambient aura", "Atmospheric emerald & cyan lighting inside menus", "aura", true, 0, 0, 0, 1)
    };
    private static final Row[] FPS_ROWS = {
        new Row("Average", "Average of the last second instead of the instant value", "avg", true, 0, 0, 0, 0),
        new Row("Min / max", "Show the lowest and highest FPS of the last second", "minmax", true, 0, 0, 0, 0)
    };
    private static final Row[] PING_ROWS = {
        new Row("Unit", "Append \"ms\" after the number", "ms", true, 0, 0, 0, 1)
    };
    private static final Row[] MEMORY_ROWS = {
        new Row("Percent", "Show used / max and the percentage", "percent", true, 0, 0, 0, 0)
    };
    private static final Row[] CPS_ROWS = {
        new Row("Split left / right", "Show left and right clicks separately", "split", true, 0, 0, 0, 1),
        new Row("Brackets", "Wrap the value in [ ]", "brackets", true, 0, 0, 0, 0),
        new Row("Right clicks first", "Right|Left instead of Left|Right", "reverse", true, 0, 0, 0, 0)
    };
    private static final Row[] KEYSTROKE_ROWS = {
        new Row("Mouse buttons", "Show LMB / RMB", "mouse", true, 0, 0, 0, 1),
        new Row("Space bar", "Show the space bar", "space", true, 0, 0, 0, 1),
        new Row("Arrow keys", "Label the keys with arrows instead of WASD", "arrows", true, 0, 0, 0, 0),
        new Row("CPS on mouse keys", "Click speed inside LMB / RMB", "keycps", true, 0, 0, 0, 0),
        new Row("Key size", "Box size in pixels", "box", false, 12, 32, 2, 18)
    };
    private static final Row[] COORD_ROWS = {
        new Row("Facing", "Show the direction after the coords", "facing", true, 0, 0, 0, 1),
        new Row("Decimals", "Show decimals instead of whole blocks", "decimal", true, 0, 0, 0, 0),
        new Row("Decimal places", "Digits after the point", "decimals", false, 0, 4, 1, 1),
        new Row("Nether coords", "Show the overworld equivalent in the Nether", "nether", true, 0, 0, 0, 0)
    };
    private static final Row[] ARMOR_ROWS = {
        new Row("Held item", "Item in the main hand on top", "hand", true, 0, 0, 0, 1),
        new Row("Off hand", "Item in the off hand", "offhand", true, 0, 0, 0, 0),
        new Row("Item count", "Stack size for items without durability", "count", true, 0, 0, 0, 1),
        new Row("Max durability", "Show 512/512 instead of 512", "maxdur", true, 0, 0, 0, 0),
        new Row("Numbers on the left", "Durability before the icon", "durleft", true, 0, 0, 0, 1),
        new Row("Damage colors", "Green to red as durability drops", "dmgcolor", true, 0, 0, 0, 1),
        new Row("Low durability alert", "Blink pieces about to break", "lowwarn", true, 0, 0, 0, 1),
        new Row("Alert threshold", "Remaining durability % that triggers the alert", "lowpct", false, 5, 50, 5, 20)
    };
    private static final Row[] EFFECT_ROWS = {
        new Row("Effect name", "Name of the effect", "name", true, 0, 0, 0, 1),
        new Row("Duration", "Time left", "duration", true, 0, 0, 0, 1),
        new Row("Amplifier", "Level as a number (2 instead of II)", "amplifier", true, 0, 0, 0, 1),
        new Row("Uppercase names", "SPEED instead of Speed", "uppercase", true, 0, 0, 0, 0),
        new Row("Icons", "Draw the effect icon", "icons", true, 0, 0, 0, 1),
        new Row("Blink when low", "Blink effects about to expire", "blink", true, 0, 0, 0, 1),
        new Row("Hide ambient", "Skip beacon and conduit effects", "hideambient", true, 0, 0, 0, 0),
        new Row("Text after icon", "Text on the right of the icon", "textright", true, 0, 0, 0, 1),
        new Row("Small text", "Smaller name and time", "small", true, 0, 0, 0, 0)
    };
    private static final Row[] VIEWMODEL_ROWS = {
        new Row("Offset X", "Hand to the left / right", "vmx", false, -0.6, 0.6, 0.02, 0),
        new Row("Offset Y", "Hand up / down", "vmy", false, -0.6, 0.6, 0.02, 0),
        new Row("Offset Z", "Hand forward / back", "vmz", false, -0.6, 0.6, 0.02, 0),
        new Row("Scale", "Hand size", "vmscale", false, 0.5, 1.5, 0.05, 1)
    };
    private static final Row[] CROSSHAIR_ROWS = {
        new Row("Center dot", "Draw a crisp anti-aliased center dot", "dot", true, 0, 0, 0, 1),
        new Row("Cross lines", "Draw the 4 crosshair lines", "cross", true, 0, 0, 0, 1),
        new Row("Outer ring", "Draw a subtle circular ring around the crosshair", "ring", true, 0, 0, 0, 0),
        new Row("Dynamic spread", "Expand smoothly when attacking, jumping or sprinting", "dynamic", true, 0, 0, 0, 1),
        new Row("Target highlight", "Turn red when aiming at a living entity", "hitcolor", true, 0, 0, 0, 1),
        new Row("Line length", "Length of each crosshair arm in pixels", "size", false, 2, 12, 1, 4),
        new Row("Center gap", "Gap from center to lines in pixels", "gap", false, 1, 10, 1, 3)
    };

    // animações contínuas (dt-based — suaves em qualquer FPS)
    private boolean profEdit;
    private int profEditIdx;
    private String profBuf = "";
    private int colorEdit = -1;
    private float hueSel = 0.33f, satSel = 0.9f, valSel = 1f;
    private static final int[] PALETTE = new int[24];

    static {
        for (int k = 0; k < 12; k++) {
            PALETTE[k] = rgbHsv(k / 12f, 0.85f, 1f);
        }
        for (int k = 0; k < 12; k++) {
            PALETTE[12 + k] = rgbHsv(k / 12f, 0.75f, 0.66f);
        }
    }

    private static int rgbHsv(float h, float sat, float v) {
        float f = h * 6f;
        int i = (int) f;
        f -= i;
        float p = v * (1f - sat);
        float q = v * (1f - f * sat);
        float t = v * (1f - (1f - f) * sat);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
             case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (((int) (r * 255) & 0xFF) << 16) | (((int) (g * 255) & 0xFF) << 8) | ((int) (b * 255) & 0xFF);
    }

    private int tabEndX;
    private final java.util.HashMap<String, Float> anims = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> press = new java.util.HashMap<>();
    private long stamp = System.currentTimeMillis();
    private float dt = 0.016f;
    private long lastScroll = 0L;
    private int optShift = 0;
    // abertura do menu + scroll suave (float: o movimento anda em decimal,
    // não em pixels inteiros — era o que fazia o scroll "pular")
    private final long openedAt = System.currentTimeMillis();
    private boolean closing = false;
    private long closedAt = 0L;
    private float spotX = -1f, spotY = -1f;
    private long catStamp = System.currentTimeMillis();
    private long savedStamp = 0L;
    private float scrollTarget = 0;

    // barra de estado do card (cores da Ui)

    private boolean modsTab = false;
    private Object filter = null;   // null = TODOS, "novos" = novos, ModuleCategory = categoria
    private String query = "";
    private int sel = 0;
    private float scrollY = 0;
    private float optScroll = 0;
    private float optScrollTarget = 0;
    private Module option = null;
    private int optSel = 0;
    private int drag = 0;
    private double dragGrab = 0;
    // arrasto de slider: anda pelo DELTA do mouse desde o clique (nunca "volta")
    private double dragX0 = 0;
    private double dragFrac0 = 0;
    private int profile = 0;
    private double mx = -999, my = -999;

    // campo de busca próprio (sem EditBox: sem sombra no texto, visual limpo)
    private boolean labelEdit = false;
    private String labelBuf = "";
    private boolean searchFocus = false;
    private String searchText = "";
    private int caret = 0;

    // pills calculadas com quebra de linha (compartilhadas entre input e draw)
    private final List<Object> pillCats = new ArrayList<>();
    private final List<int[]> pillRects = new ArrayList<>();

    // lista filtrada em cache (não recria a cada frame)
    private String fkey = null;
    private List<Module> fcache = List.of();

    private int gw, gh, x0, y0, pw, ph, topH, sideW;
    private int cx, cy, cw, ch, cols, cardW, cardH, gridY;

    public RShiftScreen() {
        super(Component.literal("Astucy"));
    }

    /** Abre já nas opções de um módulo (engrenagem do editor de HUD). */
    public RShiftScreen(Module open) {
        this();
        this.option = open;
        this.optSel = 0;
    }

    /**
     * Botão esquerdo pressionado? Vem do GLFW, porque o
     * `MouseHandler.isLeftPressed()` NÃO é atualizado enquanto um Screen está
     * aberto — o Minecraft só grava esse campo com o jogo em jogo; com menu
     * aberto ele fica travado no último valor (quase sempre false). Era isso
     * que matava o arrasto do slider no frame seguinte ao clique.
     */
    private static boolean leftDown() {
        try {
            return GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().handle(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void init() {
        super.init();
        layout(AstucyFonts.menu());
    }

    private int[] searchBox() {
        int right = cx + cw - 10;
        int pillsRight = cx;
        for (int[] r : pillRects) {
            if (r[1] == cy - 2) {
                pillsRight = Math.max(pillsRight, r[0] + r[2]);
            }
        }
        int avail = right - (pillsRight + 8);
        if (avail >= 64) {
            int w = Math.min(136, avail);
            return new int[]{right - w, cy - 2, w, 18};
        }
        int chipLeft = AccountMenu.chipX();
        if (chipLeft <= 0) {
            chipLeft = x0 + pw - 26 - 8 - AccountMenu.chipWidth(AstucyFonts.menu());
        }
        int topRight = chipLeft - 8;
        int topLeft = Math.max(cx + 120, tabEndX);
        int w = Math.max(0, Math.min(160, topRight - topLeft));
        if (topRight - topLeft < 52) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{topLeft, y0 + (topH - 18) / 2, w, 18};
    }

    /** Aplica o texto digitado ao filtro (minúsculo, sem espaços nas pontas). */
    private void applyQuery() {
        query = searchText.trim().toLowerCase();
        sel = 0;
        scrollY = 0;
        scrollTarget = 0;
    }

    /** Insere o texto digitado na posição do cursor. */
    private void typeChar(String s) {
        if (searchText.length() >= 24) {
            return;
        }
        searchText = searchText.substring(0, caret) + s + searchText.substring(caret);
        caret += s.length();
        applyQuery();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closing && AstucyConfig.prefBool("anim", true)) {
            closing = true;
            closedAt = System.currentTimeMillis();
            return;
        }
        AstucyConfig.save();
        super.onClose();
    }

    private void finishClose() {
        AstucyConfig.save();
        super.onClose();
    }

    /* ------------------------------------------------------------------
     * geometria
     * ------------------------------------------------------------------ */

    private void layout(Font f) {
        gw = width;
        gh = height;
        pw = clamp((int) (gw * 0.84), 500, 900);
        ph = clamp((int) (gh * 0.84), 285, 620);
        pw = Math.min(pw, gw - 16);
        ph = Math.min(ph, gh - 16);
        // animação de abertura e fechamento suave (spring vertical + fade)
        long nowOpen = System.currentTimeMillis();
        float openRaw = Math.min(1f, (nowOpen - openedAt) / 240f);
        float openEase = 1f - (float) Math.pow(1f - openRaw, 3.5);
        if (closing) {
            float closeRaw = Math.min(1f, (nowOpen - closedAt) / 130f);
            openEase = 1f - closeRaw * closeRaw;
        }
        int slidePanelY = AstucyConfig.prefBool("anim", true) ? Math.round((1f - openEase) * 12f) : 0;

        x0 = (gw - pw) / 2;
        y0 = (gh - ph) / 2 + slidePanelY;
        topH = f.lineHeight + 20;
        sideW = clamp(pw / 6, 108, 144);
        cx = x0 + sideW + 12;
        cy = y0 + topH + 10;
        cw = pw - sideW - 22;
        ch = ph - topH - 22;
        cols = 2;
        int gridW = cw - 10; // reserva 10px à direita para a scrollbar não cobrir os cards
        cardW = (gridW - (cols - 1) * 8) / cols;
        cardH = 38;

        layoutPills(f);
        gridY = pillBottom() + 8;
    }

    private static String pillLabelText(Object key) {
        return key == null ? "ALL"
                : key instanceof String ? "NEW" : ((ModuleCategory) key).label().toUpperCase();
    }

    private void layoutPills(Font f) {
        pillCats.clear();
        pillRects.clear();
        pillCats.add(null);
        pillCats.add("novos");
        int rx = cx;
        int ry = cy - 2;
        int[] fixed = {
            f.width(pillLabelText(null)) + 12,
            f.width(pillLabelText("novos")) + 12
        };
        for (int i = 0; i < 2; i++) {
            pillRects.add(new int[]{rx, ry, fixed[i], 18});
            rx += fixed[i] + 4;
        }
        for (ModuleCategory c : ModuleCategory.values()) {
            int w = f.width(pillLabelText(c)) + 12;
            pillCats.add(c);
            if (rx > cx && rx + w > cx + cw - 4) {
                rx = cx;
                ry += 20;
            }
            pillRects.add(new int[]{rx, ry, w, 18});
            rx += w + 4;
        }
    }

    private int pillBottom() {
        int last = 0;
        for (int[] r : pillRects) {
            last = Math.max(last, r[1] + r[3]);
        }
        return last;
    }

    private int maxScroll() {
        int count = filtered().size();
        int rows = (count + cols - 1) / cols;
        int needed = rows * (cardH + 7);
        return Math.max(0, needed - (cy + ch - gridY));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private List<Module> filtered() {
        // cache por frame: evita recriar a lista (e lowercases) várias vezes por frame
        String key = (filter == null ? "-" : filter.toString()) + "|" + query;
        if (key.equals(fkey)) {
            return fcache;
        }
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.all()) {
            boolean okCat = filter == null
                    || (filter instanceof String ? m.isNew : m.category == filter);
            boolean okQ = query.isEmpty()
                    || m.name.toLowerCase().contains(query)
                    || m.id.contains(query)
                    || m.description.toLowerCase().contains(query);
            if (okCat && okQ) {
                out.add(m);
            }
        }
        fkey = key;
        fcache = out;
        return out;
    }

    private int optRowH() {
        // altura fixa (linhas consistentes) — o que não couber rola com scrollbar
        return 20;
    }

    /** Posição do scroll em pixels (o estado é float; arredonda só na hora de desenhar). */
    private int optScrollPx() {
        return Math.round(optScroll);
    }

    private int gridScrollPx() {
        return Math.round(scrollY);
    }

    private int optRowY(int i) {
        return cy + 28 + i * optRowH() + optShift - optScrollPx();
    }

    /** Altura visível da lista de opções (entre cabeçalho e rodapé). */
    private int optViewH() {
        return Math.max(40, (cy + ch - 30) - (cy + 26));
    }

    private int optMaxScroll() {
        return Math.max(0, rowCount() * optRowH() - optViewH());
    }

    /* ---- geometria das scrollbars (compartilhada entre clique e desenho) ---- */

    private int gridTrackY() {
        return gridY;
    }

    private int gridTrackH() {
        return Math.max(1, cy + ch - gridY);
    }

    private int gridThumbH() {
        int trackH = gridTrackH();
        int max = maxScroll();
        return Math.max(22, (int) ((long) trackH * trackH / (trackH + max)));
    }

    private int gridThumbY() {
        int trackH = gridTrackH();
        int thumbH = gridThumbH();
        int max = maxScroll();
        return gridTrackY() + (int) Math.round(scrollY * (trackH - thumbH) / (double) Math.max(1, max));
    }

    private int optTrackY() {
        return cy + 26;
    }

    private int optTrackH() {
        return optViewH();
    }

    private int optThumbH() {
        int trackH = optTrackH();
        int max = optMaxScroll();
        return Math.max(20, (int) ((long) trackH * trackH / (trackH + max)));
    }

    private int optThumbY() {
        int trackH = optTrackH();
        int thumbH = optThumbH();
        int max = optMaxScroll();
        return optTrackY() + (int) Math.round(optScroll * (trackH - thumbH) / (double) Math.max(1, max));
    }

    /** Zona de pega da barra (mais larga que os 3px desenhados). */
    private boolean onGridBar(int px, int py) {
        return maxScroll() > 0 && px >= cx + cw - 18 && px < cx + cw + 8
                && py >= gridTrackY() && py < gridTrackY() + gridTrackH();
    }

    private boolean onOptBar(int px, int py) {
        return optMaxScroll() > 0 && px >= cx + cw - 18 && px < cx + cw + 8
                && py >= optTrackY() && py < optTrackY() + optTrackH();
    }

    /** Passo entre as amostras de cor: encolhe junto com o painel. */
    private int swPitch() {
        int avail = Math.max(120, (cx + cw - 34) - sliderX());
        return Math.max(9, Math.min(16, avail / 16));
    }

    private int swSize() {
        return Math.min(13, swPitch() - 2);
    }

    private int sliderX() {
        return cx + clamp(cw / 3, 96, 138);
    }

    private int sliderW() {
        return cw - (sliderX() - cx) - 52;
    }

    /* ------------------------------------------------------------------
     * input
     * ------------------------------------------------------------------ */

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
        this.mx = x;
        this.my = y;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dX, double dY) {
        // com o botão preso num slider, a roda não mexe a lista (senão a linha
        // foge debaixo do cursor e o arrasto parece travado)
        if (drag == 1 || drag == 2 || drag == 3 || drag == 6 || drag >= 100) {
            return true;
        }
        lastScroll = System.currentTimeMillis();
        if (option != null) {
            optScrollTarget -= (float) (dY * 24);
            optScrollTarget = Math.max(0f, Math.min(optMaxScroll(), optScrollTarget));
            return true;
        }
        float max = maxScroll();
        scrollTarget -= (float) (dY * 28);
        scrollTarget = Math.max(0f, Math.min(max, scrollTarget));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        this.mx = x;
        this.my = y;
        if (drag != 0) {
            applyDrag();
            return true;
        }
        return super.mouseDragged(event, x, y);
    }

    /**
     * Aplica o arrasto a partir da posição atual do mouse. Chamado pelo evento
     * E pelo render (fallback): se o evento de drag não chegar, o slider ainda
     * segue o mouse — era o "não dá pra arrastar".
     */
    private void applyDrag() {
        if (drag == 4) {
            int max = maxScroll();
            int span = Math.max(1, gridTrackH() - gridThumbH());
            double f = Math.max(0, Math.min(1, (my - gridTrackY() - dragGrab) / (double) span));
            scrollTarget = (float) (f * max);
            scrollY = scrollTarget;
            return;
        }
        if (drag == 5) {
            int max = optMaxScroll();
            int span = Math.max(1, optTrackH() - optThumbH());
            double f = Math.max(0, Math.min(1, (my - optTrackY() - dragGrab) / (double) span));
            optScrollTarget = (float) (f * max);
            optScroll = optScrollTarget;
            return;
        }
        if (drag == 7 || drag == 8) {
            if (option == null || colorEdit < 0) {
                return;
            }
            int[] pr = paletteRect();
            int wx = pr[0] + 8;
            int wy = pr[1] + 22;
            int ws = 88;
            if (drag == 7) {
                applyWheel((int) mx, (int) my, wx, wy, ws);
            } else {
                applyBar((int) mx, wx, ws);
            }
            return;
        }
        if (option == null) {
            return;
        }
        if (drag == 1) {
            option.scale = round2(0.6 + dragFrac() * 1.4);
            option.scaleY = Math.max(0.6, Math.min(3.0, option.scale));
            return;
        }
        if (drag == 2) {
            int alpha = (int) Math.round(dragFrac() * 255.0);
            option.bgColor = (clamp(alpha, 0, 255) << 24) | (option.bgColor & 0xFFFFFF);
            return;
        }
        if (drag == 6) {
            option.scaleY = round2(0.6 + dragFrac() * 2.4);
            return;
        }
        if (drag >= 100) {
            Row[] rows = rows();
            int k = drag - 100 - styleRows();
            if (rows != null && k >= 0 && k < rows.length) {
                Row r = rows[k];
                option.props.put(r.prop(), clampNum(snap(r.min() + dragFrac() * (r.max() - r.min()), r.step()),
                        r.min(), r.max()));
            }
        }
    }

    /** Começa um arrasto de slider a partir da fração atual. */
    private void beginDrag(int id, double frac0) {
        drag = id;
        dragX0 = mx;
        dragFrac0 = Math.max(0, Math.min(1, frac0));
    }

    /** Fração do slider: a do começo + o quanto o mouse andou desde então. */
    private double dragFrac() {
        return Math.max(0, Math.min(1, dragFrac0 + (mx - dragX0) / (double) sliderW()));
    }

    /** Fração que um controle já está mostrando (pra começar o arrasto sem pular). */
    private double controlFrac(int id, int row) {
        if (option == null) {
            return 0;
        }
        if (id == 1) {
            return (option.scale - 0.6) / 1.4;
        }
        if (id == 2) {
            return ((option.bgColor >>> 24) & 0xFF) / 255.0;
        }
        if (id == 6) {
            return (option.scaleY - 0.6) / 2.4;
        }
        if (id >= 100) {
            Row r = modRow(row);
            if (r != null) {
                return (rowNum(r) - r.min()) / (r.max() - r.min());
            }
        }
        return 0;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != 0) {
            AstucyConfig.save();
        }
        drag = 0;
        return super.mouseReleased(event);
    }

    private double sliderFrac() {
        return Math.max(0, Math.min(1, (mx - sliderX()) / (double) sliderW()));
    }

    /** Salva o texto do rótulo digitado e sai do modo de edição. */
    private void commitLabel() {
        if (option != null) {
            option.customLabel = labelBuf == null ? "" : labelBuf.trim();
            AstucyConfig.save();
        }
        labelEdit = false;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double snap(double v, double step) {
        return Math.round(v / step) * step;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        layout(AstucyFonts.menu());
        // clicar fora do campo de rótulo salva o que foi digitado
        if (labelEdit) {
            commitLabel();
        }
        // posição do EVENTO (mx podia estar um clique atrasado — era o slider
        // que "ia pra frente e voltava sozinho")
        this.mx = event.x();
        this.my = event.y();
        // clique novo sempre zera arrasto antigo (nada fica "preso")
        drag = 0;
        int px = (int) mx;
        int py = (int) my;
        Font f = AstucyFonts.menu();

        // conta (chip no topo / dropdown): consome o clique quando age
        if (AccountMenu.click(px, py, event.button())) {
            return true;
        }

        // campo de pesquisa (topo) prioriza o foco
        if (!modsTab && option == null) {
            int[] sb = searchBox();
            if (inside(px, py, sb[0], sb[1], sb[2], sb[3])) {
                searchFocus = true;
                caret = searchText.length();
                return true;
            }
        }
        searchFocus = false;

        // fechar
        if (inside(px, py, x0 + pw - 26, y0 + 7, 18, 18)) {
            onClose();
            return true;
        }
        // abas (pills — mesma geometria do desenho)
        int t1x = x0 + sideW + 14;
        int t1w = 58;
        int t2x = t1x + t1w + 4;
        int t2w = 42;
        int stx = t2x + t2w + 4;
        int gwTab = 54;
        if (inside(px, py, t1x, y0 + 6, t1w, topH - 12)) {
            modsTab = false;
            option = null;
            catStamp = System.currentTimeMillis();
            return true;
        }
        if (inside(px, py, t2x, y0 + 6, t2w, topH - 12)) {
            modsTab = true;
            option = null;
            return true;
        }

        // engrenagem de configs do client (ao lado de MODS)
        if (inside(px, py, stx, y0 + 6, gwTab, topH - 12)) {
            AstucyConfig.save();
            minecraft.gui.setScreen(new ClientSettingsScreen(this));
            return true;
        }

        // sidebar
        int sy = y0 + topH + 22;
        for (int i = 0; i < PROFILES.length; i++) {
            if (inside(px, py, x0 + 10, sy + i * 22, sideW - 20, 18)) {
                if (event.button() == 1) {
                    profEdit = true;
                    profEditIdx = i;
                    profBuf = AstucyConfig.profileName(i);
                    profile = i;
                    return true;
                }
                if (profEdit && profEditIdx != i) {
                    profEdit = false;
                }
                profile = i;
                AstucyConfig.applyProfile(AstucyConfig.profileName(i));
                catStamp = System.currentTimeMillis();
                return true;
            }
        }
        int sideBtnY = sy + PROFILES.length * 22 + 6;
        if (inside(px, py, x0 + 10, sideBtnY, sideW - 20, 20)) {
            AstucyConfig.saveProfile(AstucyConfig.profileName(profile));
            savedStamp = System.currentTimeMillis();
            return true;
        }
        if (inside(px, py, x0 + 10, sideBtnY + 24, sideW - 20, 20)) {
            AstucyConfig.save();
            minecraft.gui.setScreen(new HudEditorScreen());
            return true;
        }
        if (inside(px, py, x0 + 10, sideBtnY + 48, sideW - 20, 20)) {
            AstucyConfig.save();
            minecraft.gui.setScreen(new ClientSettingsScreen(this));
            return true;
        }

        if (modsTab) {
            return super.mouseClicked(event, dbl);
        }

        // opções de módulo
        if (option != null) {
            return clickOptions(px, py);
        }

        // pills (mesma geometria do desenho)
        for (int i = 0; i < pillRects.size(); i++) {
            int[] r = pillRects.get(i);
            if (!inside(px, py, r[0], r[1], r[2], r[3])) {
                continue;
            }
            if (filter != pillCats.get(i)) {
                catStamp = System.currentTimeMillis();
            }
            filter = pillCats.get(i);
            sel = 0;
            scrollY = 0;
            scrollTarget = 0;
            return true;
        }

        // scrollbar da grade (arrastável — a zona de pega é mais larga que a barra)
        if (onGridBar(px, py)) {
            int ty = gridThumbY();
            int th = gridThumbH();
            drag = 4;
            dragGrab = (py >= ty && py < ty + th) ? (py - ty) : th / 2.0;
            applyDrag();
            return true;
        }

        // cards
        List<Module> list = filtered();
        for (int i = 0; i < list.size(); i++) {
            int gx = cx + (i % cols) * (cardW + 8);
            int gy = gridY + (i / cols) * (cardH + 7) - gridScrollPx();
            if (!inside(px, py, gx, gy, cardW, cardH)) {
                continue;
            }
            sel = i;
            Module m = list.get(i);
            press.put(m.id, System.currentTimeMillis());
            if (event.button() == 1 || inside(px, py, gx + cardW - 58, gy, 22, cardH)) {
                option = m;
                optSel = 0;
                optScroll = 0;
                optScrollTarget = 0;
                anims.put("optIn", 0f);
                return true;
            }
            m.enabled = !m.enabled;
            AstucyConfig.save();
            return true;
        }
        return super.mouseClicked(event, dbl);
    }

    /** Retângulo do controle de cada linha — MESMO do desenho e do clique. */
    private int[] optControl(int i) {
        int sx = sliderX();
        int ry = optRowY(i);
        Row r = modRow(i);
        if (r != null) {
            return r.toggle()
                    ? new int[]{sx, ry - 3, 36, 16}
                    : new int[]{sx, ry - 4, sliderW() + 2, 18};
        }
        return switch (i) {
            case 0, 2, 10 -> new int[]{sx, ry - 4, sliderW() + 2, 18};  // trilhas de slider
            case 1, 5, 6, 8 -> new int[]{sx, ry - 3, 36, 16};            // toggles
            case 3, 4 -> new int[]{sx, ry - 3, swPitch() * Module.SWATCHES.length + 28, 18}; // swatches + RGB
            case 7 -> new int[]{sx, ry - 3, 136, 17};                    // botão azul
            case 9 -> new int[]{sx, ry - 3, sliderW() + 42, 17};          // campo de texto
            default -> new int[]{sx, ry - 3, 10, 10};
        };
    }

    /** Índice do controle sob o cursor (-1 = nenhum). */
    private int controlIndexAt(int px, int py) {
        int rows = rowCount();
        for (int i = 0; i < rows; i++) {
            int[] r = optControl(i);
            if (r[1] < optTrackY() - 6 || r[1] > optTrackY() + optTrackH()) {
                continue;
            }
            if (inside(px, py, r[0], r[1], r[2], r[3])) {
                return i;
            }
        }
        return -1;
    }

    /** Converte o índice da linha no id de arrasto (0 = não arrastável). */
    private int dragIdFor(int i) {
        if (i < 0) {
            return 0;
        }
        Row mr = modRow(i);
        if (mr != null) {
            return mr.toggle() ? 0 : 100 + i;
        }
        return switch (i) {
            case 0 -> 1;
            case 2 -> 2;
            case 10 -> 6;
            default -> 0;
        };
    }

    private int[] paletteRect() {
        return new int[]{cx + cw - 166, cy + 34, 164, 142};
    }

    private void applyPicked() {
        int col = rgbHsv(hueSel, satSel, valSel) & 0xFFFFFF;
        int cur = colorEdit == 1 ? option.fgColor : option.bgColor;
        if (colorEdit == 1) {
            option.fgColor = ((cur >>> 24) & 0xFF) << 24 | col;
        } else {
            option.bgColor = ((cur >>> 24) & 0xFF) << 24 | col;
        }
    }

    private void applyWheel(int px, int py, int wx, int wy, int ws) {
        float dx = (px - (wx + ws / 2f)) / (ws / 2f - 2f);
        float dy = (py - (wy + ws / 2f)) / (ws / 2f - 2f);
        float rad = (float) Math.sqrt(dx * dx + dy * dy);
        float h2 = (float) (Math.atan2(dy, dx) / (Math.PI * 2));
        if (h2 < 0) {
            h2 += 1f;
        }
        hueSel = Math.max(0f, Math.min(1f, h2));
        satSel = Math.max(0f, Math.min(1f, rad));
        applyPicked();
    }

    private void applyBar(int px, int bx, int ws) {
        float fr = (px - bx) / (float) Math.max(1, ws);
        valSel = Math.max(0f, Math.min(1f, fr));
        applyPicked();
    }

    private void clickPalette(int px, int py) {
        int[] pr = paletteRect();
        if (inside(px, py, pr[0] + pr[2] - 15, pr[1] + 5, 11, 11)) {
            colorEdit = -1;
            AstucyConfig.save();
            return;
        }
        int wx = pr[0] + 8;
        int wy = pr[1] + 22;
        int ws = 88;
        if (inside(px, py, wx, wy, ws, ws)) {
            beginDrag(7, 0);
            applyWheel(px, py, wx, wy, ws);
            return;
        }
        int by = wy + ws + 6;
        if (inside(px, py, wx, by, ws, 12)) {
            beginDrag(8, 0);
            applyBar(px, wx, ws);
        }
    }

    private void drawPalette(GuiGraphicsExtractor g, Font f) {
        int[] pr = paletteRect();
        Ui.rrect(g, pr[0], pr[1], pr[2], pr[3], 0xD908080A, 10);
        g.text(f, "Custom color", pr[0] + 9, pr[1] + 7, Ui.TEXT, false);
        int wx = pr[0] + 8;
        int wy = pr[1] + 22;
        int ws = 88;
        Skin.hueWheel(g, wx, wy, ws);
        Skin.hueValueBar(g, wx, wy + ws + 6, ws, 10, hueSel, satSel);
        int col = colorEdit == 1 ? option.fgColor : option.bgColor;
        int pvx = wx + ws + 8;
        int pvy = wy + 2;
        Ui.rrect(g, pvx, pvy, 50, 24, 0xFF000000 | (col & 0xFFFFFF), 6);
        g.text(f, "R " + ((col >>> 16) & 0xFF), pvx, pvy + 28, Ui.TEXT, false);
        g.text(f, "G " + ((col >>> 8) & 0xFF), pvx, pvy + 37, Ui.TEXT, false);
        g.text(f, "B " + (col & 0xFF), pvx, pvy + 46, Ui.TEXT, false);
        float ang = hueSel * (float) (Math.PI * 2);
        int cxp = wx + ws / 2 + Math.round((float) Math.cos(ang) * (ws / 2f - 2f) * satSel);
        int cyp = wy + ws / 2 + Math.round((float) Math.sin(ang) * (ws / 2f - 2f) * satSel);
        Skin.disc(g, cxp - 3, cyp - 3, 6, 0xA6000000);
        Skin.disc(g, cxp - 2, cyp - 2, 4, 0xF2FFFFFF);
        int bxx = wx + Math.round(valSel * (ws - 6));
        Skin.disc(g, bxx, wy + ws + 9, 8, 0xF2FFFFFF);
        Ui.ctext(g, f, "X", pr[0] + pr[2] - 10, pr[1] + 7, 0x8CFFFFFF);
    }

    private void wheelOpenInit() {
        int cur = colorEdit == 1 ? option.fgColor : option.bgColor;
        float r = ((cur >>> 16) & 0xFF) / 255f;
        float g = ((cur >>> 8) & 0xFF) / 255f;
        float b = (cur & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) % 6f;
            } else if (max == g) {
                h = 2f + (b - r) / d;
            } else {
                h = 4f + (r - g) / d;
            }
            h /= 6f;
            if (h < 0) {
                h += 1f;
            }
        }
        hueSel = Math.max(0f, Math.min(1f, h));
        satSel = max <= 0f ? 0f : Math.min(1f, d / max);
        valSel = max;
    }

    private boolean clickOptions(int px, int py) {
        if (colorEdit >= 0) {
            int[] pr = paletteRect();
            if (inside(px, py, pr[0], pr[1], pr[2], pr[3])) {
                clickPalette(px, py);
                return true;
            }
            colorEdit = -1;
        }
        // barra de rolagem das opções (quando as linhas não cabem)
        if (onOptBar(px, py)) {
            int ty = optThumbY();
            int th = optThumbH();
            drag = 5;
            dragGrab = (py >= ty && py < ty + th) ? (py - ty) : th / 2.0;
            applyDrag();
            return true;
        }
        int rows = rowCount();
        for (int i = 0; i < rows; i++) {
            int[] r = optControl(i);
            if (r[1] < optTrackY() - 6 || r[1] > optTrackY() + optTrackH()) {
                continue;   // linha fora da área visível
            }
            if (!inside(px, py, r[0], r[1], r[2], r[3])) {
                continue;
            }
            if (i == 3 || i == 4) {
                int bx = sliderX() + swPitch() * Module.SWATCHES.length + 2;
                if (inside(px, py, bx, r[1], 24, 18)) {
                    colorEdit = i - 3;
                    wheelOpenInit();
                    return true;
                }
            }
            Row mrow = modRow(i);
            if (mrow != null) {
                if (mrow.toggle()) {
                    option.props.put(mrow.prop(), !rowBool(mrow));
                } else {
                    double f0 = sliderFrac();
                    beginDrag(100 + i, f0);
                    option.props.put(mrow.prop(), clampNum(
                            snap(mrow.min() + f0 * (mrow.max() - mrow.min()), mrow.step()),
                            mrow.min(), mrow.max()));
                }
                AstucyConfig.save();
                return true;
            }
            switch (i) {
                case 0 -> {
                    double f0 = sliderFrac();
                    beginDrag(1, f0);
                    option.scale = round2(0.6 + f0 * 1.4);
                }
                case 1 -> {
                    option.bg = !option.bg;
                    AstucyConfig.save();
                }
                case 2 -> {
                    double f0 = sliderFrac();
                    beginDrag(2, f0);
                    int alpha = (int) Math.round(f0 * 255.0);
                    option.bgColor = (clamp(alpha, 0, 255) << 24) | (option.bgColor & 0xFFFFFF);
                }
                case 3 -> {
                    int idx = clamp((px - sliderX()) / swPitch(), 0, Module.SWATCHES.length - 1);
                    option.bgColor = ((option.bgColor >>> 24) & 0xFF) << 24 | (Module.SWATCHES[idx] & 0xFFFFFF);
                    AstucyConfig.save();
                }
                case 4 -> {
                    int idx = clamp((px - sliderX()) / swPitch(), 0, Module.SWATCHES.length - 1);
                    option.fgColor = Module.SWATCHES[idx];
                    AstucyConfig.save();
                }
                case 5 -> {
                    option.shadow = !option.shadow;
                    AstucyConfig.save();
                }
                case 6 -> {
                    option.prefix = !option.prefix;
                    AstucyConfig.save();
                }
                case 7 -> {
                    AstucyConfig.save();
                    minecraft.gui.setScreen(new HudEditorScreen());
                }
                case 8 -> {
                    option.props.put("font", !option.boolProp("font", true));
                    AstucyConfig.save();
                }
                case 9 -> {
                    labelEdit = true;
                    labelBuf = option.customLabel == null ? "" : option.customLabel;
                }
                case 10 -> {
                    double f0 = sliderFrac();
                    beginDrag(6, f0);
                    option.scaleY = round2(0.6 + f0 * 2.4);
                }
                default -> { }
            }
            return true;
        }
        int by = cy + ch - 26;
        if (inside(px, py, cx, by, 136, 18)) {
            option.resetStyle();
            AstucyConfig.save();
            return true;
        }
        if (inside(px, py, cx + 146, by, 136, 18)) {
            option = null;
            optScroll = 0;
            optScrollTarget = 0;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        // dropdown de conta aberto: ESC só fecha ele
        if (AccountMenu.key(key)) {
            return true;
        }
        // renomeando o perfil
        if (profEdit) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                if (key != GLFW.GLFW_KEY_ESCAPE) {
                    AstucyConfig.renameProfile(profEditIdx, profBuf);
                }
                profEdit = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!profBuf.isEmpty()) {
                    profBuf = profBuf.substring(0, profBuf.length() - 1);
                }
                return true;
            }
            return true;
        }

        // editando o rótulo do módulo
        if (labelEdit) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                commitLabel();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!labelBuf.isEmpty()) {
                    labelBuf = labelBuf.substring(0, labelBuf.length() - 1);
                }
                return true;
            }
            return true;
        }
        // campo de busca em foco: teclas edita o texto (sem EditBox, sem sombra)
        if (searchFocus) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> {
                    searchFocus = false;
                    return true;
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (caret > 0) {
                        searchText = searchText.substring(0, caret - 1) + searchText.substring(caret);
                        caret--;
                        applyQuery();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (caret < searchText.length()) {
                        searchText = searchText.substring(0, caret) + searchText.substring(caret + 1);
                        applyQuery();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    caret = Math.max(0, caret - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    caret = Math.min(searchText.length(), caret + 1);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    caret = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    caret = searchText.length();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (option != null) {
                option = null;
            } else {
                onClose();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            modsTab = !modsTab;
            option = null;
            return true;
        }
        if (option != null) {
            int rows = rowCount();
            switch (key) {
                case GLFW.GLFW_KEY_UP -> optSel = Math.floorMod(optSel - 1, rows);
                case GLFW.GLFW_KEY_DOWN -> optSel = Math.floorMod(optSel + 1, rows);
                case GLFW.GLFW_KEY_LEFT -> adjustOption(-1);
                case GLFW.GLFW_KEY_RIGHT -> adjustOption(1);
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        if (!modsTab) {
            List<Module> list = filtered();
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> sel = Math.max(0, sel - 1);
                case GLFW.GLFW_KEY_RIGHT -> sel = Math.min(list.size() - 1, sel + 1);
                case GLFW.GLFW_KEY_UP -> sel = Math.max(0, sel - cols);
                case GLFW.GLFW_KEY_DOWN -> sel = Math.min(list.size() - 1, sel + cols);
                case GLFW.GLFW_KEY_ENTER -> {
                    if (sel < list.size()) {
                        option = list.get(sel);
                        optSel = 0;
                        optScroll = 0;
                        optScrollTarget = 0;
                        anims.put("optIn", 0f);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_SPACE -> {
                    if (sel < list.size()) {
                        list.get(sel).enabled = !list.get(sel).enabled;
                        AstucyConfig.save();
                    }
                    return true;
                }
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (labelEdit) {
            String s = event.codepointAsString();
            if (!s.isEmpty() && s.charAt(0) >= ' ' && labelBuf.length() < 16) {
                labelBuf += s;
            }
            return true;
        }
        if (profEdit) {
            String s = event.codepointAsString();
            if (!s.isEmpty() && s.charAt(0) >= ' ' && profBuf.length() < 20) {
                profBuf += s;
            }
            return true;
        }
        if (searchFocus) {
            String s = event.codepointAsString();
            if (!s.isEmpty() && s.charAt(0) >= ' ') {
                typeChar(s);
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void adjustOption(int dir) {
        Row mrow = modRow(optSel);
        if (mrow != null) {
            if (mrow.toggle()) {
                option.props.put(mrow.prop(), !rowBool(mrow));
            } else {
                option.props.put(mrow.prop(), clampNum(
                        snap(rowNum(mrow) + dir * mrow.step(), mrow.step()), mrow.min(), mrow.max()));
            }
            AstucyConfig.save();
            return;
        }
        switch (optSel) {
            case 0 -> {
                option.scale = Math.max(0.6, Math.min(2.0, snap(option.scale + dir * 0.05, 0.05)));
                option.scaleY = Math.max(0.6, Math.min(3.0, option.scale));
            }
            case 1 -> option.bg = !option.bg;
            case 2 -> {
                int a = ((option.bgColor >>> 24) & 0xFF) + dir * 13;
                option.bgColor = (clamp(a, 0, 255) << 24) | (option.bgColor & 0xFFFFFF);
            }
            case 3 -> {
                int i = (option.bgColor & 0xFFFFFF) == (Module.SWATCHES[0] & 0xFFFFFF) ? 0 : swatchIndex(option.bgColor);
                option.bgColor = ((option.bgColor >>> 24) & 0xFF) << 24
                        | (Module.SWATCHES[Math.floorMod(i + dir, Module.SWATCHES.length)] & 0xFFFFFF);
            }
            case 4 -> {
                int i = swatchIndex(option.fgColor);
                option.fgColor = Module.SWATCHES[Math.floorMod(i + dir, Module.SWATCHES.length)];
            }
            case 5 -> option.shadow = !option.shadow;
            case 6 -> option.prefix = !option.prefix;
            case 7 -> {
                AstucyConfig.save();
                minecraft.gui.setScreen(new HudEditorScreen());
            }
            case 8 -> option.props.put("font", !option.boolProp("font", true));
            case 10 -> option.scaleY = Math.max(0.6, Math.min(3.0, snap(option.scaleY + dir * 0.05, 0.05)));
            default -> { }
        }
        AstucyConfig.save();
    }

    private static int swatchIndex(int rgb) {
        for (int i = 0; i < Module.SWATCHES.length; i++) {
            if ((Module.SWATCHES[i] & 0xFFFFFF) == rgb) {
                return i;
            }
        }
        return 0;
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /* ------------------------------------------------------------------
     * desenho
     * ------------------------------------------------------------------ */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        Font f = AstucyFonts.menu();
        layout(f);

        // tempo do frame (animações independentes de FPS)
        long nowMs = System.currentTimeMillis();
        dt = Math.min(0.06f, Math.max(0.002f, (nowMs - stamp) / 1000f));
        stamp = nowMs;

        // scroll com inércia (smooth, em decimal — nada de arredondar por frame)
        float k = Math.min(1f, dt * 24f);
        scrollY += (scrollTarget - scrollY) * k;
        if (Math.abs(scrollTarget - scrollY) < 0.02f) {
            scrollY = scrollTarget;
        }
        optScroll += (optScrollTarget - optScroll) * k;
        if (Math.abs(optScrollTarget - optScroll) < 0.02f) {
            optScroll = optScrollTarget;
        }

        // cursor ao vivo do MouseHandler: durante um arrasto o mouseX/mouseY do
        // frame fica congelado (era o slider que "travava" no meio do movimento)
        Minecraft mcHud = Minecraft.getInstance();
        this.mx = mcHud.mouseHandler.getScaledXPos(mcHud.getWindow());
        this.my = mcHud.mouseHandler.getScaledYPos(mcHud.getWindow());

        // arrasto direto: segurar o botão sobre a barra ou um slider já arrasta
        // (não depende do evento de clique — clique rápido não "perde" mais o arrasto)
        if (drag == 0 && leftDown()) {
            int px = (int) mx;
            int py = (int) my;
            if (onOptBar(px, py)) {
                int th = optThumbH();
                int ty = optThumbY();
                drag = 5;
                dragGrab = (py >= ty && py < ty + th) ? (py - ty) : th / 2.0;
                applyDrag();
            } else if (option == null && onGridBar(px, py)) {
                int th = gridThumbH();
                int ty = gridThumbY();
                drag = 4;
                dragGrab = (py >= ty && py < ty + th) ? (py - ty) : th / 2.0;
                applyDrag();
            } else if (option != null) {
                int ci = controlIndexAt(px, py);
                int id = dragIdFor(ci);
                if (id != 0) {
                    beginDrag(id, controlFrac(id, ci));
                }
            }
        }

        // arrasto: segue o mouse enquanto o botão estiver preso. Quem encerra é
        // o mouseReleased — não dá pra perguntar "o botão está solto?" a cada
        // frame porque nenhuma fonte é confiável dentro de um Screen.
        if (drag != 0) {
            applyDrag();
        }

        // animação de abertura / fechamento
        float openRaw = Math.min(1f, (nowMs - openedAt) / 240f);
        float openEase = 1f - (float) Math.pow(1f - openRaw, 3.5);
        if (closing) {
            float closeRaw = Math.min(1f, (nowMs - closedAt) / 130f);
            if (closeRaw >= 1f) {
                finishClose();
                return;
            }
            openEase = 1f - closeRaw * closeRaw;
        }

        // cursor spotlight suavizado
        if (spotX < 0f) {
            spotX = (float) mx;
            spotY = (float) my;
        } else {
            float sk = Math.min(1f, dt * 18f);
            spotX += ((float) mx - spotX) * sk;
            spotY += ((float) my - spotY) * sk;
        }

        // fundo: blur real do mundo + vinheta atmosférica
        try {
            g.blurBeforeThisStratum();
        } catch (IllegalStateException alreadyBlurred) {
            // o proprio Screen ja aplicou o blur neste frame
        }
        int scrimA = Math.round(0x5C * openEase);
        Ui.rect(g, 0, 0, gw, gh, (scrimA << 24) | 0x030508);

        // painel principal: obsidiana profunda de alto contraste + grão sutil
        int pr = 12;
        Ui.shadow(g, x0, y0, pw, ph);
        if (!Skin.boxGrad(g, x0, y0, pw, ph, 0xF4080B10, 0xF805070B, pr)) {
            Ui.rrect(g, x0, y0, pw, ph, 0xF4070A0F, pr);
        }
        Ui.rframe(g, x0, y0, pw, ph, 0x18FFFFFF, pr);
        Skin.noise(g, x0 + 4, y0 + 4, pw - 8, ph - 8);

        Module mb = Modules.find("menublur");
        boolean showSpot = mb == null || !mb.enabled || mb.boolProp("spotlight", true);
        if (showSpot && inside((int) mx, (int) my, x0, y0, pw, ph)) {
            g.enableScissor(x0 + 2, y0 + 2, x0 + pw - 2, y0 + ph - 2);
            Skin.glow(g, Math.round(spotX), Math.round(spotY), 80, 0x0C22C55E);
            g.disableScissor();
        }

        // divisória sutil do cabeçalho
        Ui.hline(g, x0 + 6, y0 + topH, pw - 12, 0x10FFFFFF);

        // laterais: sidebar encaixada em obsidiana mais escura
        if (!Skin.boxGrad(g, x0 + 6, y0 + topH + 5, sideW - 4, ph - topH - 11, 0x58030508, 0x70020306, 9)) {
            Ui.rrect(g, x0 + 6, y0 + topH + 5, sideW - 4, ph - topH - 11, 0x48000000, 9);
        }
        // painel de opcoes do modulo: mesmo tom da sidebar
        if (option != null) {
            Ui.rrect(g, cx - 6, cy - 4, cw + 12, ch + 8, 0x36000000, 9);
        }

        // topo: logo oficial Astucy girando e voando até encaixar ao lado de ASTUCY + abas com indicador esmeralda
        float irisX = x0 + 18f;
        float irisY = y0 + topH / 2f;
        float logoFlight = Math.min(1f, Math.max(0f, (nowMs - openedAt) / 520f));
        float logoIdleAng = (float) ((nowMs % 9000L) / 9000.0 * Math.PI * 2.0);
        Skin.logoTravel(g,
                x0 + pw * 0.36f, y0 + topH + 32f,
                irisX, irisY,
                logoFlight,
                19f, 13.5f,
                logoIdleAng,
                0xFFFFFFFF, 0xFF22C55E);

        Font brand = AstucyFonts.brand();
        float brandFade = Math.min(1f, Math.max(0f, (logoFlight - 0.22f) / 0.78f));
        int brandCol = (((int) (0xFF * brandFade)) << 24) | (Ui.TEXT & 0xFFFFFF);
        int brandShift = Math.round((1f - brandFade) * 6f);
        Ui.spaced(g, brand, "ASTUCY", x0 + 29 + brandShift, y0 + (topH - brand.lineHeight) / 2 + 1, brandCol, 2);

        int t1x = x0 + sideW + 14;
        int t1w = 58;
        int t2x = t1x + t1w + 4;
        int t2w = 42;
        int gx = t2x + t2w + 4;
        int gwTab = 54;
        float tab = anim("tab", modsTab ? 1f : 0f, 16f);
        int ix = Math.round(t1x + (t2x - t1x) * tab);
        int iw = Math.round(t1w + (t2w - t1w) * tab);
        Ui.rrect(g, ix + 6, y0 + topH - 2, iw - 12, 2, 0xFF22C55E, 1);
        drawTab(g, f, t1x, y0 + 5, t1w, topH - 10, "MODULES", !modsTab);
        drawTab(g, f, t2x, y0 + 5, t2w, topH - 10, "MODS", modsTab);
        boolean ghov = inside((int) mx, (int) my, gx, y0 + 5, gwTab, topH - 10);
        float gha = anim("setTab", ghov ? 1f : 0f, 16f);
        if (gha > 0.02f) {
            Ui.rrect(g, gx, y0 + 5, gwTab, topH - 10, ((int) (0x18 * gha) << 24) | 0xFFFFFF, 5);
        }
        Ui.ctext(g, f, "SETTINGS", gx + gwTab / 2, y0 + (topH - f.lineHeight) / 2 + 1,
                Ui.mix(0xFF8A94A6, Ui.TEXT, gha));
        tabEndX = gx + gwTab + 8;

        // conta logada (chip + dropdown) ao lado do X — calcula chipX antes do searchBox()
        AccountMenu.cursor(mx, my);
        AccountMenu.drawChip(g, f, x0, y0, pw, topH);

        if (!modsTab && option == null) {
            int[] sb = searchBox();
            if (sb[2] >= 52) {
                boolean shv = inside((int) mx, (int) my, sb[0], sb[1], sb[2], sb[3]);
                float sf = anim("sfocus", searchFocus ? 1f : (shv ? 0.35f : 0f), 14f);
                Ui.rrect(g, sb[0], sb[1], sb[2], sb[3], Ui.mix(0xFF05070B, 0xFF0A0E16, sf), 6);
                Ui.rframe(g, sb[0], sb[1], sb[2], sb[3], Ui.mix(0x1CFFFFFF, 0xAA22C55E, sf), 6);
                Ui.icon(g, "search", sb[0] + 6, sb[1] + 4, 10, searchFocus ? 0xFF22C55E : 0xFF64748B);
                int tx = sb[0] + 19;
                int tfy = sb[1] + (sb[3] - f.lineHeight) / 2 + 1;
                if (searchText.isEmpty() && !searchFocus) {
                    g.text(f, "Search...", tx, tfy, 0xFF64748B, false);
                } else {
                    g.text(f, searchText, tx, tfy, Ui.TEXT, false);
                }
                if (searchFocus && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                    int cxr = tx + f.width(searchText.substring(0, Math.min(caret, searchText.length())));
                    Ui.rect(g, cxr + 1, sb[1] + 4, 1, sb[3] - 8, 0xFF22C55E);
                }
            }
        }

        // fechar (hover animado + ícone vetorial simétrico)
        int cbx = x0 + pw - 26;
        int cby = y0 + (topH - 18) / 2;
        float chv = anim("close", inside((int) mx, (int) my, cbx, cby, 18, 18) ? 1f : 0f, 16f);
        Ui.rrect(g, cbx, cby, 18, 18, Ui.mix(0xFF10141D, 0x38E25661, chv), 6);
        Ui.rframe(g, cbx, cby, 18, 18, Ui.mix(0x18FFFFFF, 0x88E25661, chv), 6);
        Skin.icon(g, "close", cbx + 4, cby + 4, 10, Ui.mix(0xFF94A3B8, 0xFFFFFFFF, chv));

        // sidebar
        drawSidebar(g, f);

        if (modsTab) {
            drawMods(g, f);
        } else if (option != null) {
            drawOptions(g, f);
            if (colorEdit >= 0) {
                drawPalette(g, f);
            }
        } else {
            drawGrid(g, f);
        }

        // dropdown de conta por cima de tudo
        AccountMenu.drawPanel(g, f);
    }

    private void drawTab(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, String label, boolean active) {
        boolean hov = inside((int) mx, (int) my, x, y, w, h);
        if (hov && !active) {
            Ui.rrect(g, x, y, w, h, 0x14FFFFFF, 5);
        }
        Ui.ctext(g, f, label, x + w / 2, y + (h - f.lineHeight) / 2 + 1, active ? 0xFFFFFFFF : 0xFF8A94A6);
    }

    /** Valor animado por chave (exponencial, independente de FPS). */
    private float anim(String key, float target, float speed) {
        if (!AstucyConfig.prefBool("anim", true)) {
            anims.put(key, target);
            return target;
        }
        float cur = anims.getOrDefault(key, target);
        cur += (target - cur) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - cur) < 0.002f) {
            cur = target;
        }
        anims.put(key, cur);
        return cur;
    }

    private void drawSidebar(GuiGraphicsExtractor g, Font f) {
        int sy = y0 + topH + 22;
        g.text(AstucyFonts.ui8(), "PROFILES", x0 + 12, sy - f.lineHeight - 4, 0xFF64748B, false);

        // seleção de perfil com superfície tonal limpa e indicador esmeralda à esquerda (sem borda grossa)
        for (int i = 0; i < PROFILES.length; i++) {
            int ry = sy + i * 22;
            boolean current = profile == i;
            boolean hov = inside((int) mx, (int) my, x0 + 10, ry, sideW - 18, 18);
            float a = anim("prof:" + i, current ? 1f : (hov ? 0.45f : 0f), 14f);
            if (a > 0.01f) {
                int pillCol = current
                        ? Ui.mix(0xFF0E131B, 0xFF151C27, a)
                        : (((int) (0x16 * a) << 24) | 0xFFFFFF);
                Ui.rrect(g, x0 + 10, ry, sideW - 18, 18, pillCol, 5);
                if (current) {
                    Ui.rrect(g, x0 + 12, ry + 4, 2, 10, 0xFF22C55E, 1);
                }
            }
            boolean editing = profEdit && profEditIdx == i;
            String shown = editing ? profBuf : AstucyConfig.profileName(i);
            g.text(f, shown, x0 + 18, ry + (18 - f.lineHeight) / 2 + 1,
                    current || editing ? 0xFFFFFFFF : Ui.mix(0xFF8A94A6, 0xFFE2E8F0, a), false);
            if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                Ui.rect(g, x0 + 18 + f.width(shown), ry + 4, 1, f.lineHeight, 0xFF22C55E);
            }
        }

        int by = sy + PROFILES.length * 22 + 8;
        int bw = sideW - 18;
        boolean sh = inside((int) mx, (int) my, x0 + 10, by, bw, 20);
        boolean hh = inside((int) mx, (int) my, x0 + 10, by + 24, bw, 20);
        float sa = anim("sb1", sh ? 1f : 0f, 16f);
        boolean justSaved = System.currentTimeMillis() - savedStamp < 1200L;
        int saveBg = justSaved ? 0x2E22C55E : Ui.mix(0xFF121721, 0xFF1A2230, sa);
        Ui.rrect(g, x0 + 10, by, bw, 20, saveBg, 6);
        Ui.rframe(g, x0 + 10, by, bw, 20, justSaved ? 0x8822C55E : 0x16FFFFFF, 6);
        Ui.ctext(g, f, justSaved ? "SAVED" : "SAVE PROFILE", x0 + 10 + bw / 2, by + (20 - f.lineHeight) / 2 + 1,
                justSaved ? 0xFF4ADE80 : Ui.mix(0xFFCBD5E1, 0xFFFFFFFF, sa));

        // Botão primário sólido esmeralda para EDIT HUD (alto contraste, sem wireframe oco)
        float hb = anim("sb2", hh ? 1f : 0f, 16f);
        int hudTop = Ui.mix(0xFF22C55E, 0xFF34D399, hb);
        int hudBot = Ui.mix(0xFF16A34A, 0xFF22C55E, hb);
        if (!Skin.boxGrad(g, x0 + 10, by + 24, bw, 20, hudTop, hudBot, 6)) {
            Ui.rrect(g, x0 + 10, by + 24, bw, 20, hudTop, 6);
        }
        Ui.ctext(g, f, "EDIT HUD", x0 + 10 + bw / 2, by + 24 + (20 - f.lineHeight) / 2 + 1, 0xFF04140A);

        // Card de telemetria ao vivo no rodapé da sidebar
        Font sf = AstucyFonts.ui8();
        int ty = y0 + ph - 42;
        if (ty > by + 50) {
            Ui.rrect(g, x0 + 10, ty, sideW - 18, 32, 0xFF070A0F, 6);
            Ui.rframe(g, x0 + 10, ty, sideW - 18, 32, 0x12FFFFFF, 6);
            int activeMods = 0;
            for (Module m : Modules.all()) {
                if (m.enabled) {
                    activeMods++;
                }
            }
            Skin.glow(g, x0 + 18, ty + 10, 9, 0x2222C55E);
            float miniAng = (float) ((System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2.0);
            Skin.logo(g, x0 + 18.5f, ty + 9.5f, 8.5f, miniAng, 0xFF22C55E);
            g.text(sf, activeMods + " active", x0 + 26, ty + 5, 0xFFE2E8F0, false);
            int fpsNow = Minecraft.getInstance().getFps();
            g.text(sf, fpsNow + " FPS  ·  v0.1", x0 + 16, ty + 18, 0xFF64748B, false);
        } else {
            g.text(sf, "astucy v0.1", x0 + 12, y0 + ph - f.lineHeight - 8, 0xFF64748B, false);
        }
    }

    private void drawGrid(GuiGraphicsExtractor g, Font f) {
        // indicador da pill ativa — pílula sólida esmeralda que desliza sem contorno borrado
        int activeIdx = -1;
        for (int i = 0; i < pillCats.size(); i++) {
            if (pillCats.get(i) == filter) {
                activeIdx = i;
                break;
            }
        }
        int[] ar = activeIdx >= 0 ? pillRects.get(activeIdx) : null;
        float ix = anim("pillX", ar == null ? cx : ar[0], 20f);
        float iw = anim("pillW", ar == null ? 0 : ar[2], 20f);
        for (int i = 0; i < pillRects.size(); i++) {
            int[] r = pillRects.get(i);
            boolean active = filter == pillCats.get(i);
            boolean hov = inside((int) mx, (int) my, r[0], r[1], r[2], r[3]);
            float h = anim("ph:" + i, hov ? 1f : 0f, 16f);
            if (!active && h > 0.02f) {
                Ui.rrect(g, r[0], r[1], r[2], r[3], ((int) (0x16 * h) << 24) | 0xFFFFFF, 5);
            }
        }
        if (ar != null && iw > 2f) {
            int rx = Math.round(ix);
            int rw = Math.round(iw);
            if (!Skin.boxGrad(g, rx, ar[1], rw, ar[3], 0xFF22C55E, 0xFF16A34A, 5)) {
                Ui.rrect(g, rx, ar[1], rw, ar[3], 0xFF22C55E, 5);
            }
        }
        for (int i = 0; i < pillRects.size(); i++) {
            int[] r = pillRects.get(i);
            Object key = pillCats.get(i);
            String label = pillLabelText(key);
            boolean active = filter == key;
            boolean hov = inside((int) mx, (int) my, r[0], r[1], r[2], r[3]);
            int ly = r[1] + (r[3] - f.lineHeight) / 2 + 1;
            if (active) {
                Ui.ctext(g, f, label, Math.round(ix + iw / 2), ly, 0xFF04140A);
            } else {
                float h = anim("ph:" + i, hov ? 1f : 0f, 16f);
                Ui.ctext(g, f, label, r[0] + r[2] / 2, ly,
                        Ui.mix(0xFF8A94A6, 0xFFFFFFFF, h));
            }
        }

        List<Module> list = filtered();
        if (list.isEmpty()) {
            Ui.icon(g, "search", cx + cw / 2 - 16, cy + 46, 32, 0x44FFFFFF);
            Ui.ctext(g, f, "Nothing here", cx + cw / 2, cy + 88, Ui.DIM);
            Ui.ctext(g, f, "try another filter or clear the search", cx + cw / 2, cy + 102, 0x55FFFFFF);
            return;
        }

        // cards (recortados pra não vazarem do painel)
        int gridBottom = cy + ch;
        g.enableScissor(cx - 2, gridY - 2, cx + cw, gridBottom);
        float wmAng = (float) ((System.currentTimeMillis() % 28000L) / 28000.0 * Math.PI * 2.0);
        Skin.logo(g, cx + (cw - 8) * 0.5f, gridY + (gridBottom - gridY) * 0.5f, 148f, wmAng, 0x0A22C55E);
        for (int i = 0; i < list.size(); i++) {
            int gx = cx + (i % cols) * (cardW + 8);
            int gy = gridY + (i / cols) * (cardH + 7) - gridScrollPx();
            if (gy + cardH < gridY - 2 || gy > gridBottom) {
                continue;
            }
            drawCard(g, f, list.get(i), gx, gy, i);
        }
        g.disableScissor();

        // fades suaves caso a lista esteja rolada
        if (scrollY > 1f) {
            for (int i = 0; i < 6; i++) {
                Ui.rect(g, cx, gridY + i, cw - 8, 1, (((int) (0x14 * (6 - i))) << 24) | 0x070A0F);
            }
        }
        int max = maxScroll();
        if (max > 0 && scrollY < max - 2f) {
            for (int i = 0; i < 8; i++) {
                Ui.rect(g, cx, gridBottom - 8 + i, cw - 8, 1, (((int) (0x14 * (i + 1))) << 24) | 0x070A0F);
            }
        }

        // scrollbar: trilho fino na calha exclusiva à direita dos cards
        if (max > 0) {
            boolean hover = drag == 0 && onGridBar((int) mx, (int) my);
            boolean scrolling = System.currentTimeMillis() - lastScroll < 900 || scrollTarget != scrollY;
            float hot = anim("sbar", (hover || scrolling || drag == 4) ? 1f : 0f, 12f);
            Ui.scrollbar(g, cx + cw - 4, gridTrackY(), gridTrackH(),
                    gridThumbY(), gridThumbH(), hot, drag == 4 ? 1f : 0f);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, Font f, Module m, int gx, int gy, int idx) {
        boolean hov = inside((int) mx, (int) my, gx, gy, cardW, cardH);
        float h = anim("c:" + m.id, hov ? 1f : 0f, 16f);
        float on = anim("e:" + m.id, m.enabled ? 1f : 0f, 15f);
        Long pr = press.get(m.id);
        float flash = pr == null ? 0f : Math.max(0f, 1f - (System.currentTimeMillis() - pr) / 280f);

        // cascata suave ao abrir o menu ou trocar de categoria
        int row = idx / cols;
        float enterRaw = (System.currentTimeMillis() - catStamp - row * 18L - (idx % cols) * 9L) / 210f;
        float enter = Math.max(0f, Math.min(1f, enterRaw));
        float enterEase = 1f - (1f - enter) * (1f - enter) * (1f - enter);
        int slideY = Math.round((1f - enterEase) * 5f);

        int ly = gy + slideY - Math.round(1.0f * h);

        // superfície escura bem definida + tom esmeralda profundo quando o módulo está ativo (sem borda vazada)
        int topOff = Ui.mix(0xFF0E121A, 0xFF151B27, h);
        int botOff = Ui.mix(0xFF0B0E15, 0xFF101620, h);
        int topOn = Ui.mix(0xFF0E221A, 0xFF132C22, h);
        int botOn = Ui.mix(0xFF0A1812, 0xFF0E2119, h);
        int cTop = Ui.mix(topOff, topOn, on);
        int cBot = Ui.mix(botOff, botOn, on);
        if (!Skin.boxGrad(g, gx, ly, cardW, cardH, cTop, cBot, 7)) {
            Ui.rrect(g, gx, ly, cardW, cardH, cTop, 7);
        }

        // barra lateral esmeralda animada quando ativo
        int badgeSz = 22;
        int badgeX = gx + 8;
        int badgeY = ly + (cardH - badgeSz) / 2;
        if (on > 0.02f) {
            int barH = Math.max(4, Math.round((cardH - 14) * on));
            int barY = ly + (cardH - barH) / 2;
            Ui.rrect(g, gx + 2, barY, 2, barH, ((int) (0xFF * on) << 24) | 0x22C55E, 1);
        }

        // poço escuro recuado para o ícone do módulo (esmeralda quando ativo)
        if (on > 0.04f) {
            Skin.glow(g, badgeX + badgeSz / 2, badgeY + badgeSz / 2, 16, (((int) (0x20 * on)) << 24) | 0x22C55E);
        }
        Ui.rrect(g, badgeX, badgeY, badgeSz, badgeSz,
                Ui.mix(Ui.mix(0xFF07090E, 0xFF0B0E16, h), 0x2622C55E, on), 5);
        Ui.icon(g, m.id, badgeX + 5, badgeY + 5, 12,
                Ui.mix(Ui.mix(0xFF94A3B8, 0xFFE2E8F0, h), 0xFF4ADE80, on));

        // tipografia limpa em 2 linhas: Nome na linha 1, Descrição concisa (sem "...") na linha 2
        int textX = badgeX + badgeSz + 8;
        int titleY = ly + 7;
        g.text(f, m.name, textX, titleY,
                Ui.mix(0xFFF1F5F9, 0xFFFFFFFF, Math.max(on, h)), false);

        Font sf = AstucyFonts.ui8();
        if (m.isNew && on < 0.3f) {
            int bx = textX + f.width(m.name) + 5;
            int bw = sf.width("NEW") + 6;
            if (bx + bw < gx + cardW - 58) {
                Ui.rrect(g, bx, titleY + 1, bw, sf.lineHeight + 2, 0x2222C55E, 3);
                g.text(sf, "NEW", bx + 3, titleY + 2, 0xFF4ADE80, false);
            }
        }

        String desc = m.description == null ? "" : m.description;
        int subColor = Ui.mix(Ui.mix(0xFF64748B, 0xFF94A3B8, h), 0xFF6EE7B7, on * 0.85f);
        g.text(sf, desc, textX, ly + 21, subColor, false);

        // botão de opções (18x18 em gx + cardW - 56, deixando 6px livres antes do switch)
        int optX = gx + cardW - 56;
        int optY = ly + (cardH - 18) / 2;
        boolean gearHov = inside((int) mx, (int) my, optX - 2, ly + 4, 22, cardH - 8);
        float gh2 = anim("g:" + m.id, gearHov ? 1f : 0f, 16f);
        if (gh2 > 0.02f) {
            Ui.rrect(g, optX, optY, 18, 18, ((int) (0x20 * gh2) << 24) | 0xFFFFFF, 5);
        }
        Ui.icon(g, "options", optX + 4, ly + (cardH - 10) / 2, 10,
                Ui.mix(0xFF64748B, 0xFFFFFFFF, Math.max(h * 0.45f, gh2)));

        // switch de alto contraste (trilha preta profunda quando OFF, esmeralda vibrante quando ON)
        int sw = 26;
        int sh = 14;
        int sx = gx + cardW - sw - 8;
        int sy = ly + (cardH - sh) / 2;
        if (!Skin.boxGrad(g, sx, sy, sw, sh,
                Ui.mix(0xFF05070B, 0xFF22C55E, on),
                Ui.mix(0xFF040609, 0xFF16A34A, on), sh / 2)) {
            Ui.rrect(g, sx, sy, sw, sh, Ui.mix(0xFF05070B, 0xFF22C55E, on), sh / 2);
        }
        if (on < 0.85f) {
            Ui.rframe(g, sx, sy, sw, sh, Ui.mix(0x24FFFFFF, 0x00FFFFFF, on), sh / 2);
        }
        int kr = sh - 4;
        int kx = sx + 2 + Math.round((sw - 4 - kr) * on);
        int knobCol = Ui.mix(Ui.mix(0xFF64748B, 0xFF94A3B8, h), 0xFFFFFFFF, on);
        Skin.disc(g, kx, sy + 2, kr, knobCol);

        // pulso sutil ao clicar no card
        if (flash > 0.01f) {
            int rad = Math.round(16 + (1f - flash) * (cardW * 0.40f));
            Skin.glow(g, gx + cardW / 2, ly + cardH / 2, rad, (((int) (0x22 * flash)) << 24) | 0x22C55E);
        }
    }

    private void drawOptions(GuiGraphicsExtractor g, Font f) {
        Module m = option;
        Font b = AstucyFonts.bold();
        boolean behavior = behaviorMode();

        // animação de entrada (desliza 8px ao abrir)
        float oi = anim("optIn", 1f, 16f);
        optShift = Math.round((1f - oi) * 8f);
        int hy = cy - 2 + optShift;

        // cabeçalho + preview ao vivo
        Ui.icon(g, m.id, cx + 2, hy, 16, Ui.TEXT);
        g.text(AstucyFonts.midBold(), m.name, cx + 26, hy, Ui.TEXT, false);
        g.text(f, m.description, cx + 26, hy + f.lineHeight + 2, Ui.DIM, false);
        // só módulo de HUD tem chip; blur/zoom/viewmodel não desenham texto na tela
        if (m.hud) {
            String label = Modules.chipText(m, minecraft);
            int[] psz = Modules.chipSize(f, m, label == null ? m.name : label);
            Modules.drawHud(g, f, m, cx + cw - psz[0] - 14, hy);
        }
        Ui.hline(g, cx, cy + f.lineHeight * 2 + 8 + optShift, cw, 0x00FFFFFF);

        int rows = rowCount();
        int hovered = -1;
        // as linhas rolam dentro da área visível (nada vaza pro rodapé)
        g.enableScissor(cx - 10, optTrackY(), cx + cw + 10, optTrackY() + optTrackH());
        for (int i = 0; i < rows; i++) {
            int ry = optRowY(i);
            int rh = optRowH();
            boolean focus = optSel == i;
            boolean rowHover = inside((int) mx, (int) my, cx - 4, ry - 4, cw + 8, rh);
            if (rowHover) {
                hovered = i;
            }
            if (focus) {
                Ui.rrect(g, cx - 6, ry - 4, cw + 12, rh, 0x14FFFFFF);
            } else if (rowHover) {
                Ui.rrect(g, cx - 6, ry - 4, cw + 12, rh, 0x12FFFFFF);
                Ui.rect(g, cx - 6, ry - 1, 2, rh - 6, 0xFF6FE3A2);
            }
            String rowLabel = rowLabel(i);
            g.text(f, rowLabel, cx + 6, ry + 1, focus || rowHover ? Ui.TEXT : Ui.DIM, false);
            int sx = sliderX();
            Row mr = modRow(i);
            if (mr != null) {
                if (mr.toggle()) {
                    boolean on = rowBool(mr);
                    Ui.toggle(g, sx, ry - 3, 36, 16, on, toggleAnim("b:" + mr.prop(), on));
                } else {
                    double v = rowNum(mr);
                    float frac = (float) ((v - mr.min()) / (mr.max() - mr.min()));
                    Ui.sliderV3(g, sx, ry + 1, sliderW(), frac,
                            drag == 100 + i ? 1f : (rowHover ? 0.45f : 0f));
                    g.text(f, rowText(mr, v), sx + sliderW() + 10, ry + 1, Ui.TEXT, false);
                }
                continue;
            }
            switch (i) {
                case 0 -> {
                    Ui.sliderV3(g, sx, ry + 1, sliderW(), (float) ((m.scale - 0.6) / 1.4),
                            drag == 1 ? 1f : (rowHover ? 0.45f : 0f));
                    g.text(f, String.format("%.2fx", m.scale), sx + sliderW() + 10, ry + 1, Ui.TEXT, false);
                }
                case 1 -> Ui.toggle(g, sx, ry - 3, 36, 16, m.bg, toggleAnim("bg", m.bg));
                case 2 -> {
                    int alpha = (m.bgColor >>> 24) & 0xFF;
                    Ui.sliderV3(g, sx, ry + 1, sliderW(), alpha / 255f,
                            drag == 2 ? 1f : (rowHover ? 0.45f : 0f));
                    g.text(f, (alpha * 100 / 255) + "%", sx + sliderW() + 10, ry + 1, Ui.TEXT, false);
                }
                case 3 -> {
                    for (int s = 0; s < Module.SWATCHES.length; s++) {
                        Ui.swatch(g, sx + s * swPitch(), ry - 2 + (13 - swSize()) / 2, swSize(), Module.SWATCHES[s],
                                (m.bgColor & 0xFFFFFF) == (Module.SWATCHES[s] & 0xFFFFFF));
                    }
                    Ui.rrect(g, sx + swPitch() * Module.SWATCHES.length + 2, ry - 2, 24, 14, 0x1AFFFFFF, 4);
                    g.text(f, "RGB", sx + swPitch() * Module.SWATCHES.length + 6, ry + 1, 0x9EFFFFFF, false);
                }
                case 4 -> {
                    for (int s = 0; s < Module.SWATCHES.length; s++) {
                        Ui.swatch(g, sx + s * swPitch(), ry - 2 + (13 - swSize()) / 2, swSize(), Module.SWATCHES[s],
                                (m.fgColor & 0xFFFFFF) == (Module.SWATCHES[s] & 0xFFFFFF));
                    }
                    Ui.rrect(g, sx + swPitch() * Module.SWATCHES.length + 2, ry - 2, 24, 14, 0x1AFFFFFF, 4);
                    g.text(f, "RGB", sx + swPitch() * Module.SWATCHES.length + 6, ry + 1, 0x9EFFFFFF, false);
                }
                case 5 -> Ui.toggle(g, sx, ry - 3, 36, 16, m.shadow, toggleAnim("shadow", m.shadow));
                case 6 -> Ui.toggle(g, sx, ry - 3, 36, 16, m.prefix, toggleAnim("prefix", m.prefix));
                case 7 -> Ui.blueBtn(g, f, sx, ry - 3, 136, 17, "EDIT LAYOUT");
                case 8 -> {
                    boolean cleanFont = option.boolProp("font", true);
                    Ui.toggle(g, sx, ry - 3, 36, 16, cleanFont, toggleAnim("font", cleanFont));
                }
                case 9 -> {
                    boolean editing = labelEdit;
                    int fw = sliderW() + 42;
                    Ui.rrect(g, sx, ry - 3, fw, 17, editing ? 0x2EFFFFFF : 0x1AFFFFFF);
                    String cur = option.customLabel == null ? "" : option.customLabel;
                    String txt = editing ? labelBuf : (cur.isEmpty() ? "—" : cur);
                    int col = editing ? Ui.WHITE : (cur.isEmpty() ? Ui.DIM : Ui.TEXT);
                    g.text(f, txt, sx + 6, ry + 1, col, false);
                    if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                        Ui.rect(g, sx + 7 + f.width(labelBuf), ry - 1, 1, 11, 0xFFFFFFFF);
                    }
                }
                case 10 -> {
                    Ui.sliderV3(g, sx, ry + 1, sliderW(), (float) ((m.scaleY - 0.6) / 2.4),
                            drag == 6 ? 1f : (rowHover ? 0.45f : 0f));
                    g.text(f, String.format("%.2fx", m.scaleY), sx + sliderW() + 10, ry + 1, Ui.TEXT, false);
                }
                default -> { }
            }
        }

        g.disableScissor();

        // scrollbar das opções (trilho + pílula, arrastável)
        int omax = optMaxScroll();
        if (omax > 0) {
            boolean hover = drag == 0 && onOptBar((int) mx, (int) my);
            float ohot = anim("osbar", (hover || drag == 5) ? 1f : 0f, 12f);
            Ui.scrollbar(g, cx + cw - 4, optTrackY(), optTrackH(),
                    optThumbY(), optThumbH(), ohot, drag == 5 ? 1f : 0f);
        }

        int by = cy + ch - 26;
        boolean h1 = inside((int) mx, (int) my, cx, by, 136, 18);
        boolean h2 = inside((int) mx, (int) my, cx + 146, by, 136, 18);
        Ui.rrect(g, cx, by, 136, 18, Ui.mix(0x1EFFFFFF, 0x34FFFFFF, anim("ob1", h1 ? 1f : 0f, 16f)));
        Ui.ctext(g, f, "RESET STYLE", cx + 68, by + 5, Ui.TEXT);
        Ui.rrect(g, cx + 146, by, 136, 18, Ui.mix(0x1EFFFFFF, 0x34FFFFFF, anim("ob2", h2 ? 1f : 0f, 16f)));
        Ui.ctext(g, f, "BACK", cx + 146 + 68, by + 5, Ui.TEXT);

        // tooltip da linha sob o cursor
        if (hovered >= 0) {
            String tip = rowTip(hovered);
            if (!tip.isEmpty()) {
                Ui.tooltip(g, f, clamp((int) mx + 12, cx, cx + cw - 180), (int) my + 14, tip);
            }
        }
    }

    /** Zoom, viewmodel e afins mostram configurações de comportamento (não de estilo). */
    private boolean behaviorMode() {
        return rows() != null;
    }

    private Row[] rows() {
        return rowsFor(option);
    }

    private static Row[] rowsFor(Module m) {
        if (m == null) {
            return null;
        }
        return switch (m.id) {
            case "zoom" -> ZOOM_ROWS;
            case "viewmodel" -> VIEWMODEL_ROWS;
            case "blur" -> BLUR_ROWS;
            case "speedfx" -> SPEEDFX_ROWS;
            case "ambience" -> AMBIENCE_ROWS;
            case "menublur" -> MENUBLUR_ROWS;
            case "crosshair" -> CROSSHAIR_ROWS;
            case "chat" -> CHAT_ROWS;
            case "fps" -> FPS_ROWS;
            case "ping" -> PING_ROWS;
            case "memory" -> MEMORY_ROWS;
            case "cps" -> CPS_ROWS;
            case "keystrokes" -> KEYSTROKE_ROWS;
            case "coords" -> COORD_ROWS;
            case "armor" -> ARMOR_ROWS;
            case "efeitos" -> EFFECT_ROWS;
            default -> null;
        };
    }

    /** Módulos de HUD mostram as linhas de estilo + as linhas próprias do módulo. */
    private boolean hasStyle() {
        return option != null && option.hud;
    }

    private int styleRows() {
        return hasStyle() ? OPT_ROWS.length : 0;
    }

    private int rowCount() {
        int s = styleRows();
        Row[] r = rows();
        int m = r == null ? 0 : r.length;
        if (s > 0) {
            return s + m;
        }
        return m > 0 ? m : OPT_ROWS.length;
    }

    private Row modRow(int i) {
        Row[] r = rows();
        if (r == null) {
            return null;
        }
        int k = i - styleRows();
        return k >= 0 && k < r.length ? r[k] : null;
    }

    private String rowLabel(int i) {
        int s = styleRows();
        if (s > 0 && i < s) {
            return OPT_ROWS[i];
        }
        Row r = modRow(i);
        return r == null ? "" : r.label();
    }

    private String rowTip(int i) {
        int s = styleRows();
        if (s > 0 && i < s) {
            return OPT_TOOLTIPS[i];
        }
        Row r = modRow(i);
        return r == null ? "" : r.tip();
    }

    private boolean rowBool(Row r) {
        return option.boolProp(r.prop(), r.def() != 0);
    }

    private double rowNum(Row r) {
        return option.numProp(r.prop(), r.def());
    }

    private static String rowText(Row r, double v) {
        if (r.prop().equals("level")) {
            return Math.round(v * 100) + "%";
        }
        if (r.prop().equals("vmscale")) {
            return String.format(java.util.Locale.ROOT, "%.2fx", v);
        }
        if (r.prop().equals("strength") || r.prop().equals("intensity")) {
            return String.format(java.util.Locale.ROOT, "%.2fx", v);
        }
        if (r.prop().equals("lowpct")) {
            return Math.round(v) + "%";
        }
        if (r.prop().equals("box") || r.prop().equals("size") || r.prop().equals("gap")) {
            return Math.round(v) + " px";
        }
        if (r.prop().equals("decimals")) {
            return String.valueOf(Math.round(v));
        }
        return String.format(java.util.Locale.ROOT, "%+.2f", v);
    }

    private static double clampNum(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private float toggleAnim(String key, boolean on) {
        return anim("t:" + key, on ? 1f : 0f, 18f);
    }

    private void drawMods(GuiGraphicsExtractor g, Font f) {
        List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        mods.sort(Comparator.comparing(mc -> mc.getMetadata().getId()));
        int rowH = f.lineHeight + 12;
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int row = 0;
        int sy = gridScrollPx();
        int start = Math.max(0, sy / rowH);
        for (int i = start; i < mods.size(); i++) {
            int ry = cy + row * rowH - sy % rowH;
            if (ry > cy + ch) {
                break;
            }
            ModContainer mc = mods.get(i);
            boolean hov = inside((int) mx, (int) my, cx, ry, cw, rowH - 4);
            float hh = anim("mod:" + mc.getMetadata().getId(), hov ? 1f : 0f, 16f);
            Ui.rrect(g, cx, ry, cw, rowH - 4, Ui.mix(0x0BFFFFFF, 0x1FFFFFFF, hh));
            g.text(f, mc.getMetadata().getName(), cx + 10, ry + 5, Ui.mix(Ui.NAME, Ui.TEXT, hh), false);
            String ver = mc.getMetadata().getVersion().getFriendlyString();
            g.text(f, ver, cx + cw - f.width(ver) - 10, ry + 5, Ui.DIM, false);
            row++;
        }
        g.disableScissor();
        g.text(f, mods.size() + " mods loaded by Fabric", cx + 8, cy + ch - 14, Ui.DIM, false);
    }
}
