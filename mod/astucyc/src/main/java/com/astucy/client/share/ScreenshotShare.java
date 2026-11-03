package com.astucy.client.share;

import com.astucy.client.AstucyFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import javax.imageio.ImageIO;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Screenshot compartilhável DENTRO do chat (estilo client comercial):
 * cada print salva insere uma mensagem no chat com dois botões clicáveis
 * — "[Copy image]" e "[Link]" — que executam na linha do chat.
 *
 * O marcador das linhas de ação é um U+200B invisível, e o clique mapeia
 * pela MESMA API vanilla (captureClickableText) usada pelo Ctrl+botão
 * direito — sem barra flutuante, sem estilos de clickEvent.
 */
public final class ScreenshotShare {

    private ScreenshotShare() {
    }

    /** prefixo invisível das linhas de ação (U+200B) */
    static final String MARK = "\u200B";
    static final String BTN_COPY = "[Copy image]";
    static final String BTN_LINK = "[Link]";

    private static long grabAt = -1L;
    private static File lastShot;
    private static boolean busy;
    private static int copyW, linkW, gapW; // px no espaço do chat

    // ----------------------------------------------------------------
    // ciclo de vida
    // ----------------------------------------------------------------

    /** Chamado no TAIL do Screenshot.grab vanilla (F2). */
    public static void onGrab() {
        grabAt = System.currentTimeMillis();
        busy = false;
    }

    /** Tick do cliente: acha a print mais nova e entra com as ações no chat. */
    public static void tick(Minecraft mc) {
        if (grabAt >= 0L && System.currentTimeMillis() - grabAt < 4000L) {
            File best = newestAfter(new File(mc.gameDirectory, "screenshots"), grabAt - 1500L);
            if (best != null && best != lastShot) {
                lastShot = best;
                offerInChat(mc, best);
            }
        }
    }

