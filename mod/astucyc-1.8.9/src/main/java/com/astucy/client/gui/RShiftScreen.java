package com.astucy.client.gui;

import com.astucy.client.account.Accounts;
import com.astucy.client.config.AstucyConfig;
import com.astucy.client.module.Module;
import com.astucy.client.module.ModuleCategory;
import com.astucy.client.module.Modules;
import com.astucy.client.ui.Skin;
import com.astucy.client.ui.Ui;
import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class RShiftScreen extends Screen {
    private static final int TAB_MODULES = 0;
    private static final int TAB_MODS = 1;
    private static final int TAB_SETTINGS = 2;

    private int activeTab = TAB_MODULES;
    private float tabIndicatorX = 0f;
    private float tabIndicatorW = 54f;

    private ModuleCategory selectedCat = null; // null = ALL
    private String searchQuery = "";
    private boolean searchFocused = false;

    private float scrollModules = 0f;
    private float scrollMods = 0f;
    private float scrollSettings = 0f;
    private float scrollOptions = 0f;

    private Module optionsModule = null;
    private int dragSlider = -1; // 0=scale, 1=opacity, 2=radius
    private boolean dragWheel = false;
    private int wheelTarget = 0; // 0=module fg, 2=menu accent
    private int paintMode = 0; // 0=none, 1=draw, 2=erase

    private final AccountMenu accountMenu = new AccountMenu();
    private final Map<String, Float> toggleAnim = new HashMap<String, Float>();
    private final List<ModContainer> modList = new ArrayList<ModContainer>(FabricLoader.getInstance().getAllMods());

    private static final int PARTICLE_COUNT = 26;
    private final float[] px = new float[PARTICLE_COUNT];
    private final float[] py = new float[PARTICLE_COUNT];
    private final float[] vx = new float[PARTICLE_COUNT];
    private final float[] vy = new float[PARTICLE_COUNT];
    private final float[] pr = new float[PARTICLE_COUNT];
    private boolean particlesInit = false;

    private float openAnim = 0f;
    private long lastNs = System.nanoTime();

    // Rust-parity responsive UI scale metrics
    private double uiDensity = 1.0;
    private float uiScale = 1f;
    private float gw = 1040f, gh = 700f;
    private float shX, shY, shW, shH;
    private float topH = 44f;
    private float sideW = 148f;

    public void openModuleOptions(Module m) {
        this.activeTab = TAB_MODULES;
        this.optionsModule = m;
        this.scrollOptions = 0f;
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    private void computeUiScale() {
        int physW = Math.max(1, client.width);
        int physH = Math.max(1, client.height);
        Window win = new Window(client);
        int guiScale = Math.max(1, win.getScaleFactor());
        double d = Math.min(physW / 1040.0, physH / 700.0);
        uiDensity = Math.max(0.65, Math.min(4.0, d));
        uiScale = (float) (uiDensity / guiScale);
        gw = Math.round(physW / uiDensity);
        gh = Math.round(physH / uiDensity);
        shW = clampInt(Math.round(gw * 0.86f), 660, 900);
        shH = clampInt(Math.round(gh * 0.84f), 420, 560);
        topH = 44f;
        sideW = clampInt(Math.round(shW / 5.8f), 138, 168);
        shX = (gw - shW) * 0.5f;
        shY = (gh - shH) * 0.5f + (1f - openAnim) * 10f;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        if (!particlesInit) {
            particlesInit = true;
            Random rnd = new Random(1337L);
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                px[i] = rnd.nextFloat();
                py[i] = rnd.nextFloat();
                vx[i] = (rnd.nextFloat() - 0.5f) * 0.015f;
                vy[i] = -0.01f - rnd.nextFloat() * 0.025f;
                pr[i] = 1.6f + rnd.nextFloat() * 2.4f;
            }
        }
        for (Module m : Modules.ALL) {
            if (!toggleAnim.containsKey(m.id)) {
                toggleAnim.put(m.id, m.enabled ? 1f : 0f);
            }
        }
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        AstucyConfig.flushNow();
        super.removed();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.001f, (nowNs - lastNs) / 1_000_000_000f));
        lastNs = nowNs;
        openAnim = Math.min(1f, openAnim + dt * 10f);

        computeUiScale();
        int mx = Math.round(mouseX / uiScale);
        int my = Math.round(mouseY / uiScale);

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1f);

        // Backdrop across full virtual canvas
        Ui.rect(0f, 0f, gw + 4f, gh + 4f, 0x8805070B);

        // Ambient emerald particles
        if (AstucyConfig.get().menu_particles) {
            int acc = Ui.accent();
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                px[i] += vx[i] * dt;
                py[i] += vy[i] * dt;
                if (py[i] < -0.05f) py[i] = 1.05f;
                if (px[i] < -0.05f) px[i] = 1.05f;
                if (px[i] > 1.05f) px[i] = -0.05f;
                Ui.disc(px[i] * gw, py[i] * gh, pr[i], Ui.withA(acc, 0.30f));
            }
        }

        // Main shell
        Ui.rrect(shX, shY, shW, shH, 12f, Ui.bgShell());
        Ui.rframe(shX, shY, shW, shH, 12f, 1f, Ui.borderSubtle());

        renderHeader(mx, my, dt);

        float bodyY = shY + topH + 1f;
        float bodyH = shH - topH - 12f;

        if (activeTab == TAB_MODULES && optionsModule == null) {
            renderSidebar(shX, bodyY, bodyH, mx, my);
            float mainX = shX + sideW + 14f;
            float mainW = shW - sideW - 28f;
            Ui.scissorPush(mainX - 4f, bodyY, mainW + 8f, bodyH, client.height, uiDensity);
            renderModulesTab(mainX, bodyY + 8f, mainW, bodyH - 8f, mx, my, dt);
            Ui.scissorPop();
        } else {
            float bodyX = shX + 18f;
            float bodyW = shW - 36f;
            Ui.scissorPush(shX + 6f, bodyY, shW - 12f, bodyH, client.height, uiDensity);
            if (activeTab == TAB_MODULES) {
                renderModuleOptions(bodyX, bodyY + 8f, bodyW, bodyH - 8f, mx, my);
            } else if (activeTab == TAB_MODS) {
                renderModsTab(bodyX, bodyY + 8f, bodyW, bodyH - 8f, mx, my);
            } else {
                renderSettingsTab(bodyX, bodyY + 8f, bodyW, bodyH - 8f, mx, my);
            }
            Ui.scissorPop();
        }

        accountMenu.render(shX + shW - 14f, shY + 13f, mx, my);

        GlStateManager.popMatrix();

        if (!Mouse.isButtonDown(0)) {
            dragSlider = -1;
            dragWheel = false;
        }
        if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) {
            paintMode = 0;
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    private void renderHeader(int mx, int my, float dt) {
        float ang = (System.currentTimeMillis() % 6000L) / 6000f * 360f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(shX + 26f, shY + topH * 0.5f, 0f);
        GlStateManager.rotate(ang, 0f, 0f, 1f);
        Skin.drawTex(Skin.logoIris(38, Ui.accent()), -9f, -9f, 18f, 18f, 0xFFFFFFFF);
        GlStateManager.popMatrix();
        Ui.textS("ASTUCY", shX + 41f, shY + 18f, Ui.fgPrimary());

        String[] tabs = { "MODULES", "MODS", "SETTINGS" };
        float tx = shX + Math.max(122f, sideW + 14f);
        float targetX = tx;
        float targetW = 54f;
        for (int i = 0; i < tabs.length; i++) {
            float tw = Ui.tw(tabs[i]) + 18f;
            boolean active = activeTab == i;
            boolean hov = Ui.hit(mx, my, tx, shY + 11f, tw, 22f);
            int tc = active ? Ui.fgPrimary() : (hov ? Ui.fgPrimary() : Ui.fgMuted());
            Ui.ctext(tabs[i], tx + tw * 0.5f, shY + 18f, tc, false);
            if (active) {
                targetX = tx + 4f;
                targetW = tw - 8f;
            }
            tx += tw + 6f;
        }
        if (tabIndicatorX <= 0f) tabIndicatorX = targetX;
        tabIndicatorX += (targetX - tabIndicatorX) * Math.min(1f, dt * 16f);
        tabIndicatorW += (targetW - tabIndicatorW) * Math.min(1f, dt * 16f);
        Ui.rrect(tabIndicatorX, shY + topH - 4f, tabIndicatorW, 2f, 1f, Ui.accent());

        float accLeft = shX + shW - 14f - Math.max(76f, Ui.tw(Accounts.activeName()) + 26f) - 8f;
        float themeW = 46f;
        float themeX = accLeft - themeW;
        boolean hovTheme = Ui.hit(mx, my, themeX, shY + 13f, themeW, 18f);
        Ui.rrect(themeX, shY + 13f, themeW, 18f, 5f, hovTheme ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rframe(themeX, shY + 13f, themeW, 18f, 5f, 1f, Ui.borderSubtle());
        Ui.ctext(AstucyConfig.get().light_mode ? "Light" : "Dark", themeX + themeW * 0.5f, shY + 18f, Ui.fgPrimary(), false);

        float hudW = 68f;
        float hudX = themeX - hudW - 6f;
        boolean hovHud = Ui.hit(mx, my, hudX, shY + 13f, hudW, 18f);
        Ui.rrect(hudX, shY + 13f, hudW, 18f, 5f, hovHud ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rframe(hudX, shY + 13f, hudW, 18f, 5f, 1f, Ui.borderSubtle());
        Ui.ctext("HUD Edit", hudX + hudW * 0.5f, shY + 18f, Ui.accent(), false);

        Ui.rect(shX + 12f, shY + topH, shW - 24f, 1f, Ui.borderSubtle());
    }

    private void renderSidebar(float shellX, float y, float h, int mx, int my) {
        Ui.rect(shellX + sideW, y + 6f, 1f, h - 6f, Ui.borderSubtle());
        float sx = shellX + 12f;
        float sw = sideW - 22f;
        float cy = y + 10f;

        Ui.scaledText("CATEGORIES", sx + 4f, cy, 0.8f, Ui.fgDim(), false);
        cy += 14f;

        cy += drawSidePill(sx, cy, sw, "All Modules", selectedCat == null, mx, my) + 4f;
        for (ModuleCategory cat : ModuleCategory.values()) {
            cy += drawSidePill(sx, cy, sw, cat.label, selectedCat == cat, mx, my) + 4f;
        }

        cy += 10f;
        Ui.rect(sx, cy, sw, 1f, Ui.borderSubtle());
        cy += 10f;
        Ui.scaledText("TOOLS", sx + 4f, cy, 0.8f, Ui.fgDim(), false);
        cy += 14f;

        boolean hovHud = Ui.hit(mx, my, sx, cy, sw, 22f);
        Ui.rrect(sx, cy, sw, 22f, 5f, hovHud ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rframe(sx, cy, sw, 22f, 5f, 1f, Ui.borderSubtle());
        Ui.text("Edit HUD Layout", sx + 8f, cy + 7f, Ui.accent());
        cy += 26f;

        boolean hovVid = Ui.hit(mx, my, sx, cy, sw, 22f);
        Ui.rrect(sx, cy, sw, 22f, 5f, hovVid ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rframe(sx, cy, sw, 22f, 5f, 1f, Ui.borderSubtle());
        Ui.text("Video Settings", sx + 8f, cy + 7f, Ui.fgPrimary());
    }

    private float drawSidePill(float x, float y, float w, String label, boolean active, int mx, int my) {
        float ph = 22f;
        boolean hov = Ui.hit(mx, my, x, y, w, ph);
        int bg = active ? Ui.withA(Ui.accent(), 0.20f) : (hov ? Ui.bgCardHover() : 0x00000000);
        if ((bg >>> 24) > 0) {
            Ui.rrect(x, y, w, ph, 5f, bg);
        }
        if (active) {
            Ui.rrect(x + 2f, y + 5f, 2.5f, ph - 10f, 1f, Ui.accent());
        }
        Ui.text(label, x + 10f, y + 7f, active ? Ui.fgPrimary() : (hov ? Ui.fgPrimary() : Ui.fgMuted()));
        return ph;
    }

    private void renderModulesTab(float x, float y, float w, float h, int mx, int my, float dt) {
        float searchW = Math.min(250f, w - 100f);
        Ui.rrect(x, y, searchW, 22f, 6f, Ui.bgInput());
        Ui.rframe(x, y, searchW, 22f, 6f, 1f, searchFocused ? Ui.accent() : Ui.borderSubtle());
        String sDisp = searchQuery.isEmpty()
            ? (searchFocused ? "|" : "Search modules...")
            : (searchQuery + (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : ""));
        Ui.text(sDisp, x + 8f, y + 7f, searchQuery.isEmpty() ? Ui.fgDim() : Ui.fgPrimary());

        List<Module> list = filteredModules();
        String countTxt = list.size() + " modules";
        Ui.text(countTxt, x + w - Ui.tw(countTxt) - 8f, y + 7f, Ui.fgMuted());

        float gridY = y + 30f;
        float gridH = h - 30f;
        int cols = w >= 420f ? 3 : 2;
        float gap = 8f;
        float cardW = (w - 10f - gap * (cols - 1)) / cols;
        float cardH = AstucyConfig.get().compact_cards ? 52f : 64f;
        int rows = (list.size() + cols - 1) / cols;
        float contentH = rows * (cardH + gap);
        scrollModules = Ui.clamp(scrollModules, 0f, Math.max(0f, contentH - gridH));

        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int col = i % cols;
            int row = i / cols;
            float rx = x + col * (cardW + gap);
            float ry = gridY + row * (cardH + gap) - scrollModules;
            if (ry + cardH < gridY || ry > gridY + gridH) continue;

            Float prevA = toggleAnim.get(m.id);
            float anim = prevA != null ? prevA.floatValue() : (m.enabled ? 1f : 0f);
            anim += ((m.enabled ? 1f : 0f) - anim) * Math.min(1f, dt * 14f);
            toggleAnim.put(m.id, anim);

            boolean hov = Ui.hit(mx, my, rx, ry, cardW, cardH);
            int bg = m.enabled ? Ui.mix(Ui.bgCard(), Ui.withA(Ui.accent(), 0.16f), 0.7f) : (hov ? Ui.bgCardHover() : Ui.bgCard());
            Ui.rrect(rx, ry, cardW, cardH, 7f, bg);
            Ui.rframe(rx, ry, cardW, cardH, 7f, 1f, m.enabled ? Ui.withA(Ui.accent(), 0.55f) : Ui.borderSubtle());

            Ui.text(m.name, rx + 26f, ry + 10f, Ui.fgPrimary());
            Skin.drawIcon(m.id, rx + 8f, ry + 7f, 14f, m.enabled ? Ui.accent() : Ui.fgMuted());
            if (m.isNew && anim < 0.3f) {
                float bx = rx + 26f + Ui.tw(m.name) + 5f;
                float bw = Ui.tw("NEW") * 0.7f + 6f;
                if (bx + bw < rx + cardW - 6f) {
                    Ui.rrect(bx, ry + 9f, bw, 10f, 3f, Ui.withA(Ui.accent(), 0.25f));
                    Ui.ctextScaled("NEW", bx + bw * 0.5f, ry + 11f, 0.7f, Ui.accent(), false);
                }
            }
            String shortDesc = trimTo(m.desc, cardW - 20f);
            Ui.scaledText(shortDesc, rx + 10f, ry + 25f, 0.85f, Ui.fgMuted(), false);

            Ui.toggle(rx + 10f, ry + cardH - 20f, 26f, 12f, anim, Ui.accent());

            float optW = 50f, optH = 15f;
            float ox = rx + cardW - optW - 9f;
            float oy = ry + cardH - 21f;
            boolean hovOpt = Ui.hit(mx, my, ox, oy, optW, optH);
            Ui.rrect(ox, oy, optW, optH, 4f, hovOpt ? Ui.bgCardHover() : Ui.bgInput());
            Ui.ctextScaled("Options", ox + optW * 0.5f, oy + 4f, 0.85f, Ui.fgMuted(), false);
        }

        Ui.scrollbar(x + w - 4f, gridY, gridH, gridH, contentH, scrollModules);
    }

    private void renderModuleOptions(float x, float y, float w, float h, int mx, int my) {
        Module m = optionsModule;
        boolean hovBack = Ui.hit(mx, my, x, y, 56f, 20f);
        Ui.rrect(x, y, 56f, 20f, 5f, hovBack ? Ui.bgCardHover() : Ui.bgCard());
        Ui.ctext("< Back", x + 28f, y + 6f, Ui.fgPrimary(), false);

        Ui.text(m.name + " Settings", x + 66f, y + 6f, Ui.fgPrimary());
        Ui.toggle(x + w - 34f, y + 4f, 26f, 12f, m.enabled ? 1f : 0f, Ui.accent());

        float cy = y + 30f - scrollOptions;
        float contentStart = cy;

        if ("crosshair".equals(m.id)) {
            cy = renderCrosshairEditor(x, cy, w, mx, my);
        }

        if (!m.props.isEmpty()) {
            Ui.text("Module Options", x, cy, Ui.accent());
            cy += 16f;
            float bx = x;
            for (Map.Entry<String, Object> e : m.props.entrySet()) {
                if (!(e.getValue() instanceof Boolean)) continue;
                boolean val = ((Boolean) e.getValue()).booleanValue();
                String lbl = formatPropName(e.getKey()) + ": " + (val ? "ON" : "OFF");
                float pw = Math.max(96f, Ui.tw(lbl) + 16f);
                if (bx + pw > x + w - 10f) {
                    bx = x;
                    cy += 24f;
                }
                boolean hov = Ui.hit(mx, my, bx, cy, pw, 20f);
                Ui.rrect(bx, cy, pw, 20f, 5f, val ? Ui.withA(Ui.accent(), 0.25f) : (hov ? Ui.bgCardHover() : Ui.bgCard()));
                Ui.rframe(bx, cy, pw, 20f, 5f, 1f, val ? Ui.accent() : Ui.borderSubtle());
                Ui.ctext(lbl, bx + pw * 0.5f, cy + 6f, Ui.fgPrimary(), false);
                bx += pw + 6f;
            }
            cy += 30f;
        }

        if (m.hud) {
            Ui.text("HUD Appearance", x, cy, Ui.accent());
            cy += 18f;
            float leftW = w * 0.54f;
            Ui.text("Scale (" + String.format(Locale.US, "%.2fx", m.scale) + ")", x, cy, Ui.fgPrimary());
            Ui.slider(x, cy + 12f, leftW, Ui.clamp((m.scale - 0.5f) / 1.5f, 0f, 1f), Ui.accent());
            if (dragSlider == 0 && Mouse.isButtonDown(0)) {
                m.scale = 0.5f + Ui.clamp((mx - x) / leftW, 0f, 1f) * 1.5f;
                AstucyConfig.markDirty();
            }

            Ui.text("Opacity (" + Math.round(m.opacity * 100f) + "%)", x, cy + 34f, Ui.fgPrimary());
            Ui.slider(x, cy + 46f, leftW, Ui.clamp((m.opacity - 0.1f) / 0.9f, 0f, 1f), Ui.accent());
            if (dragSlider == 1 && Mouse.isButtonDown(0)) {
                m.opacity = 0.1f + Ui.clamp((mx - x) / leftW, 0f, 1f) * 0.9f;
                AstucyConfig.markDirty();
            }

            Ui.text("Corner Radius (" + Math.round(m.radius) + "px)", x, cy + 68f, Ui.fgPrimary());
            Ui.slider(x, cy + 80f, leftW, Ui.clamp(m.radius / 10f, 0f, 1f), Ui.accent());
            if (dragSlider == 2 && Mouse.isButtonDown(0)) {
                m.radius = Ui.clamp((mx - x) / leftW, 0f, 1f) * 10f;
                AstucyConfig.markDirty();
            }

            float rx = x + leftW + 22f;
            drawSmallToggle(rx, cy + 6f, 112f, "Background", m.bg, mx, my);
            drawSmallToggle(rx, cy + 30f, 112f, "Border", m.border, mx, my);
            Ui.rrect(rx, cy + 56f, 112f, 20f, 5f, Ui.bgCard());
            Ui.ctext("Reset Style", rx + 56f, cy + 62f, Ui.fgMuted(), false);

            cy += 104f;

            Ui.text("Text Color Swatches", x, cy, Ui.fgPrimary());
            for (int i = 0; i < Module.SWATCHES.length; i++) {
                float sx = x + i * 24f;
                Ui.swatch(sx, cy + 16f, 18f, Module.SWATCHES[i], (m.fgColor & 0xFFFFFF) == (Module.SWATCHES[i] & 0xFFFFFF));
            }
            float wheelX = x + w - 104f;
            float wheelY = cy - 4f;
            Skin.drawTex(Skin.hueWheel(96), wheelX, wheelY, 88f, 88f, 0xFFFFFFFF);
            if (dragWheel && wheelTarget == 0 && Mouse.isButtonDown(0)) {
                int picked = pickWheelColor(wheelX, wheelY, 88f, mx, my);
                if (picked != 0) {
                    m.fgColor = picked;
                    AstucyConfig.markDirty();
                }
            }
            cy += 92f;
        }

        float totalH = cy - contentStart + 16f;
        float viewH = h - 30f;
        scrollOptions = Ui.clamp(scrollOptions, 0f, Math.max(0f, totalH - viewH));
        Ui.scrollbar(x + w - 4f, y + 30f, viewH, viewH, totalH, scrollOptions);
    }

    private float renderCrosshairEditor(float x, float y, float w, int mx, int my) {
        AstucyConfig cfg = AstucyConfig.get();
        Ui.text("17x17 Crosshair Painter (LMB Draw / RMB Erase)", x, y, Ui.accent());
        y += 16f;

        float cell = 10f;
        float gridSize = 17f * cell;
        Ui.rrect(x - 3f, y - 3f, gridSize + 6f, gridSize + 6f, 5f, Ui.bgInput());
        for (int r = 0; r < 17; r++) {
            for (int c = 0; c < 17; c++) {
                float cx = x + c * cell;
                float cy = y + r * cell;
                boolean on = (cfg.crosshair_grid[r] & (1L << c)) != 0L;
                int cellCol = on ? cfg.crosshair_color : ((r == 8 && c == 8) ? 0x33FFFFFF : (((r + c) & 1) == 0 ? 0xFF161B24 : 0xFF12161E));
                Ui.rect(cx, cy, cell - 0.5f, cell - 0.5f, cellCol);
                if (paintMode > 0 && Ui.hit(mx, my, cx, cy, cell, cell)) {
                    if (paintMode == 1) cfg.crosshair_grid[r] |= (1L << c);
                    else cfg.crosshair_grid[r] &= ~(1L << c);
                    AstucyConfig.markDirty();
                }
            }
        }

        float px = x + gridSize + 22f;
        String[] presets = { "Cross", "Dot", "Gap", "X", "Clear" };
        for (int i = 0; i < presets.length; i++) {
            float bx = px + (i % 3) * 62f;
            float by = y + (i / 3) * 24f;
            boolean hov = Ui.hit(mx, my, bx, by, 56f, 20f);
            Ui.rrect(bx, by, 56f, 20f, 5f, hov ? Ui.bgCardHover() : Ui.bgCard());
            Ui.rframe(bx, by, 56f, 20f, 5f, 1f, Ui.borderSubtle());
            Ui.ctext(presets[i], bx + 28f, by + 6f, Ui.fgPrimary(), false);
        }

        float invY = y + 54f;
        drawSmallToggle(px, invY, 118f, "Invert Blend", cfg.crosshair_invert, mx, my);

        float prevX = px + 128f;
        Ui.rrect(prevX, invY, 52f, 52f, 6f, 0xFF2B3545);
        for (int r = 0; r < 17; r++) {
            for (int c = 0; c < 17; c++) {
                if ((cfg.crosshair_grid[r] & (1L << c)) != 0L) {
                    Ui.rect(prevX + 18f + c, invY + 18f + r, 1f, 1f, cfg.crosshair_color);
                }
            }
        }

        Ui.text("Crosshair Color", px, invY + 30f, Ui.fgMuted());
        for (int i = 0; i < Module.SWATCHES.length; i++) {
            float sx = px + (i % 4) * 26f;
            float sy = invY + 44f + (i / 4) * 24f;
            Ui.swatch(sx, sy, 18f, Module.SWATCHES[i], (cfg.crosshair_color & 0xFFFFFF) == (Module.SWATCHES[i] & 0xFFFFFF));
        }

        return y + gridSize + 18f;
    }

    private void renderModsTab(float x, float y, float w, float h, int mx, int my) {
        Ui.text("Installed Legacy Fabric Mods (" + modList.size() + ")", x, y, Ui.accent());
        float listY = y + 18f;
        float listH = h - 18f;
        float rowH = 38f;
        float contentH = modList.size() * (rowH + 6f);
        scrollMods = Ui.clamp(scrollMods, 0f, Math.max(0f, contentH - listH));

        for (int i = 0; i < modList.size(); i++) {
            ModContainer mcMod = modList.get(i);
            float ry = listY + i * (rowH + 6f) - scrollMods;
            if (ry + rowH < listY || ry > listY + listH) continue;

            Ui.rrect(x, ry, w - 10f, rowH, 6f, Ui.bgCard());
            Ui.rframe(x, ry, w - 10f, rowH, 6f, 1f, Ui.borderSubtle());
            String title = mcMod.getMetadata().getName();
            String ver = mcMod.getMetadata().getVersion().getFriendlyString();
            String id = mcMod.getMetadata().getId();
            Ui.text(title, x + 12f, ry + 7f, Ui.fgPrimary());
            Ui.scaledText(id + " v" + ver, x + 12f, ry + 21f, 0.85f, Ui.fgMuted(), false);
        }
        Ui.scrollbar(x + w - 4f, listY, listH, listH, contentH, scrollMods);
    }

    private void renderSettingsTab(float x, float y, float w, float h, int mx, int my) {
        AstucyConfig cfg = AstucyConfig.get();
        float cy = y - scrollSettings;
        float startY = cy;

        Ui.text("Astucy Client Preferences", x, cy, Ui.accent());
        cy += 18f;
        drawSmallToggle(x, cy, 156f, "Light Theme", cfg.light_mode, mx, my);
        drawSmallToggle(x + 164f, cy, 156f, "Menu Particles", cfg.menu_particles, mx, my);
        drawSmallToggle(x + 328f, cy, 156f, "HUD Snapping", cfg.hud_snapping, mx, my);
        cy += 28f;
        drawSmallToggle(x, cy, 156f, "Compact Cards", cfg.compact_cards, mx, my);

        float vsx = x + 164f;
        boolean hovVs = Ui.hit(mx, my, vsx, cy, 156f, 20f);
        Ui.rrect(vsx, cy, 156f, 20f, 5f, hovVs ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rframe(vsx, cy, 156f, 20f, 5f, 1f, Ui.borderSubtle());
        Ui.ctext("Client Video Settings >", vsx + 78f, cy + 6f, Ui.accent(), false);
        cy += 34f;

        Ui.text("Profiles", x, cy, Ui.accent());
        cy += 16f;
        for (int i = 0; i < 3 && i < cfg.profile_names.size(); i++) {
            float px = x + i * 124f;
            boolean active = cfg.active_profile == i;
            Ui.rrect(px, cy, 114f, 22f, 5f, active ? Ui.accent() : Ui.bgCard());
            Ui.ctext(cfg.profile_names.get(i), px + 57f, cy + 7f, active ? 0xFF0A0D12 : Ui.fgPrimary(), false);
        }
        cy += 36f;

        Ui.text("Menu Accent Color", x, cy, Ui.accent());
        cy += 18f;
        for (int i = 0; i < Module.SWATCHES.length; i++) {
            Ui.swatch(x + i * 26f, cy, 18f, Module.SWATCHES[i], (cfg.menu_accent & 0xFFFFFF) == (Module.SWATCHES[i] & 0xFFFFFF));
        }
        float wheelX = x + w - 104f;
        float wheelY = cy - 16f;
        Skin.drawTex(Skin.hueWheel(96), wheelX, wheelY, 88f, 88f, 0xFFFFFFFF);
        if (dragWheel && wheelTarget == 2 && Mouse.isButtonDown(0)) {
            int picked = pickWheelColor(wheelX, wheelY, 88f, mx, my);
            if (picked != 0) {
                cfg.menu_accent = picked;
                AstucyConfig.markDirty();
            }
        }
        cy += 92f;

        float contentH = cy - startY;
        scrollSettings = Ui.clamp(scrollSettings, 0f, Math.max(0f, contentH - h));
        Ui.scrollbar(x + w - 4f, y, h, h, contentH, scrollSettings);
    }

    private void drawSmallToggle(float x, float y, float w, String label, boolean on, int mx, int my) {
        boolean hov = Ui.hit(mx, my, x, y, w, 20f);
        Ui.rrect(x, y, w, 20f, 5f, on ? Ui.withA(Ui.accent(), 0.24f) : (hov ? Ui.bgCardHover() : Ui.bgCard()));
        Ui.rframe(x, y, w, 20f, 5f, 1f, on ? Ui.accent() : Ui.borderSubtle());
        Ui.text(label, x + 8f, y + 6f, Ui.fgPrimary());
        Ui.disc(x + w - 11f, y + 10f, 3.5f, on ? Ui.accent() : Ui.fgDim());
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            float delta = dw > 0 ? -24f : 24f;
            if (activeTab == TAB_MODULES) {
                if (optionsModule != null) scrollOptions += delta;
                else scrollModules += delta;
            } else if (activeTab == TAB_MODS) {
                scrollMods += delta;
            } else {
                scrollSettings += delta;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        computeUiScale();
        int mx = Math.round(mouseX / uiScale);
        int my = Math.round(mouseY / uiScale);

        if (accountMenu.mouseClicked(mx, my, button)) return;

        // Header clicks
        if (button == 0 && Ui.hit(mx, my, shX, shY, shW, topH)) {
            String[] tabs = { "MODULES", "MODS", "SETTINGS" };
            float tx = shX + Math.max(122f, sideW + 14f);
            for (int i = 0; i < tabs.length; i++) {
                float tw = Ui.tw(tabs[i]) + 18f;
                if (Ui.hit(mx, my, tx, shY + 11f, tw, 22f)) {
                    activeTab = i;
                    optionsModule = null;
                    searchFocused = false;
                    return;
                }
                tx += tw + 6f;
            }

            float accLeft = shX + shW - 14f - Math.max(76f, Ui.tw(Accounts.activeName()) + 26f) - 8f;
            float themeW = 46f, themeX = accLeft - themeW;
            if (Ui.hit(mx, my, themeX, shY + 13f, themeW, 18f)) {
                AstucyConfig.get().light_mode = !AstucyConfig.get().light_mode;
                AstucyConfig.markDirty();
                return;
            }
            float hudW = 68f, hudX = themeX - hudW - 6f;
            if (Ui.hit(mx, my, hudX, shY + 13f, hudW, 18f)) {
                AstucyConfig.flushNow();
                client.setScreen(new HudEditorScreen(this));
                return;
            }
        }

        float bodyY = shY + topH + 1f;
        float bodyH = shH - topH - 12f;

        if (activeTab == TAB_MODULES && optionsModule == null) {
            if (button == 0 && Ui.hit(mx, my, shX, bodyY, sideW, bodyH)) {
                clickSidebar(shX, bodyY, mx, my);
                return;
            }
            float mainX = shX + sideW + 14f;
            float mainW = shW - sideW - 28f;
            if (button == 0 && Ui.hit(mx, my, mainX, bodyY, mainW, bodyH)) {
                clickModulesGrid(mainX, bodyY + 8f, mainW, bodyH - 8f, mx, my);
            }
            return;
        }

        float bodyX = shX + 18f, bodyW = shW - 36f;
        if (!Ui.hit(mx, my, bodyX, bodyY, bodyW, bodyH)) return;

        if (activeTab == TAB_MODULES) {
            clickModuleOptions(bodyX, bodyY + 8f, bodyW, mx, my, button);
        } else if (activeTab == TAB_SETTINGS && button == 0) {
            clickSettings(bodyX, bodyY + 8f, bodyW, mx, my);
        }
    }

    private void clickSidebar(float shellX, float y, int mx, int my) {
        float sx = shellX + 12f;
        float sw = sideW - 22f;
        float cy = y + 24f;
        if (Ui.hit(mx, my, sx, cy, sw, 22f)) {
            selectedCat = null;
            scrollModules = 0f;
            return;
        }
        cy += 26f;
        for (ModuleCategory cat : ModuleCategory.values()) {
            if (Ui.hit(mx, my, sx, cy, sw, 22f)) {
                selectedCat = cat;
                scrollModules = 0f;
                return;
            }
            cy += 26f;
        }
        cy += 34f;
        if (Ui.hit(mx, my, sx, cy, sw, 22f)) {
            AstucyConfig.flushNow();
            client.setScreen(new HudEditorScreen(this));
            return;
        }
        cy += 26f;
        if (Ui.hit(mx, my, sx, cy, sw, 22f)) {
            client.setScreen(new ClientSettingsScreen(this));
        }
    }

    private void clickModulesGrid(float x, float y, float w, float h, int mx, int my) {
        float searchW = Math.min(250f, w - 100f);
        if (Ui.hit(mx, my, x, y, searchW, 22f)) {
            searchFocused = true;
            return;
        }
        searchFocused = false;

        List<Module> list = filteredModules();
        float gridY = y + 30f, gridH = h - 30f;
        int cols = w >= 420f ? 3 : 2;
        float gap = 8f;
        float cardW = (w - 10f - gap * (cols - 1)) / cols;
        float cardH = AstucyConfig.get().compact_cards ? 52f : 64f;

        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            float rx = x + (i % cols) * (cardW + gap);
            float ry = gridY + (i / cols) * (cardH + gap) - scrollModules;
            if (ry + cardH < gridY || ry > gridY + gridH) continue;
            if (!Ui.hit(mx, my, rx, ry, cardW, cardH)) continue;

            float optW = 50f, optH = 15f;
            float ox = rx + cardW - optW - 9f, oy = ry + cardH - 21f;
            if (Ui.hit(mx, my, ox, oy, optW, optH)) {
                openModuleOptions(m);
                return;
            }
            m.enabled = !m.enabled;
            AstucyConfig.markDirty();
            return;
        }
    }

    private void clickModuleOptions(float x, float y, float w, int mx, int my, int button) {
        Module m = optionsModule;
        if (button == 0 && Ui.hit(mx, my, x, y, 56f, 20f)) {
            optionsModule = null;
            return;
        }
        if (button == 0 && Ui.hit(mx, my, x + w - 40f, y, 40f, 20f)) {
            m.enabled = !m.enabled;
            AstucyConfig.markDirty();
            return;
        }

        float cy = y + 30f - scrollOptions;
        if ("crosshair".equals(m.id)) {
            float gy = cy + 16f;
            float cell = 10f;
            float gridSize = 17f * cell;
            if (Ui.hit(mx, my, x, gy, gridSize, gridSize)) {
                paintMode = (button == 1) ? 2 : 1;
                return;
            }
            float px = x + gridSize + 22f;
            String[] presets = { "Cross", "Dot", "Gap", "X", "Clear" };
            if (button == 0) {
                for (int i = 0; i < presets.length; i++) {
                    float bx = px + (i % 3) * 62f;
                    float by = gy + (i / 3) * 24f;
                    if (Ui.hit(mx, my, bx, by, 56f, 20f)) {
                        AstucyConfig.applyCrossPreset(presets[i]);
                        return;
                    }
                }
                float invY = gy + 54f;
                if (Ui.hit(mx, my, px, invY, 118f, 20f)) {
                    AstucyConfig.get().crosshair_invert = !AstucyConfig.get().crosshair_invert;
                    AstucyConfig.markDirty();
                    return;
                }
                for (int i = 0; i < Module.SWATCHES.length; i++) {
                    float sx = px + (i % 4) * 26f;
                    float sy = invY + 44f + (i / 4) * 24f;
                    if (Ui.hit(mx, my, sx, sy, 18f, 18f)) {
                        AstucyConfig.get().crosshair_color = Module.SWATCHES[i];
                        AstucyConfig.markDirty();
                        return;
                    }
                }
            }
            cy = gy + gridSize + 18f;
        }

        if (button != 0) return;

        if (!m.props.isEmpty()) {
            cy += 16f;
            float bx = x;
            for (Map.Entry<String, Object> e : m.props.entrySet()) {
                if (!(e.getValue() instanceof Boolean)) continue;
                boolean val = ((Boolean) e.getValue()).booleanValue();
                String lbl = formatPropName(e.getKey()) + ": " + (val ? "ON" : "OFF");
                float pw = Math.max(96f, Ui.tw(lbl) + 16f);
                if (bx + pw > x + w - 10f) {
                    bx = x;
                    cy += 24f;
                }
                if (Ui.hit(mx, my, bx, cy, pw, 20f)) {
                    e.setValue(!val);
                    AstucyConfig.markDirty();
                    return;
                }
                bx += pw + 6f;
            }
            cy += 30f;
        }

        if (m.hud) {
            cy += 18f;
            float leftW = w * 0.54f;
            if (Ui.hit(mx, my, x - 4f, cy + 8f, leftW + 8f, 16f)) { dragSlider = 0; return; }
            if (Ui.hit(mx, my, x - 4f, cy + 42f, leftW + 8f, 16f)) { dragSlider = 1; return; }
            if (Ui.hit(mx, my, x - 4f, cy + 76f, leftW + 8f, 16f)) { dragSlider = 2; return; }

            float rx = x + leftW + 22f;
            if (Ui.hit(mx, my, rx, cy + 6f, 112f, 20f)) { m.bg = !m.bg; AstucyConfig.markDirty(); return; }
            if (Ui.hit(mx, my, rx, cy + 30f, 112f, 20f)) { m.border = !m.border; AstucyConfig.markDirty(); return; }
            if (Ui.hit(mx, my, rx, cy + 56f, 112f, 20f)) { m.resetStyle(); AstucyConfig.markDirty(); return; }

            cy += 104f;
            for (int i = 0; i < Module.SWATCHES.length; i++) {
                if (Ui.hit(mx, my, x + i * 24f, cy + 16f, 18f, 18f)) {
                    m.fgColor = Module.SWATCHES[i];
                    AstucyConfig.markDirty();
                    return;
                }
            }
            float wheelX = x + w - 104f, wheelY = cy - 4f;
            if (Ui.hit(mx, my, wheelX, wheelY, 88f, 88f)) {
                wheelTarget = 0;
                dragWheel = true;
            }
        }
    }

    private void clickSettings(float x, float y, float w, int mx, int my) {
        AstucyConfig cfg = AstucyConfig.get();
        float cy = y - scrollSettings + 18f;
        if (Ui.hit(mx, my, x, cy, 156f, 20f)) { cfg.light_mode = !cfg.light_mode; AstucyConfig.markDirty(); return; }
        if (Ui.hit(mx, my, x + 164f, cy, 156f, 20f)) { cfg.menu_particles = !cfg.menu_particles; AstucyConfig.markDirty(); return; }
        if (Ui.hit(mx, my, x + 328f, cy, 156f, 20f)) { cfg.hud_snapping = !cfg.hud_snapping; AstucyConfig.markDirty(); return; }
        cy += 28f;
        if (Ui.hit(mx, my, x, cy, 156f, 20f)) { cfg.compact_cards = !cfg.compact_cards; AstucyConfig.markDirty(); return; }
        if (Ui.hit(mx, my, x + 164f, cy, 156f, 20f)) { client.setScreen(new ClientSettingsScreen(this)); return; }
        cy += 50f;
        for (int i = 0; i < 3; i++) {
            if (Ui.hit(mx, my, x + i * 124f, cy, 114f, 22f)) {
                AstucyConfig.switchProfile(i);
                return;
            }
        }
        cy += 54f;
        for (int i = 0; i < Module.SWATCHES.length; i++) {
            if (Ui.hit(mx, my, x + i * 26f, cy, 18f, 18f)) {
                cfg.menu_accent = Module.SWATCHES[i];
                AstucyConfig.markDirty();
                return;
            }
        }
        float wheelX = x + w - 104f, wheelY = cy - 16f;
        if (Ui.hit(mx, my, wheelX, wheelY, 88f, 88f)) {
            wheelTarget = 2;
            dragWheel = true;
        }
    }

    private int pickWheelColor(float wx, float wy, float size, int mx, int my) {
        float r = size * 0.5f;
        float dx = mx - (wx + r);
        float dy = my - (wy + r);
        float d = (float) Math.hypot(dx, dy);
        if (d > r) return 0;
        float sat = Math.min(1f, d / r);
        float hue = (float) ((Math.atan2(dy, dx) / (2.0 * Math.PI)) + 0.5);
        return Color.HSBtoRGB(hue, sat, 1f) | 0xFF000000;
    }

    @Override
    protected void keyPressed(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (optionsModule != null) {
                optionsModule = null;
                return;
            }
            if (searchFocused) {
                searchFocused = false;
                return;
            }
            client.setScreen(null);
            return;
        }
        if (searchFocused && activeTab == TAB_MODULES && optionsModule == null) {
            if (keyCode == Keyboard.KEY_BACK) {
                if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return;
            }
            if (typedChar >= ' ' && typedChar != 127 && searchQuery.length() < 28) {
                searchQuery += typedChar;
                return;
            }
        }
        super.keyPressed(typedChar, keyCode);
    }

    private List<Module> filteredModules() {
        List<Module> out = new ArrayList<Module>();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        for (Module m : Modules.ALL) {
            if (selectedCat != null && m.category != selectedCat) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.desc.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String trimTo(String s, float maxW) {
        if (s == null || Ui.tw(s) * 0.85f <= maxW) return s;
        String t = s;
        while (t.length() > 3 && Ui.tw(t + "...") * 0.85f > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    private static String formatPropName(String k) {
        if ("percent".equals(k)) return "Show %";
        if ("bar".equals(k)) return "Durability Bar";
        if ("prefix".equals(k)) return "Slot Prefix";
        if ("horizontal".equals(k)) return "Horizontal";
        if ("low_blink".equals(k)) return "Low Blink";
        if ("rmb".equals(k)) return "Show RMB";
        if ("mouse".equals(k)) return "Mouse Keys";
        if ("space".equals(k)) return "Spacebar";
        if ("cps".equals(k)) return "Show CPS";
        if ("sens_scale".equals(k)) return "Scale Sens";
        if ("smooth".equals(k)) return "Smooth";
        return k;
    }
}
