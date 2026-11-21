package com.astucy.client.gui;

import com.astucy.client.account.Accounts;
import com.astucy.client.ui.Ui;

import java.util.List;

public final class AccountMenu {
    private boolean open = false;
    private float lastX, lastY, lastW, lastH;

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void render(float rightEdge, float topY, int mx, int my) {
        String name = Accounts.activeName();
        float chipW = Math.max(76f, Ui.tw(name) + 26f);
        float chipH = 18f;
        float x = rightEdge - chipW;
        float y = topY;
        lastX = x; lastY = y; lastW = chipW; lastH = chipH;

        boolean hov = Ui.hit(mx, my, x, y, chipW, chipH);
        Ui.rrect(x, y, chipW, chipH, 5f, hov ? Ui.bgCardHover() : Ui.bgCard());
        Ui.rframe(x, y, chipW, chipH, 5f, 1f, Ui.borderSubtle());
        Ui.disc(x + 9f, y + 9f, 4f, Ui.accent());
        Ui.text(name, x + 17f, y + 5f, Ui.fgPrimary());

        if (open) {
            List<Accounts.Entry> list = Accounts.list();
            float dw = Math.max(chipW, 128f);
            float dh = Math.max(22f, list.size() * 18f + 8f);
            float dx = rightEdge - dw;
            float dy = y + chipH + 4f;
            Ui.rrect(dx, dy, dw, dh, 6f, Ui.bgPanel());
            Ui.rframe(dx, dy, dw, dh, 6f, 1f, Ui.borderSubtle());
            for (int i = 0; i < list.size(); i++) {
                Accounts.Entry e = list.get(i);
                float ry = dy + 4f + i * 18f;
                Ui.disc(dx + 10f, ry + 8f, 3f, e.active ? Ui.accent() : Ui.fgDim());
                Ui.text(e.username, dx + 18f, ry + 4f, e.active ? Ui.fgPrimary() : Ui.fgMuted());
            }
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0) return false;
        if (Ui.hit(mx, my, lastX, lastY, lastW, lastH)) {
            open = !open;
            return true;
        }
        if (open) {
            open = false;
            return true;
        }
        return false;
    }
}