    private static File newestAfter(File dir, long after) {
        File[] list = dir.listFiles();
        if (list == null) {
            return null;
        }
        File best = null;
        long bestT = -1L;
        for (File f : list) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".png")) {
                continue;
            }
            long t = f.lastModified();
            if (t >= after && t > bestT) {
                bestT = t;
                best = f;
            }
        }
        return bestT >= after ? best : null;
    }

    public static boolean isBusy() {
        return busy;
    }

    // ----------------------------------------------------------------
    // mensagem clicável no chat
    // ----------------------------------------------------------------

    private static void offerInChat(Minecraft mc, File f) {
        try {
            ChatComponent cc = mc.gui.hud.getChat();
            if (cc == null) {
                return;
            }
            Font font = AstucyFonts.menu();
            copyW = font.width(BTN_COPY);
            linkW = font.width(BTN_LINK);
            gapW = font.width("  ");
            // linha 1: título com o nome do arquivo
            cc.addClientSystemMessage(Component.literal(
                    "\u00A7fAstucy\u00A78: \u00A77Screenshot \u2014 \u00A7f" + f.getName()));
            // linha 2: ações clicáveis (marcador invisível na frente)
            cc.addClientSystemMessage(Component.literal(
                    MARK + "\u00A7b" + BTN_COPY + "  " + BTN_LINK));
        } catch (Throwable ignored) {
        }
    }

    /** Clique numa linha de ação? true se consumiu (e executou o botão). */
    public static boolean clickAction(Minecraft mc, double mx, double my) {
        String text = messageUnderCursor(mc, mx, my);
        if (text == null || !text.startsWith(MARK)) {
            return false;
        }
        if (lastShot == null || busy) {
            return false;
        }
        float local = hitLocalX;
        if (Float.isNaN(local)) {
            return false;
        }
        if (local >= 0 && local <= copyW) {
            copyImage(mc);
            return true;
        }
        if (local >= copyW + gapW && local <= copyW + gapW + linkW) {
            upload(mc);
            return true;
        }
        // clicou a linha mas fora dos botões: não consome
        return false;
    }

    // ----------------------------------------------------------------
    // Ctrl+botão direito: mensagem completa do chat sob o cursor
    // ----------------------------------------------------------------

    /** Copia a mensagem inteira clicada com CTRL+RMB; true se copiou. */
    public static boolean copyMessageUnderCursor(Minecraft mc, double mx, double my, int mods) {
        try {
            String text = messageUnderCursor(mc, mx, my);
            if (text == null || text.startsWith(MARK)) {
                return false; // não copia linhas de ação
            }
            if (text.isBlank()) {
                return false;
            }
            Clipboard clip = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clip.setContents(new StringSelection(text), null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // ações
    // ----------------------------------------------------------------

    /** Copia o PNG pro clipboard do SO como IMAGEM (fallback: caminho). */
    public static void copyImage(Minecraft mc) {
        File f = lastShot;
        if (f == null || busy) {
            return;
        }
        busy = true;
        Thread t = new Thread(() -> {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) {
                    throw new IllegalStateException("not an image");
                }
                Clipboard clip = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                clip.setContents(new ImageTransferable(img), null);
                say(mc, "Screenshot copied as image — press Ctrl+V to paste");
            } catch (Throwable ex) {
                try {
                    Clipboard clip = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                    clip.setContents(new StringSelection(f.getAbsolutePath()), null);
                    say(mc, "Copied file path — " + f.getName());
                } catch (Throwable ignored) {
                    say(mc, "Could not copy the screenshot");
                }
            } finally {
                busy = false;
            }
        }, "astucy-shot-copy");
        t.setDaemon(true);
        t.start();
    }

    /** Upload em host público (catbox.moe); o link vai pro clipboard + chat. */
    public static void upload(Minecraft mc) {
        File f = lastShot;
        if (f == null || busy) {
            return;
        }
        busy = true;
        say(mc, "Uploading link...");
        Thread t = new Thread(() -> {
            try {
                String url = callCatbox(f);
                try {
                    Clipboard clip = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                    clip.setContents(new StringSelection(url), null);
                } catch (Throwable ignored) {
                }
                say(mc, "Link copied — " + url);
            } catch (Throwable ex) {
                String msg = String.valueOf(ex.getMessage());
                say(mc, "Upload failed — " + (msg.isBlank() ? ex.getClass().getSimpleName() : msg));
            } finally {
                busy = false;
            }
        }, "astucy-shot-upload");
        t.setDaemon(true);
        t.start();
    }

    private static String callCatbox(File f) throws Exception {
        String boundary = "----AstucyBoundary" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"userhash\"\r\n\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                + f.getName() + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(Files.readAllBytes(f.toPath()));
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://catbox.moe/user/api.php"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "Astucy/0.1.0 (Minecraft client)")
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        String text = resp.body() == null ? "" : resp.body().trim();
        boolean ok = resp.statusCode() == 200 && text.startsWith("https://");
        if (!ok) {
            throw new IllegalStateException(text.isEmpty() ? ("HTTP " + resp.statusCode()) : text);
        }
        return text;
    }

    private static void say(Minecraft mc, String s) {
        mc.execute(() -> {
            try {
                mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("\u00A7fAstucy\u00A78: \u00A77" + s));
            } catch (Throwable ignored) {
            }
        });
    }

    // ----------------------------------------------------------------
    // hit-test no espaço real do chat
    // ----------------------------------------------------------------

    private static float hitLocalX = Float.NaN;

    /** texto completo da mensagem sob o cursor (e axes para ações). */
    private static String messageUnderCursor(Minecraft mc, double mx, double my) {
        hitLocalX = Float.NaN;
        try {
            if (!(mc.gui.screen() instanceof ChatScreen) || mc.font == null) {
                return null;
            }
            ChatComponent cc = mc.gui.hud.getChat();
            if (cc == null) {
                return null;
            }
            ChatComponent.DisplayMode mode = null;
            try {
                ChatScreen sn = (ChatScreen) mc.gui.screen();
                java.lang.reflect.Field fd = ChatScreen.class.getDeclaredField("displayMode");
                fd.setAccessible(true);
                mode = (ChatComponent.DisplayMode) fd.get(sn);
            } catch (Throwable ignored) {
            }
            if (mode == null) {
                mode = ChatComponent.DisplayMode.FOREGROUND;
            }
            RowPicking picking = new RowPicking(mc.font, mx, my);
            cc.captureClickableText(picking, mc.getWindow().getGuiScaledHeight(),
                    mc.gui.hud.getGuiTicks(), mode);
            String cell = picking.hit;
            if (cell == null) {
                return null;
            }
            GuiMessage.Line line = matchLine(cc, cell);
            if (line == null) {
                return null;
            }
            hitLocalX = picking.localX;
            return line.parent().content().getString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** acha em trimmedMessages a linha cujo texto é igual a célula clicada. */
    private static GuiMessage.Line matchLine(ChatComponent cc, String text) {
        try {
            java.lang.reflect.Field fd = null;
            for (java.lang.reflect.Field f : cc.getClass().getDeclaredFields()) {
                if (f.getType() == List.class && "trimmedMessages".equals(f.getName())) {
                    fd = f;
                    break;
                }
            }
            if (fd == null) {
                return null;
            }
            fd.setAccessible(true);
            List<?> lines = (List<?>) fd.get(cc);
            if (lines == null) {
                return null;
            }
            for (Object o : lines) {
                if (o instanceof GuiMessage.Line line && text.contentEquals(astucy$seqText(line.content()))) {
                    return line;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** FormattedCharSequence -> String (igual ao render: code points em ordem). */
    private static String astucy$seqText(FormattedCharSequence seq) {
        if (seq == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        seq.accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    /** coleta as células do chat na ordem vanilla (runs e linhas).
     *  IMPORTANTE: as posições que o chat entrega estão no ESPAÇO do chat
     *  (pré-escala) — screen = pose * chatSpace. O mouse chega em GUI;
     *  convertemos pelo INVERSO da pose (escala m00/m11 + translação). */
    private static final class RowPicking implements ActiveTextCollector {
        private final Font font;
        private final float mx, my;
        String hit;
        float localX = Float.NaN;

        RowPicking(Font font, double mx, double my) {
            this.font = font;
            this.mx = (float) mx;
            this.my = (float) my;
        }

        private ActiveTextCollector.Parameters cur =
                new ActiveTextCollector.Parameters(new Matrix3x2f());

        @Override
        public ActiveTextCollector.Parameters defaultParameters() {
            return cur;
        }

        @Override
        public void defaultParameters(ActiveTextCollector.Parameters p) {
            cur = p;
        }

        @Override
        public void accept(TextAlignment align, int x, int y,
                           ActiveTextCollector.Parameters p, FormattedCharSequence seq) {
            if (seq == null || hit != null) {
                return;
            }
            float sx = 1f, sy = 1f, gx = 0f, gy = 0f;
            try {
                if (p != null && p.pose() != null) {
                    var m = new Matrix3x2f(p.pose());
                    sx = m.m00();
                    sy = m.m11();
                    gx = m.m20();
                    gy = m.m21();
                }
            } catch (Throwable ignored) {
            }
            // mouse -> chat space (inverso da pose do chat)
            float cx = Math.abs(sx) < 1e-6f ? mx : (mx - gx) / sx;
            float cy = Math.abs(sy) < 1e-6f ? my : (my - gy) / sy;
            int left = align == null ? x : align.calculateLeft(x, font, seq);
            int w = font.width(seq);
            if (cx >= left && cx <= left + w && cy >= y && cy <= y + font.lineHeight) {
                StringBuilder sb = new StringBuilder();
                seq.accept((i, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                hit = sb.toString();
                localX = cx - (float) left;
            }
        }

        @Override
        public void acceptScrolling(Component c, int i1, int i2, int i3, int i4, int i5,
                                    ActiveTextCollector.Parameters p) {
            // chat não rola horizontalmente
        }
    }

    /** Transferable de imagem pro clipboard do sistema (Ctrl+V cola a png). */
    private static final class ImageTransferable implements Transferable {
        private final java.awt.Image img;

        ImageTransferable(java.awt.Image img) {
            this.img = img;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor == DataFlavor.imageFlavor;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return img;
        }
    }
}
