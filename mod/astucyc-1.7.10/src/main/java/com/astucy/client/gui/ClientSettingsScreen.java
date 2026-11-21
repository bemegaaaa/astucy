package com.astucy.client.gui;

import com.astucy.client.ui.Ui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class ClientSettingsScreen extends Screen {
    private final Screen parent;
    private int dragId = -1;
    private float cardX, cardY, cardW, cardH;

    public ClientSettingsScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        Ui.rect(0f, 0f, width, height, 0x9906080C);
        cardW = Math.min(380f, width - 32f);
        cardH = 264f;
        cardX = (width - cardW) * 0.5f;
        cardY = (height - cardH) * 0.5f;

        Ui.rrect(cardX, cardY, cardW, cardH, 10f, Ui.bgShell());
        Ui.rframe(cardX, cardY, cardW, cardH, 10f, 1f, Ui.borderSubtle());

        Ui.text("Client Video & Sensitivity Settings", cardX + 16f, cardY + 14f, Ui.fgPrimary());
        float backW = 54f, backX = cardX + cardW - backW - 14f, backY = cardY + 11f;
        boolean hovBack = Ui.hit(mouseX, mouseY, backX, backY, backW, 18f);
        Ui.rrect(backX, backY, backW, 18f, 5f, hovBack ? Ui.bgCardHover() : Ui.bgCard());
        Ui.ctext("Back", backX + backW * 0.5f, backY + 5f, Ui.fgPrimary(), false);

        GameOptions opt = client.options;
        float sy = cardY + 46f;
        float sx = cardX + 16f;
        float sw = cardW - 32f;

        drawSlider(0, sx, sy, sw, "FOV", String.valueOf(Math.round(opt.fov)), (opt.fov - 30f) / 80f);
        sy += 34f;
        drawSlider(1, sx, sy, sw, "Gamma (Brightness)", Math.round(opt.gamma * 100f) + "%", clamp01(opt.gamma / 5f));
        sy += 34f;
        String fpsTxt = opt.maxFramerate >= 260 ? "Unlimited" : (opt.maxFramerate + " FPS");
        drawSlider(2, sx, sy, sw, "Max Framerate", fpsTxt, clamp01((opt.maxFramerate - 20f) / 240f));
        sy += 34f;
        drawSlider(3, sx, sy, sw, "Render Distance", opt.viewDistance + " chunks", clamp01((opt.viewDistance - 2f) / 14f));
        sy += 34f;
        drawSlider(4, sx, sy, sw, "Mouse Sensitivity", Math.round(opt.sensitivity * 200f) + "%", clamp01(opt.sensitivity));
        sy += 36f;

        float btnW = (sw - 12f) / 3f;
        drawToggleBtn(sx, sy, btnW, 20f, "View Bobbing", opt.bobView, Ui.hit(mouseX, mouseY, sx, sy, btnW, 20f));
        drawToggleBtn(sx + btnW + 6f, sy, btnW, 20f, "Fancy Graphics", opt.fancyGraphics, Ui.hit(mouseX, mouseY, sx + btnW + 6f, sy, btnW, 20f));
        String partLabel = opt.particle == 0 ? "Particles: All" : (opt.particle == 1 ? "Particles: Dec" : "Particles: Min");
        drawToggleBtn(sx + (btnW + 6f) * 2f, sy, btnW, 20f, partLabel, opt.particle == 0, Ui.hit(mouseX, mouseY, sx + (btnW + 6f) * 2f, sy, btnW, 20f));

        if (dragId >= 0 && Mouse.isButtonDown(0)) {
            applySlider(dragId, clamp01((mouseX - sx) / sw));
        } else if (!Mouse.isButtonDown(0)) {
            dragId = -1;
        }
        super.render(mouseX, mouseY, tickDelta);
    }

    private void drawSlider(int id, float x, float y, float w, String label, String val, float norm) {
        Ui.text(label, x, y, Ui.fgPrimary());
        Ui.text(val, x + w - Ui.tw(val), y, Ui.accent());
        Ui.slider(x, y + 13f, w, norm, Ui.accent());
    }

    private void drawToggleBtn(float x, float y, float w, float h, String label, boolean on, boolean hov) {
        int bg = on ? Ui.withA(Ui.accent(), hov ? 0.35f : 0.22f) : (hov ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rrect(x, y, w, h, 5f, bg);
        Ui.rframe(x, y, w, h, 5f, 1f, on ? Ui.accent() : Ui.borderSubtle());
        Ui.ctextScaled(label, x + w * 0.5f, y + 6f, 0.85f, Ui.fgPrimary(), false);
    }

    private void applySlider(int id, float n) {
        GameOptions opt = client.options;
        if (id == 0) opt.fov = Math.round(30f + n * 80f);
        else if (id == 1) opt.gamma = n * 5f;
        else if (id == 2) opt.maxFramerate = Math.round(20f + n * 240f);
        else if (id == 3) opt.viewDistance = Math.max(2, Math.min(16, Math.round(2f + n * 14f)));
        else if (id == 4) opt.sensitivity = clamp01(n);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        float backW = 54f, backX = cardX + cardW - backW - 14f, backY = cardY + 11f;
        if (Ui.hit(mouseX, mouseY, backX, backY, backW, 18f)) {
            client.options.save();
            client.setScreen(parent);
            return;
        }
        float sx = cardX + 16f, sw = cardW - 32f, sy = cardY + 46f;
        for (int i = 0; i < 5; i++) {
            float rowY = sy + i * 34f;
            if (Ui.hit(mouseX, mouseY, sx - 4f, rowY + 8f, sw + 8f, 18f)) {
                dragId = i;
                applySlider(i, clamp01((mouseX - sx) / sw));
                return;
            }
        }
        sy += 5 * 34f + 2f;
        float btnW = (sw - 12f) / 3f;
        if (Ui.hit(mouseX, mouseY, sx, sy, btnW, 20f)) {
            client.options.bobView = !client.options.bobView;
        } else if (Ui.hit(mouseX, mouseY, sx + btnW + 6f, sy, btnW, 20f)) {
            client.options.fancyGraphics = !client.options.fancyGraphics;
        } else if (Ui.hit(mouseX, mouseY, sx + (btnW + 6f) * 2f, sy, btnW, 20f)) {
            client.options.particle = (client.options.particle + 1) % 3;
        }
    }

    @Override
    protected void keyPressed(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            client.options.save();
            client.setScreen(parent);
            return;
        }
        super.keyPressed(typedChar, keyCode);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
