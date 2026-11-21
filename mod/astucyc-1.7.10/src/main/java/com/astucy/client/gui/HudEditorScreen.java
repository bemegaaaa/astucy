package com.astucy.client.gui;

import com.astucy.client.config.AstucyConfig;
import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import com.astucy.client.ui.Ui;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    private Module selected = null;
    private Module dragging = null;
    private float dragOffX, dragOffY;
    private boolean snapLineX = false;
    private boolean snapLineY = false;

    public HudEditorScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        Ui.rect(0f, 0f, width, height, 0x5506080C);

        if (snapLineX) Ui.rect(width * 0.5f, 0f, 1f, height, Ui.withA(Ui.accent(), 0.75f));
        if (snapLineY) Ui.rect(0f, height * 0.5f, width, 1f, Ui.withA(Ui.accent(), 0.75f));

        if (dragging != null && Mouse.isButtonDown(0)) {
            float nx = mouseX - dragOffX;
            float ny = mouseY - dragOffY;
            snapLineX = false;
            snapLineY = false;
            if (AstucyConfig.get().hud_snapping) {
                float cx = nx + dragging.lastW * 0.5f;
                float cy = ny + dragging.lastH * 0.5f;
                if (Math.abs(cx - width * 0.5f) < 5f) {
                    nx = width * 0.5f - dragging.lastW * 0.5f;
                    snapLineX = true;
                }
                if (Math.abs(cy - height * 0.5f) < 5f) {
                    ny = height * 0.5f - dragging.lastH * 0.5f;
                    snapLineY = true;
                }
                if (Math.abs(nx - 8f) < 4f) nx = 8f;
                if (Math.abs(ny - 8f) < 4f) ny = 8f;
                if (Math.abs(nx + dragging.lastW - (width - 8f)) < 4f) nx = width - 8f - dragging.lastW;
                if (Math.abs(ny + dragging.lastH - (height - 8f)) < 4f) ny = height - 8f - dragging.lastH;
            }
            dragging.anchorX = 0f;
            dragging.anchorY = 0f;
            dragging.x = Ui.clamp(nx, 2f, Math.max(2f, width - dragging.lastW - 2f));
            dragging.y = Ui.clamp(ny, 2f, Math.max(2f, height - dragging.lastH - 2f));
            AstucyConfig.markDirty();
        } else if (!Mouse.isButtonDown(0)) {
            dragging = null;
            snapLineX = false;
            snapLineY = false;
        }

        for (Module m : Modules.ALL) {
            if (!m.hud || !m.enabled) continue;
            Modules.renderModule(client, m, width, height, true);

            boolean hov = Ui.hit(mouseX, mouseY, m.lastX, m.lastY, m.lastW, m.lastH);
            boolean sel = m == selected || m == dragging;
            int frameCol = sel ? Ui.accent() : (hov ? 0xAAFFFFFF : 0x44FFFFFF);
            Ui.frame(m.lastX - 1f, m.lastY - 1f, m.lastW + 2f, m.lastH + 2f, 1f, frameCol);

            if (sel || hov) {
                float gx = m.lastX + m.lastW - 10f;
                float gy = Math.max(2f, m.lastY - 11f);
                Ui.rrect(gx, gy, 10f, 10f, 3f, Ui.accent());
                Ui.ctextScaled("*", gx + 5f, gy + 2f, 0.9f, 0xFF0A0D12, false);
            }
        }

        float barW = 248f, barH = 26f;
        float barX = (width - barW) * 0.5f, barY = height - 36f;
        Ui.rrect(barX, barY, barW, barH, 8f, Ui.bgShell());
        Ui.rframe(barX, barY, barW, barH, 8f, 1f, Ui.borderSubtle());

        float b1x = barX + 6f, b1w = 74f;
        float b2x = b1x + b1w + 6f, b2w = 78f;
        float b3x = b2x + b2w + 6f, b3w = 72f;
        float by = barY + 4f;

        drawBarBtn(b1x, by, b1w, 18f, "Menu", Ui.hit(mouseX, mouseY, b1x, by, b1w, 18f), false);
        drawBarBtn(b2x, by, b2w, 18f, "Reset Pos", Ui.hit(mouseX, mouseY, b2x, by, b2w, 18f), false);
        drawBarBtn(b3x, by, b3w, 18f, "Done", Ui.hit(mouseX, mouseY, b3x, by, b3w, 18f), true);

        String hint = selected != null
            ? (selected.name + "  |  Scroll: Scale (" + String.format("%.2fx", selected.scale) + ")  |  Right-Click: Options")
            : "Drag HUD modules  |  Arrows: Nudge  |  Right-Click: Options";
        Ui.ctextScaled(hint, width * 0.5f, barY - 13f, 0.85f, 0xFFDCE4F0, true);

        super.render(mouseX, mouseY, tickDelta);
    }

    private void drawBarBtn(float x, float y, float w, float h, String txt, boolean hov, boolean primary) {
        int bg = primary ? Ui.accent() : (hov ? Ui.bgCardHover() : Ui.bgCard());
        int fg = primary ? 0xFF0A0D12 : Ui.fgPrimary();
        Ui.rrect(x, y, w, h, 5f, bg);
        Ui.ctext(txt, x + w * 0.5f, y + 5f, fg, false);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int dw = Mouse.getEventDWheel();
        if (dw != 0 && selected != null) {
            float delta = dw > 0 ? 0.05f : -0.05f;
            selected.scale = Ui.clamp(selected.scale + delta, 0.5f, 2.5f);
            AstucyConfig.markDirty();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        float barW = 248f, barX = (width - barW) * 0.5f, barY = height - 36f, by = barY + 4f;
        float b1x = barX + 6f, b1w = 74f;
        float b2x = b1x + b1w + 6f, b2w = 78f;
        float b3x = b2x + b2w + 6f, b3w = 72f;
        if (button == 0) {
            if (Ui.hit(mouseX, mouseY, b1x, by, b1w, 18f)) {
                AstucyConfig.flushNow();
                client.setScreen(new RShiftScreen());
                return;
            }
            if (Ui.hit(mouseX, mouseY, b2x, by, b2w, 18f)) {
                resetPositions();
                return;
            }
            if (Ui.hit(mouseX, mouseY, b3x, by, b3w, 18f)) {
                AstucyConfig.flushNow();
                client.setScreen(parent);
                return;
            }
        }

        for (int i = Modules.ALL.size() - 1; i >= 0; i--) {
            Module m = Modules.ALL.get(i);
            if (!m.hud || !m.enabled) continue;
            float gx = m.lastX + m.lastW - 10f;
            float gy = Math.max(2f, m.lastY - 11f);
            if (button == 0 && Ui.hit(mouseX, mouseY, gx, gy, 10f, 10f)) {
                RShiftScreen menu = new RShiftScreen();
                menu.openModuleOptions(m);
                client.setScreen(menu);
                return;
            }
            if (Ui.hit(mouseX, mouseY, m.lastX, m.lastY, m.lastW, m.lastH)) {
                selected = m;
                if (button == 1) {
                    RShiftScreen menu = new RShiftScreen();
                    menu.openModuleOptions(m);
                    client.setScreen(menu);
                    return;
                }
                if (button == 0) {
                    dragging = m;
                    dragOffX = mouseX - m.lastX;
                    dragOffY = mouseY - m.lastY;
                }
                return;
            }
        }
        selected = null;
    }

    private void resetPositions() {
        float y = 8f;
        for (Module m : Modules.ALL) {
            if (!m.hud) continue;
            m.anchorX = 0f;
            m.anchorY = 0f;
            m.scale = 1f;
            if (m.enabled) {
                m.x = 8f;
                m.y = y;
                y += 22f;
            }
        }
        AstucyConfig.markDirty();
    }

    @Override
    protected void keyPressed(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            AstucyConfig.flushNow();
            client.setScreen(parent);
            return;
        }
        if (selected != null) {
            float step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 5f : 1f;
            if (keyCode == Keyboard.KEY_LEFT) { selected.x -= step; AstucyConfig.markDirty(); return; }
            if (keyCode == Keyboard.KEY_RIGHT) { selected.x += step; AstucyConfig.markDirty(); return; }
            if (keyCode == Keyboard.KEY_UP) { selected.y -= step; AstucyConfig.markDirty(); return; }
            if (keyCode == Keyboard.KEY_DOWN) { selected.y += step; AstucyConfig.markDirty(); return; }
        }
        super.keyPressed(typedChar, keyCode);
    }

    @Override
    public void removed() {
        AstucyConfig.flushNow();
        super.removed();
    }
}
