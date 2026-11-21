package com.astucy.client.module;

import com.astucy.client.ui.Ui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry dos mÃƒÂ³dulos + motor de HUD do Astucy.
 * Cada mÃƒÂ³dulo renderiza com a customizaÃƒÂ§ÃƒÂ£o dele: escala, cores,
 * fundo (com opacidade), sombra e prefixo. Ãƒâ€šncora normalizada 0..1.
 */
public final class Modules {

    private static final List<Module> REGISTRY = new ArrayList<>();
    private static final Deque<Long> CLICKS_L = new ArrayDeque<>();
    private static final Deque<Long> CLICKS_R = new ArrayDeque<>();
    private static boolean attackWasDown = false;
    private static boolean useWasDown = false;
    private static long sessionStart = 0L;
    // combo (acertos seguidos no alvo)
    private static int combo = 0;
    private static long comboAt = 0L;

    /** Tecla do zoom (registrada pelo entrypoint). */
    public static KeyMapping zoomKey;
    /** fator do FOV da cÃƒÂ¢mera (1.0 = sem zoom; menor = mais aprox.) */
    private static volatile float zoomFovFactor = 1f;
    private static double zoomSens = -1d;
    private static long zoomLastNanos = -1L;
    private static boolean nvApplied = false;
    private static boolean sneakLatched = false;
    private static boolean shiftWasDown = false;
    private static boolean zoomLatched = false;
    private static float lastCamYaw = Float.NaN;
    private static float lastCamPitch = Float.NaN;
    private static long lastSpeedNs = -1L;
    private static float highSpeedFactor = 0f;
    private static float moveRushFactor = 0f;

    private Modules() {}

    public static void registerAll() {
        if (!REGISTRY.isEmpty()) {
            return;
        }
        sessionStart = System.currentTimeMillis();

        // InformaÃƒÂ§ÃƒÂ£o (HUD)
        add(new Module("fps", "FPS", "Frames per second", ModuleCategory.INFORMACAO, true).at(0.012, 0.02));
        add(new Module("ping", "Ping", "Server latency", ModuleCategory.INFORMACAO, true).at(0.012, 0.06));
        add(new Module("server", "Server", "Server IP address", ModuleCategory.INFORMACAO, true).at(0.012, 0.098));
        add(new Module("memory", "Memory", "Game RAM usage", ModuleCategory.INFORMACAO, true).at(0.012, 0.136));
        add(new Module("speed", "Speed Display", "Blocks per second", ModuleCategory.INFORMACAO, true).at(0.012, 0.174));
        add(new Module("direcao", "Direction", "Facing (N/E/S/W)", ModuleCategory.INFORMACAO, true).at(0.5, 0.02));
        add(new Module("sessao", "Session", "Session playtime", ModuleCategory.INFORMACAO, true).at(0.012, 0.25));
        add(new Module("hora", "Time", "Server world time", ModuleCategory.INFORMACAO, true).at(0.845, 0.135));
        add(new Module("bioma", "Biome", "Current biome", ModuleCategory.INFORMACAO, true).at(0.845, 0.173));
        add(new Module("coords", "Coords", "X, Y, Z + facing", ModuleCategory.INFORMACAO, true).at(0.845, 0.02));
        add(new Module("efeitos", "Effects", "Active potions", ModuleCategory.INFORMACAO, true).at(0.845, 0.211));
        // Combate (HUD)
        add(new Module("cps", "CPS Display", "Clicks per second", ModuleCategory.COMBATE, true).at(0.012, 0.88));
        add(new Module("keystrokes", "Keystrokes", "WASD + mouse keys", ModuleCategory.COMBATE, true).at(0.012, 0.62));
        add(new Module("reach", "Reach Display", "Target distance", ModuleCategory.COMBATE, true).at(0.845, 0.88));
        add(new Module("totem", "Totems", "Totem counter", ModuleCategory.COMBATE, true).at(0.845, 0.94));
        add(new Module("armor", "Armor", "Armor durability", ModuleCategory.COMBATE, true).at(0.845, 0.835));
        // VisÃƒÂ£o (toggle)
        add(new Module("fullbright", "Fullbright", "Max brightness", ModuleCategory.VISAO, false));
        add(new Module("zoom", "Zoom", "Smooth hold zoom", ModuleCategory.VISAO, false).isNew());
        add(new Module("hurtcam", "No Hurt Cam", "No damage shake", ModuleCategory.VISAO, false).isNew());
        // Utilidade (toggle + HUD)
        add(new Module("sprint", "Auto Sprint", "Always sprint", ModuleCategory.UTILIDADE, true).at(0.012, 0.30));
        add(new Module("sneak", "Toggle Sneak", "Tap sneak toggle", ModuleCategory.UTILIDADE, false).isNew());
        // Chat: historico infinito + pesquisa
        add(new Module("chat", "Chat", "History & /chs", ModuleCategory.UTILIDADE, true));
        // InformaÃƒÂ§ÃƒÂ£o extra (HUD)
        add(new Module("itens", "Held Item", "Held item count", ModuleCategory.INFORMACAO, true).at(0.012, 0.338).isNew());
        add(new Module("health", "Health", "HP + absorption", ModuleCategory.COMBATE, true).at(0.012, 0.414).isNew());
        add(new Module("hunger", "Hunger", "Food + saturation", ModuleCategory.COMBATE, true).at(0.012, 0.452).isNew());
        add(new Module("combo", "Combo Counter", "Consecutive hits", ModuleCategory.COMBATE, true).at(0.012, 0.49).isNew());
        add(new Module("healthtag", "Player Health", "Overhead HP tag", ModuleCategory.COMBATE, false).isNew());
        add(new Module("pack", "Pack", "Active pack name", ModuleCategory.INFORMACAO, true).at(0.012, 0.376).isNew());
        // PvP / info extra
        add(new Module("target", "Target HUD", "Aimed target HUD", ModuleCategory.COMBATE, true)
                .at(0.845, 0.28).isNew());
        add(new Module("armordur", "Armor Durability", "Piece durability", ModuleCategory.COMBATE, true)
                .at(0.845, 0.34).isNew());
        add(new Module("daycounter", "Day Counter", "World day number", ModuleCategory.INFORMACAO, true)
                .at(0.012, 0.528).isNew());
        add(new Module("itemcount", "Item Counter", "Stack & durability", ModuleCategory.INFORMACAO, true)
                .at(0.012, 0.566).isNew());
        // Visuals (comportamento & efeitos)
        add(new Module("viewmodel", "Viewmodel", "Hand pose & scale", ModuleCategory.VISAO, false).isNew());
        add(new Module("blur", "Motion Blur", "High-speed blur", ModuleCategory.VISAO, false).isNew());
        add(new Module("speedfx", "High-Speed FX", "Velocity & FOV FX", ModuleCategory.VISAO, false).isNew());
        add(new Module("ambience", "Ambience Tint", "Screen color tint", ModuleCategory.VISAO, false).isNew());
        add(new Module("menublur", "Menu Glass FX", "Glass & spotlight", ModuleCategory.VISAO, false).isNew());
        add(new Module("crosshair", "Crosshair", "Custom crosshair", ModuleCategory.VISAO, false).isNew());
        add(new Module("cit", "CIT Resewn (CIT)", "OptiFine custom item textures", ModuleCategory.VISAO, false).isNew());
        // Extras de HUD (desenho prÃƒÂ³prio, sem mixin)
        add(new Module("watermark", "Watermark", "Client watermark", ModuleCategory.INFORMACAO, true)
                .at(0.5, 0.955).isNew());
        add(new Module("fpsgraph", "FPS Graph", "FPS sparkline", ModuleCategory.INFORMACAO, true)
                .at(0.845, 0.40).isNew());
        add(new Module("realtime", "Real Time", "Local time clock", ModuleCategory.INFORMACAO, true)
                .at(0.845, 0.56).isNew());

        // preset de fabrica (1:1 com Rust: sprint, fps, ping, cps, cit)
        for (String id : new String[]{"sprint", "fps", "ping", "cps", "cit"}) {
            Module m = find(id);
            if (m != null) {
                m.enabled = true;
            }
        }
    }

    private static void add(Module m) {
        REGISTRY.add(m);
    }

    public static List<Module> all() {
        return REGISTRY;
    }

    public static Module find(String id) {
        for (Module m : REGISTRY) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------
     * tick (comportamento)
     * ------------------------------------------------------------------ */

    /**
     * Contagem de cliques por FRAME (~100 Hz) Ã¢â‚¬â€ o tick (20 Hz) funde cliques
     * rÃƒÂ¡pidos e o CPS subestimava (10+ viravam 5-6). Conta esquerdo e direito.
     */
    public static void frame(Minecraft mc) {
        if (mc.player == null) {
            attackWasDown = false;
            useWasDown = false;
            CLICKS_L.clear();
            CLICKS_R.clear();
            resetZoom(mc);
            return;
        }
        long now = System.currentTimeMillis();
        boolean attack = mc.options.keyAttack.isDown();
        if (attack && !attackWasDown) {
            CLICKS_L.addLast(now);
            // combo: acerto em entidade viva (usa o alvo da mira, sem mixin)
            if (mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player) {
                combo = (now - comboAt <= 3000L) ? combo + 1 : 1;
                comboAt = now;
            }
        }
        attackWasDown = attack;
        boolean use = mc.options.keyUse.isDown();
        if (use && !useWasDown) {
            CLICKS_R.addLast(now);
        }
        useWasDown = use;
        pruneClicks(CLICKS_L, now);
        pruneClicks(CLICKS_R, now);
        updateHighSpeed(mc);
        com.astucy.client.compat.BlurBridge.sync(find("blur"), highSpeedFactor);
        zoomTick(mc);  // a ~fps (suave); o tick abaixo ÃƒÂ© a garantia eterna

        // histÃƒÂ³rico do grÃƒÂ¡fico de FPS (96 amostras)
        FPS_HIST.addLast(mc.getFps());
        while (FPS_HIST.size() > GRAPH_SAMPLES) {
            FPS_HIST.removeFirst();
        }
    }

    /** Calcula velocidade angular da cÃƒÂ¢mera + velocidade fÃƒÂ­sica para o blur/FX de alta velocidade. */
    private static void updateHighSpeed(Minecraft mc) {
        if (mc.player == null) {
            highSpeedFactor = 0f;
            moveRushFactor = 0f;
            lastCamYaw = Float.NaN;
            lastSpeedNs = -1L;
            return;
        }
        long nowNs = System.nanoTime();
        float dt = lastSpeedNs < 0L ? 0.016f : Math.max(0.001f, Math.min(0.1f, (nowNs - lastSpeedNs) / 1e9f));
        lastSpeedNs = nowNs;

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float angSpeed = 0f;
        if (!Float.isNaN(lastCamYaw)) {
            float dy = yaw - lastCamYaw;
            while (dy > 180f) dy -= 360f;
            while (dy < -180f) dy += 360f;
            float dp = pitch - lastCamPitch;
            angSpeed = (float) Math.hypot(dy, dp) / dt;
        }
        lastCamYaw = yaw;
        lastCamPitch = pitch;

        var vel = mc.player.getDeltaMovement();
        float bps = (float) (Math.hypot(vel.x, vel.z) * 20.0);
        float vertBps = (float) (Math.abs(vel.y) * 20.0);
        float moveTarget = Math.min(1f, Math.max(0f, (bps - 4.2f) / 7.5f) + (mc.player.isSprinting() ? 0.25f : 0f));
        float camTarget = Math.min(1f, Math.max(0f, (angSpeed - 120f) / 680f));
        float fallTarget = Math.min(1f, Math.max(0f, (vertBps - 10f) / 28f));

        float combined = Math.min(1f, Math.max(moveTarget * 0.85f, Math.max(camTarget, fallTarget)));
        float k = 1f - (float) Math.exp(-dt * 14.0);
        highSpeedFactor += (combined - highSpeedFactor) * k;
        moveRushFactor += (moveTarget - moveRushFactor) * (1f - (float) Math.exp(-dt * 10.0));
    }

    public static float highSpeedFactor() {
        return highSpeedFactor;
    }

    public static float moveRushFactor() {
        Module sfx = find("speedfx");
        if (sfx == null || !sfx.enabled || !sfx.boolProp("fov_rush", true)) {
            return 0f;
        }
        float intensity = (float) sfx.numProp("intensity", 1.0);
        return moveRushFactor * 0.045f * intensity;
    }

    /** Valor do combo atual (zera depois de 3s sem acertar). */
    private static String comboValue() {
        return (System.currentTimeMillis() - comboAt > 3000L) ? "0x" : combo + "x";
    }

    private static String healthValue(Minecraft mc) {
        float hp = mc.player.getHealth();
        float ab = mc.player.getAbsorptionAmount();
        String s = String.format(java.util.Locale.ROOT, "%.1f", hp);
        return ab > 0.05f ? s + " +" + Math.round(ab) : s;
    }

    private static String hungerValue(Minecraft mc) {
        var fd = mc.player.getFoodData();
        return fd.getFoodLevel() + "  " + String.format(java.util.Locale.ROOT, "%.1f", fd.getSaturationLevel());
    }

    /** Estado travado do sneak (usado pelo mixin do KeyboardInput). */
    public static boolean isSneakLatched() {
        return sneakLatched;
    }

    /**
     * Zoom por MODERADORA do FOV da cÃƒÂ¢mera (CameraFovMixin multiplica o FOV
     * devolvido pelo vanilla no render). Vantagens da abordagem:
     *  - NÃƒÆ’O toca em options.fov Ã¢â€ â€™ sem limite de 30Ã¢â‚¬â€œ110 do vanilla: o zoom
     *    alcanÃƒÂ§a nÃƒÂ­veis profundos (10x) sem clamp/Ã¢â‚¬Å“bugÃ¢â‚¬Â no final da barra;
     *  Ã‚Â· options.txt nunca grava o FOV do zoom (nada de FOV Ã¢â‚¬Å“coladoÃ¢â‚¬Â);
     *  - a sensibilidade da mira acompanha o fator multiplicado.
     */
    private static void zoomTick(Minecraft mc) {
        Module zm = find("zoom");
        boolean toggle = zm != null && zm.boolProp("toggle", false);
        boolean want;
        if (zm == null || !zm.enabled || mc.screen != null) {
            want = false;
            zoomLatched = false;
        } else if (toggle) {
            want = zoomLatched;
        } else {
            want = zoomKey != null && zoomKey.isDown();
        }

        // "Zoom level" = ampliaÃƒÂ§ÃƒÂ£o: 0% = sem zoom; 100% Ã¢â€°Ë† 10x.
        // Fator do FOV = 1 / (1 + 8 * level) Ã¢â€ â€™ 35% Ã¢â€°Ë† 2.5x, 65% Ã¢â€°Ë† 5.3x, 100% Ã¢â€°Ë† 9x.
        double level = zm == null ? 0.4d : Math.max(0d, Math.min(1d, zm.numProp("level", 0.4d)));
        float target = want ? (float) (1d / (1d + 8d * level)) : 1f;
        target = Math.max(0.1f, target); // teto de sanidade: 10x

        // suavidade idÃƒÂªntica em QUALQUER fps: easing exponencial por tempo (dt)
        long nowNs = System.nanoTime();
        float dt = zoomLastNanos < 0L ? 0f : Math.max(0f, Math.min(0.1f, (nowNs - zoomLastNanos) / 1e9f));
        zoomLastNanos = nowNs;
        boolean smooth = zm != null && zm.boolProp("smooth", true);
        float cur = zoomFovFactor;
        if (smooth) {
            float k = dt <= 0f ? 0f : 1f - (float) Math.exp(-dt * 14.0);
            cur += (target - cur) * k;
            if (Math.abs(target - cur) < 0.003f) {
                cur = target;
            }
        } else {
            cur = target;
        }
        zoomFovFactor = cur;

        // mira acompanhÃƒÂ¡vel no zoom: sensibilidade proporcional ao FOV
        boolean zoomed = want && cur < 0.98f;
        if (zoomed) {
            if (zoomSens < 0d) {
                zoomSens = mc.options.sensitivity().get();
            }
            float ratio = Math.max(0.15f, cur);
            mc.options.sensitivity().set(Math.round(zoomSens * ratio * 10000d) / 10000d);
        } else if (zoomSens >= 0d) {
            mc.options.sensitivity().set(zoomSens);
            zoomSens = -1d;
        }

        // terminou de soltar (fator voltou pro 1 Ã¢â‚¬â€ nada mais a restaurar)
        if (!want && !zoomed && zoomSens < 0d && zoomFovFactor >= 0.999f) {
            zoomFovFactor = 1f;
            zoomLastNanos = -1L;
        }
    }

    /** Fator atual do FOV (1.0 = nada; o mixin da cÃƒÂ¢mera usa). */
    public static float zoomFovFactor() {
        return zoomFovFactor;
    }

    private static void resetZoom(Minecraft mc) {
        if (zoomSens >= 0d) {
            mc.options.sensitivity().set(zoomSens);
            zoomSens = -1d;
        }
        zoomFovFactor = 1f;
        zoomLastNanos = -1L;
    }

    /**
     * Chamado no encerramento do cliente: devolve a sensibilidade se o
     * cliente fechar com o zoom ativo (o FOV verdadeiro nunca foi tocado).
     */
    public static void restoreViewportOnExit(Minecraft mc) {
        resetZoom(mc);
    }

    public static void tick(Minecraft mc) {
        // blur: empurra o estado do mÃƒÂ³dulo para o mod (funciona mesmo no menu)
        com.astucy.client.compat.BlurBridge.sync(find("blur"));

        // zoom e sneak no TICK: rodam mesmo com o HUD parado (F1/ocultar GUI,
        // telas do jogo) Ã¢â‚¬â€ o zoom nunca mais "trava" com a cÃƒÂ¢mera aproximada
        zoomTick(mc);
        Module sn = find("sneak");
        boolean shiftDown = mc.options.keyShift.isDown();
        if (sn != null && sn.enabled && mc.screen == null && shiftDown && !shiftWasDown) {
            sneakLatched = !sneakLatched;
        }
        if (sn == null || !sn.enabled) {
            sneakLatched = false;
        }
        shiftWasDown = shiftDown;

        if (mc.player == null) {
            resetZoom(mc);
            return;
        }
        long now = System.currentTimeMillis();
        pruneClicks(CLICKS_L, now);
        pruneClicks(CLICKS_R, now);

        // zoom alternado: um clique na tecla liga/desliga o zoom; no modo
        // segurar os cliques sÃƒÂ£o drenados (nÃƒÂ£o atravessam pra o modo toggle)
        Module zm = find("zoom");
        if (zm == null || !zm.enabled) {
            zoomLatched = false;
        }
        if (zoomKey != null) {
            if (zm != null && zm.enabled && zm.boolProp("toggle", false)) {
                while (zoomKey.consumeClick()) {
                    zoomLatched = !zoomLatched;
                }
            } else {
                while (zoomKey.consumeClick()) {
                    // drena (hold)
                }
            }
        }

        // sem cÃƒÂ¢mera de dano: zera o tremor/flash (campos pÃƒÂºblicos, sem mixin)
        Module hc = find("hurtcam");
        if (hc != null && hc.enabled) {
            mc.player.hurtTime = 0;
            mc.player.hurtDuration = 0;
        }

        // night-vision invisÃƒÂ­vel: funciona sem mixin (o gamma do vanilla ÃƒÂ©
        // limitado a 1.0 e nÃƒÂ£o permite brilho real)
        Module fb = find("fullbright");
        if (fb != null && fb.enabled && mc.player != null) {
            MobEffectInstance nv = mc.player.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 400) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, true, false, false));
                nvApplied = true;
            }
        } else if (nvApplied && mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            nvApplied = false;
        }
        Module sp = find("sprint");
        if (sp != null && sp.enabled && mc.player.input != null) {
            mc.player.setSprinting(mc.player.input.getMoveVector().lengthSquared() > 0.001f);
        }
    }

    /* ------------------------------------------------------------------
     * HUD
     * ------------------------------------------------------------------ */

    public static void renderHud(GuiGraphics g) {
        preview = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        frame(mc);
        // HUD usa Montserrat anti-aliased por padrÃƒÂ£o (zero pixelado / serrilhado)
        Font font = com.astucy.client.AstucyFonts.ui();
        int gw = g.guiWidth();
        int gh = g.guiHeight();

        // Ambience Tint (color grading suave na tela inteira)
        Module amb = find("ambience");
        if (amb != null && amb.enabled && mc.screen == null) {
            float str = (float) amb.numProp("strength", 0.14);
            int alpha = Math.max(0, Math.min(90, Math.round(str * 255f)));
            int rgb = amb.boolProp("emerald", true) ? 0x16C975 : (amb.fgColor & 0xFFFFFF);
            if (alpha > 0) {
                Ui.rect(g, 0, 0, gw, gh, (alpha << 24) | rgb);
            }
        }

        // High-Speed FX (vinheta perifÃƒÂ©rica dinÃƒÂ¢mica em alta velocidade)
        Module sfx = find("speedfx");
        if (sfx != null && sfx.enabled && mc.screen == null && highSpeedFactor > 0.04f) {
            float intensity = (float) sfx.numProp("intensity", 1.0);
            int a = Math.min(72, Math.round(highSpeedFactor * intensity * 38f));
            if (a > 2) {
                int col = (a << 24) | 0x38C975;
                int r = Math.max(gw, gh) / 3;
                com.astucy.client.ui.Skin.glow(g, 0, 0, r, col);
                com.astucy.client.ui.Skin.glow(g, gw, 0, r, col);
                com.astucy.client.ui.Skin.glow(g, 0, gh, r, col);
                com.astucy.client.ui.Skin.glow(g, gw, gh, r, col);
            }
        }

        for (Module m : REGISTRY) {
            if (!m.hud) {
                continue;
            }
            // sÃƒÂ³ o que estÃƒÂ¡ ligado aparece no HUD
            if (!m.enabled) {
                continue;
            }
            int x = (int) Math.round(m.anchorX * gw);
            int y = (int) Math.round(m.anchorY * gh);
            drawHud(g, font, m, x, y);
        }
        Module ch = find("crosshair");
        if (ch != null && ch.enabled && mc.options.getCameraType().isFirstPerson() && mc.screen == null) {
            drawCrosshair(g, mc, ch, gw / 2, gh / 2);
        }
        if (mc.screen == null) {
            renderJoinIntro(g, font, gw, gh);
        }
    }

    private static long joinIntroStamp = 0L;

    public static void triggerJoinIntro() {
        joinIntroStamp = System.currentTimeMillis();
    }

    /**
     * AnimaÃƒÂ§ÃƒÂ£o cinematogrÃƒÂ¡fica ao entrar no mundo: a logo oficial Astucy nasce
     * girando no centro-alto da tela, voa com rastro esmeralda atÃƒÂ© o banner de
     * boas-vindas no topo e recolhe suavemente apÃƒÂ³s 3.6 segundos.
     */
    private static void renderJoinIntro(GuiGraphics g, Font f, int gw, int gh) {
        if (joinIntroStamp <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - joinIntroStamp;
        if (elapsed < 0L || elapsed > 3600L) {
            return;
        }
        float inRaw = Math.min(1f, elapsed / 620f);
        float outRaw = elapsed > 3050L ? Math.min(1f, (elapsed - 3050L) / 550f) : 0f;
        float outEase = outRaw * outRaw * (3f - 2f * outRaw);
        float vis = 1f - outEase;
        if (vis <= 0.01f) {
            return;
        }

        String brandText = "ASTUCY";
        String subText = "Press R-SHIFT for menu";
        Font bf = com.astucy.client.AstucyFonts.brand();
        int bw = 32 + bf.width(brandText) + 14 + f.width(subText) + 14;
        int bh = 24;
        int bx = (gw - bw) / 2;
        int by = Math.round(14f - outEase * 28f);

        float pillReveal = Math.min(1f, Math.max(0f, (inRaw - 0.28f) / 0.72f));
        float pillEase = 1f - (1f - pillReveal) * (1f - pillReveal) * (1f - pillReveal);
        float alpha = pillEase * vis;

        if (alpha > 0.02f) {
            int curW = Math.max(36, Math.round(bw * (0.35f + 0.65f * pillEase)));
            int curX = (gw - curW) / 2;
            Ui.shadow(g, curX, by, curW, bh);
            int topBg = (((int) (0xE8 * alpha)) << 24) | 0x090D14;
            int botBg = (((int) (0xEE * alpha)) << 24) | 0x06080D;
            if (!com.astucy.client.ui.Skin.boxGrad(g, curX, by, curW, bh, topBg, botBg, 8)) {
                Ui.rrect(g, curX, by, curW, bh, topBg, 8);
            }
            Ui.rframe(g, curX, by, curW, bh, (((int) (0x55 * alpha)) << 24) | 0x22C55E, 8);

            if (pillEase > 0.55f) {
                float txtA = Math.min(1f, (pillEase - 0.55f) / 0.45f) * vis;
                int tc = (((int) (0xFF * txtA)) << 24) | 0xF8FAFC;
                int sc = (((int) (0xFF * txtA)) << 24) | 0x94A3B8;
                int dc = (((int) (0x88 * txtA)) << 24) | 0x22C55E;
                int tx = bx + 28;
                Ui.spaced(g, bf, brandText, tx, by + (bh - bf.lineHeight) / 2 + 1, tc, 1);
                int sepX = tx + bf.width(brandText) + 9;
                Ui.rect(g, sepX, by + 6, 1, bh - 12, dc);
                g.drawString(f, subText, sepX + 7, by + (bh - f.lineHeight) / 2 + 1, sc, false);
            }

            // feixe de luz esmeralda que varre o banner ao encaixar
            float sweep = (elapsed - 480f) / 900f;
            if (sweep > 0f && sweep < 1f) {
                int sx = curX + Math.round(sweep * curW);
                com.astucy.client.ui.Skin.glow(g, sx, by + bh / 2, 18,
                        (((int) (0x36 * Math.sin(sweep * Math.PI) * vis)) << 24) | 0x4ADE80);
            }
        }

        float idleAng = (float) ((elapsed % 5000L) / 5000.0 * Math.PI * 2.0);
        float startX = gw * 0.5f;
        float startY = gh * 0.36f;
        float endX = bx + 14f;
        float endY = by + bh * 0.5f;
        int mainCol = (((int) (0xFF * vis)) << 24) | 0xFFFFFF;
        int accCol = (((int) (0xFF * vis)) << 24) | 0x22C55E;
        com.astucy.client.ui.Skin.logoTravel(g,
                startX, startY,
                endX, endY,
                inRaw,
                26f * vis, 13f * vis,
                idleAng,
                mainCol, accCol);
    }

    /** Mira customizada 17x17 em pixel-art configurÃƒÂ¡vel no menu R-SHIFT (1:1 com Rust). */
    private static void drawCrosshair(GuiGraphics g, Minecraft mc, Module m, int cx, int cy) {
        long[] rows = com.astucy.client.config.AstucyConfig.crosshairRows();
        boolean invert = com.astucy.client.config.AstucyConfig.isCrosshairInvert();
        boolean outline = m.shadow;
        int col = invert ? 0xEBE2FFF4 : m.fgColor;
        int outCol = 0xD804060A;

        if (outline) {
            for (int py = 0; py < 17; py++) {
                long row = rows[py];
                if (row == 0L) continue;
                for (int px = 0; px < 17; px++) {
                    if (((row >> px) & 1L) == 0L) continue;
                    int x = cx - 8 + px;
                    int y = cy - 8 + py;
                    Ui.rect(g, x - 1, y - 1, 3, 3, outCol);
                }
            }
        }
        for (int py = 0; py < 17; py++) {
            long row = rows[py];
            if (row == 0L) continue;
            for (int px = 0; px < 17; px++) {
                if (((row >> px) & 1L) == 0L) continue;
                Ui.rect(g, cx - 8 + px, cy - 8 + py, 1, 1, col);
            }
        }
    }

    /** Desenha um mÃƒÂ³dulo exatamente como ele aparece no HUD (usado no editor tambÃƒÂ©m). */
    public static void drawHud(GuiGraphics g, Font font, Module m, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        font = hudFont(m, font);
        if (m.id.equals("keystrokes")) {
            drawKeystrokes(g, font, m, x, y, mc);
            return;
        }
        if (m.id.equals("direcao")) {
            drawCompass(g, font, m, x, y, mc);
            return;
        }
        if (m.id.equals("armor")) {
            drawArmor(g, font, m, x, y, mc);
            return;
        }
        if (m.id.equals("efeitos")) {
            drawPotions(g, font, m, x, y, mc);
            return;
        }
        if (m.id.equals("watermark")) {
            drawWatermark(g, font, m, x, y);
            return;
        }
        if (m.id.equals("fpsgraph")) {
            drawFpsGraph(g, font, m, x, y, mc);
            return;
        }
        if (m.id.equals("target")) {
            drawTarget(g, font, m, x, y, mc);
            return;
        }
        String text = chipText(m, mc);
        if (text != null) {
            drawChip(g, font, m, x, y, text);
        }
    }

    /* ---- Target HUD (card moderno com barra de vida animada) ---- */

    private static final int TARGET_W = 124;
    private static final int TARGET_H = 34;
    private static float targetHpAnim = 1f;
    private static String lastTargetName = "Target";
    private static float lastTargetHp = 20f;
    private static float lastTargetMax = 20f;
    private static float lastTargetDist = 0f;
    private static long lastTargetSeenAt = 0L;

    private static void drawTarget(GuiGraphics g, Font font, Module m, int x, int y, Minecraft mc) {
        LivingEntity target = null;
        if (mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player && le.isAlive()) {
            target = le;
        }
        long now = System.currentTimeMillis();
        if (target != null) {
            lastTargetName = target.getName().getString();
            if (lastTargetName.length() > 15) {
                lastTargetName = lastTargetName.substring(0, 15);
            }
            lastTargetHp = target.getHealth() + target.getAbsorptionAmount();
            lastTargetMax = Math.max(1f, target.getMaxHealth() + target.getAbsorptionAmount());
            lastTargetDist = mc.player != null ? mc.player.distanceTo(target) : 0f;
            lastTargetSeenAt = now;
        } else if (preview) {
            lastTargetName = mc.player != null ? mc.player.getName().getString() : "Player";
            lastTargetHp = 18.5f;
            lastTargetMax = 20f;
            lastTargetDist = 3.1f;
            lastTargetSeenAt = now;
        }

        long elapsed = now - lastTargetSeenAt;
        if (!preview && target == null && elapsed > 1200L) {
            return;
        }
        float fade = preview || target != null ? 1f : Math.max(0f, 1f - (elapsed - 600f) / 600f);
        fade = Math.max(0f, Math.min(1f, fade));
        if (fade <= 0.02f) {
            return;
        }

        float ratio = Math.max(0f, Math.min(1f, lastTargetHp / lastTargetMax));
        targetHpAnim += (ratio - targetHpAnim) * 0.18f;

        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);

        if (m.bg) {
            int bgA = Math.round(((m.bgColor >>> 24) & 0xFF) * fade);
            chipBg(g, 0, 0, TARGET_W, TARGET_H, (bgA << 24) | (m.bgColor & 0x00FFFFFF));
        }

        int fgA = Math.round(((m.fgColor >>> 24) & 0xFF) * fade);
        int fg = (fgA << 24) | (m.fgColor & 0x00FFFFFF);
        int subCol = (Math.round(0xAA * fade) << 24) | 0x9AA2AB;

        // Indicador lateral / avatar estilizado
        boolean hurt = target != null && target.hurtTime > 0;
        int badgeCol = hurt
                ? ((Math.round(0xEE * fade) << 24) | 0xFF5D5D)
                : ((Math.round(0x33 * fade) << 24) | (m.fgColor & 0x00FFFFFF));
        Ui.rrect(g, 5, 5, 24, 24, badgeCol, 5);
        String initial = lastTargetName.isEmpty() ? "?" : lastTargetName.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        Ui.ctext(g, com.astucy.client.AstucyFonts.midBold(), initial, 17, 12, fg);

        // Nome + HP e distÃƒÂ¢ncia
        softText(g, com.astucy.client.AstucyFonts.bold(), lastTargetName, 34, 5, fg, m.shadow);
        String hpStr = String.format(java.util.Locale.ROOT, "%.1f HP  Ã‚Â·  %.1fm", lastTargetHp, lastTargetDist);
        g.drawString(font, hpStr, 34, 15, subCol, false);

        // Barra de vida suave
        int bx = 34;
        int by = 25;
        int bw = TARGET_W - bx - 6;
        int bh = 4;
        Ui.rrect(g, bx, by, bw, bh, (Math.round(0x44 * fade) << 24) | 0xFFFFFF, 2);
        int fillW = Math.round(bw * targetHpAnim);
        if (fillW > 0) {
            int hpRgb = ratio > 0.55f ? 0x38C975 : ratio > 0.25f ? 0xF0B449 : 0xF05454;
            Ui.rrect(g, bx, by, Math.max(2, fillW), bh, (fgA << 24) | hpRgb, 2);
        }

        pose.popPose();
    }

    /* ---- watermark e grÃƒÂ¡fico de FPS ---- */

    private static final int GRAPH_W = 96;
    private static final int GRAPH_H = 36;
    private static final int GRAPH_SAMPLES = 96;
    private static final Deque<Integer> FPS_HIST = new ArrayDeque<>();

    /** Marca do client: mini logo ÃƒÂris girando + "ASTUCY" + "0.1" (1:1 com Rust). */
    private static void drawWatermark(GuiGraphics g, Font font, Module m, int x, int y) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);

        Font bFont = com.astucy.client.AstucyFonts.bold();
        String mark = "ASTUCY";
        String ver = "0.1";
        int mw = bFont.width(mark);
        int h = font.lineHeight + 6;
        int w = 18 + mw + 4 + font.width(ver) + 6;

        if (m.bg) {
            chipBg(g, 0, 0, w, h, m.bgColor);
        }
        float spin = (float) ((System.currentTimeMillis() % 7400L) / 7400.0 * Math.PI * 2.0);
        com.astucy.client.ui.Skin.logo(g, 9f, h / 2f, 10f, spin, Ui.GREEN);
        softText(g, bFont, mark, 17, 3, m.fgColor, m.shadow);
        int dim = (m.fgColor & 0xFFFFFF) | 0x8E000000;
        g.drawString(font, ver, 17 + mw + 4, 3, dim, false);
        pose.popPose();
    }

    /** Sparkline do framerate: 96 amostras, cor por faixa (60+/30+/<30). */
    private static void drawFpsGraph(GuiGraphics g, Font font, Module m, int x, int y, Minecraft mc) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);

        if (m.bg) {
            chipBg(g, 0, 0, GRAPH_W, GRAPH_H, m.bgColor);
        }

        int pad = 4;
        int gw = GRAPH_W - pad * 2;
        int gh = GRAPH_H - pad * 2 - font.lineHeight;
        int top = pad + font.lineHeight;

        int[] vals = new int[FPS_HIST.size()];
        int idx = 0;
        int max = 60;
        for (int v : FPS_HIST) {
            vals[idx++] = v;
            if (v > max) {
                max = v;
            }
        }
        int start = Math.max(0, vals.length - gw);
        int count = vals.length - start;

        Ui.rect(g, pad, top + gh, gw, 1, 0x22FFFFFF);
        for (int k = 0; k < count; k++) {
            int v = vals[start + k];
            int bh = Math.max(1, Math.round(v / (float) max * gh));
            int rgb = v >= 60 ? 0x4FD07A : v >= 30 ? 0xE8C15C : 0xE25661;
            Ui.rect(g, pad + gw - count + k, top + gh - bh, 1, bh, 0xCC000000 | rgb);
        }

        softText(g, font, String.valueOf(mc.getFps()), pad, pad, m.fgColor, m.shadow);
        String lbl = "FPS";
        g.drawString(font, lbl, GRAPH_W - pad - font.width(lbl), pad, 0x669AA2AB, false);
        pose.popPose();
    }

    /* ---- bÃƒÂºssola estilo boss bar ---- */

    public static final int COMPASS_W = 200;
    public static final int COMPASS_H = 18;

    /** Deslocamento do desenho em relaÃƒÂ§ÃƒÂ£o ÃƒÂ  ÃƒÂ¢ncora (bÃƒÂºssola ÃƒÂ© centrada). */
    public static int drawOffsetX(Module m, int w) {
        return m.id.equals("direcao") ? -w / 2 : 0;
    }

    /**
     * DireÃƒÂ§ÃƒÂ£o em tempo real: faixa horizontal com N/NE/E/SE/S/SW/W/NW que
     * desliza suavemente com rotaÃƒÂ§ÃƒÂ£o em ponto flutuante (sem travadas de 1Ã‚Â°),
     * com marcador central fixo. Ãƒâ€šncora = centro da faixa.
     */
    private static void drawCompass(GuiGraphics g, Font font, Module m, int cx, int y, Minecraft mc) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) cx, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);

        int w = COMPASS_W;
        int h = COMPASS_H;
        int half = w / 2;
        int span = half - 10;

        if (m.bg) {
            chipBg(g, -half, 0, w, h, m.bgColor);
        }

        float yaw = mc.player.getYRot();
        float exactBearing = ((yaw + 180f) % 360f + 360f) % 360f;

        // traÃƒÂ§os menores a cada 15Ã‚Â° com interpolaÃƒÂ§ÃƒÂ£o contÃƒÂ­nua
        for (int bTick = 0; bTick < 360; bTick += 15) {
            if (bTick % 45 == 0) {
                continue;
            }
            float delta = angleDeltaF(bTick, exactBearing);
            if (Math.abs(delta) > 88f) {
                continue;
            }
            int lx = Math.round(delta / 90f * span);
            float fade = 1f - Math.abs(delta) / 90f;
            int alpha = Math.round(0x66 * fade);
            Ui.rect(g, lx, h - 5, 1, 3, (alpha << 24) | 0xFFFFFF);
        }

        // letras cardeais e intercardinais, com fade suave nas bordas
        String[] labels = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        Font boldFont = com.astucy.client.AstucyFonts.bold();
        for (int i = 0; i < 8; i++) {
            float delta = angleDeltaF(i * 45f, exactBearing);
            if (Math.abs(delta) > 88f) {
                continue;
            }
            int lx = Math.round(delta / 90f * span);
            float fade = 1f - Math.abs(delta) / 90f;
            int alpha = 0x35 + Math.round(0xCA * fade);
            int color = (Math.min(alpha, 0xFF) << 24) | (m.fgColor & 0xFFFFFF);
            Font fUse = (i % 2 == 0) ? boldFont : font;
            Ui.ctext(g, fUse, labels[i], lx, 4, color);
        }

        // marcador central (fixo) Ã¢â‚¬â€ indicador superior limpo
        Ui.rect(g, -1, 0, 2, 3, Ui.GREEN);
        Ui.rect(g, 0, 3, 1, h - 3, (m.fgColor & 0x00FFFFFF) | 0x55000000);
        pose.popPose();
    }

    /** Menor diferenÃƒÂ§a angular assinada entre dois rumos em float (-180..180). */
    private static float angleDeltaF(float a, float b) {
        float d = (a - b + 180f) % 360f;
        if (d < 0f) {
            d += 360f;
        }
        return d - 180f;
    }

    /** RÃƒÂ³tulo do chip (com ou sem prefixo, conforme customizaÃƒÂ§ÃƒÂ£o). */
    public static String chipText(Module m, Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        String raw = switch (m.id) {
            case "fps" -> fpsText(m, mc);
            case "ping" -> pingText(m, mc);
            case "server" -> serverValue(mc);
            case "memory" -> memoryText(m);
            case "speed" -> speedValue(mc);
            case "cps" -> cpsText(m);
            case "coords" -> coordsValue(mc, m);
            case "reach" -> reachValue(mc);
            case "totem" -> String.valueOf(mc.player == null ? 0 : mc.player.getInventory().countItem(Items.TOTEM_OF_UNDYING));
            case "armor" -> armorValue(mc);
            case "hora" -> worldTime(mc);
            case "bioma" -> biomeName(mc);
            case "direcao" -> compassLetter(mc.player.getYRot());
            case "sessao" -> sessionTime();
            case "sprint" -> m.enabled ? "ON" : "OFF";
            case "itens" -> mc.player.getMainHandItem().isEmpty()
                    ? "Ã¢â‚¬â€" : mc.player.getMainHandItem().getHoverName().getString()
                    + " x" + mc.player.getMainHandItem().getCount();
            case "pack" -> "Astucy";
            case "efeitos" -> effectCount(mc);
            case "health" -> healthValue(mc);
            case "hunger" -> hungerValue(mc);
            case "combo" -> comboValue();
            case "target" -> targetValue(mc);
            case "armordur" -> armorDurValue(mc);
            case "daycounter" -> dayValue(mc);
            case "itemcount" -> itemValue(mc);
            case "realtime" -> java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            default -> null;
        };
        if (raw == null) {
            return null;
        }
        return m.prefix ? prefixOf(m) + ": " + raw : raw;
    }

    private static void pruneClicks(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.removeFirst();
        }
    }

    /** CPS total, esquerdo/direito, invertido e com colchetes Ã¢â‚¬â€ pelas opÃƒÂ§ÃƒÂµes. */
    private static String cpsText(Module m) {
        String body;
        if (m.boolProp("split", true)) {
            body = m.boolProp("reverse", false)
                    ? CLICKS_R.size() + " | " + CLICKS_L.size()
                    : CLICKS_L.size() + " | " + CLICKS_R.size();
        } else {
            body = String.valueOf(CLICKS_L.size() + CLICKS_R.size());
        }
        return m.boolProp("brackets", false) ? "[" + body + "]" : body;
    }

    /** Fonte do chip: Montserrat anti-aliased por padrÃƒÂ£o (opÃƒÂ§ÃƒÂ£o por mÃƒÂ³dulo permite alternar). */
    public static Font hudFont(Module m, Font base) {
        if (m == null || m.boolProp("font", true)) {
            return com.astucy.client.AstucyFonts.ui();
        }
        return Minecraft.getInstance().font;
    }

    private static String prefixOf(Module m) {
        if (m.customLabel != null && !m.customLabel.isEmpty()) {
            return m.customLabel;
        }
        return switch (m.id) {
            case "fps" -> "FPS";
            case "ping" -> "Ping";
            case "server" -> "Server";
            case "memory" -> "RAM";
            case "speed" -> "Speed";
            case "cps" -> "CPS";
            case "coords" -> "Coords";
            case "reach" -> "Reach";
            case "totem" -> "Totems";
            case "armor" -> "Armor";
            case "hora" -> "Time";
            case "bioma" -> "Biome";
            case "direcao" -> "Direction";
            case "sessao" -> "Session";
            case "sprint" -> "Sprint";
            case "itens" -> "Item";
            case "pack" -> "Pack";
            case "efeitos" -> "Effects";
            case "health" -> "HP";
            case "hunger" -> "Food";
            case "combo" -> "Combo";
            case "target" -> "Target";
            case "armordur" -> "Durability";
            case "daycounter" -> "Day";
            case "itemcount" -> "Item";
            case "realtime" -> "Time";
            case "watermark" -> "Astucy";
            case "fpsgraph" -> "FPS";
            default -> m.name;
        };
    }

    /** Raio dos cantos do HUD (mesmo em todos os chips). */
    public static final int CHIP_R = 5;

    /** Fundo do HUD: vidro translÃƒÂºcido limpo, cantos anti-aliased, sem borda. */
    public static void chipBg(GuiGraphics g, int x, int y, int w, int h, int bg) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w < 6 || h < 6) {
            Ui.rect(g, x, y, w, h, bg);
            return;
        }
        Ui.rrect(g, x, y, w, h, bg, CHIP_R);
    }

    /**
     * Texto do HUD com sombra suave sub-pixel (0.6px, 40% preto) em vez da
     * sombra dura de 1px do vanilla Ã¢â‚¬â€ tira o visual "client velho".
     */
    public static void softText(GuiGraphics g, Font font, String s, int x, int y, int color, boolean shadow) {
        if (shadow) {
            int a = (color >>> 24) & 0xFF;
            int sa = Math.round(a * 0.40f);
            if (sa > 4) {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(0.6f, 0.6f, 0f);
                g.drawString(font, s, x, y, sa << 24, false);
                pose.popPose();
            }
        }
        g.drawString(font, s, x, y, color, false);
    }

    /** Largura fixa por mÃƒÂ³dulo: o fundo nÃƒÂ£o "pula" quando o valor muda de tamanho. */
    private static final Map<String, Integer> CHIP_W = new HashMap<>();

    /** Largura do chip (memo da maior jÃƒÂ¡ vista Ã¢â‚¬â€ sÃƒÂ³ cresce, nunca treme). */
    private static int chipWidth(Font font, Module m, String label) {
        int w = font.width(label) + 12;
        int memo = CHIP_W.getOrDefault(m.id, 0);
        // valor oscilando (1 dÃƒÂ­gito a mais/menos) mantÃƒÂ©m a caixa parada;
        // mudanÃƒÂ§a grande (coords, nome de item) deixa encolher de novo
        if (w >= memo || memo - w > 14) {
            CHIP_W.put(m.id, w);
            return w;
        }
        return memo;
    }

    /**
     * Chip do HUD Ã¢â‚¬â€ pill moderna e limpa: fundo translÃƒÂºcido anti-aliased +
     * hierarquia sutil entre prefixo e valor.
     */
    public static void drawChip(GuiGraphics g, Font font, Module m, int x, int y, String label) {
        font = hudFont(m, font);
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);
        int w = chipWidth(font, m, label);
        int h = font.lineHeight + 6;
        if (m.bg) {
            chipBg(g, 0, 0, w, h, m.bgColor);
        }
        int sep = m.prefix ? label.indexOf(": ") : -1;
        if (sep > 0) {
            String pre = label.substring(0, sep + 2);
            String val = label.substring(sep + 2);
            int fgA = (m.fgColor >>> 24) & 0xFF;
            int preA = Math.max(0x40, Math.round(fgA * 0.72f));
            int preCol = (preA << 24) | (m.fgColor & 0x00FFFFFF);
            softText(g, font, pre, 6, 3, preCol, m.shadow);
            softText(g, font, val, 6 + font.width(pre), 3, m.fgColor, m.shadow);
        } else {
            softText(g, font, label, 6, 3, m.fgColor, m.shadow);
        }
        pose.popPose();
    }

    /** Tamanho do chip para hit-testing no editor. */
    public static int[] chipSize(Font font, Module m, String label) {
        if (m.id.equals("keystrokes")) {
            int[] s = keySize(m, font);
            return new int[]{(int) Math.round(s[0] * m.scale), (int) Math.round(s[1] * m.scaleY)};
        }
        font = hudFont(m, font);
        if (m.id.equals("direcao")) {
            return new int[]{(int) Math.round(COMPASS_W * m.scale), (int) Math.round(COMPASS_H * m.scaleY)};
        }
        if (m.id.equals("armor") || m.id.equals("efeitos")) {
            int[] s = iconListSize(font, m);
            return new int[]{(int) Math.round(s[0] * m.scale), (int) Math.round(s[1] * m.scaleY)};
        }
        if (m.id.equals("watermark")) {
            Font bFont = com.astucy.client.AstucyFonts.bold();
            int mw = bFont.width("ASTUCY");
            int w = 18 + mw + 4 + font.width("0.1") + 6;
            return new int[]{(int) Math.round(w * m.scale),
                    (int) Math.round((font.lineHeight + 6) * m.scaleY)};
        }
        if (m.id.equals("fpsgraph")) {
            return new int[]{(int) Math.round(GRAPH_W * m.scale), (int) Math.round(GRAPH_H * m.scaleY)};
        }
        if (m.id.equals("target")) {
            return new int[]{(int) Math.round(TARGET_W * m.scale), (int) Math.round(TARGET_H * m.scaleY)};
        }
        int w = chipWidth(font, m, label);
        int h = font.lineHeight + 6;
        return new int[]{(int) Math.round(w * m.scale), (int) Math.round(h * m.scaleY)};
    }

    /** Teclas: tamanho configurÃƒÂ¡vel (12..32px) + 2 de gap, 3 colunas. */
    private static final int KEY_GAP = 2;
    private static final Map<String, Float> KEY_ANIM = new HashMap<>();

    private static int keyBox(Module m) {
        return (int) Math.max(12, Math.min(32, Math.round(m.numProp("box", 18))));
    }

    private static int[] keySize(Module m, Font font) {
        int s = keyBox(m);
        int gap = KEY_GAP;
        int mouseH = m.boolProp("keycps", false) ? s + font.lineHeight + 2 : s;
        int h = s + gap + s;
        if (m.boolProp("mouse", true)) {
            h += gap + mouseH;
        }
        if (m.boolProp("space", true)) {
            h += gap + Math.max(10, s * 2 / 3);
        }
        return new int[]{s * 3 + gap * 2, h};
    }

    private static void drawKeystrokes(GuiGraphics g, Font font, Module m, int x, int y, Minecraft mc) {
        int s = keyBox(m);
        int gap = KEY_GAP;
        int step = s + gap;
        int cols = s * 3 + gap * 2;
        boolean arrows = m.boolProp("arrows", false);
        boolean keycps = m.boolProp("keycps", false);
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);
        key(g, font, m, step, 0, s, s, arrows ? "^" : "W", null, mc.options.keyUp.isDown());
        key(g, font, m, 0, step, s, s, arrows ? "<" : "A", null, mc.options.keyLeft.isDown());
        key(g, font, m, step, step, s, s, arrows ? "v" : "S", null, mc.options.keyDown.isDown());
        key(g, font, m, step * 2, step, s, s, arrows ? ">" : "D", null, mc.options.keyRight.isDown());
        int cy = step * 2;
        if (m.boolProp("mouse", true)) {
            // LMB/RMB dividem a mesma largura da fileira A S D
            int half = (cols - gap) / 2;
            int mouseH = keycps ? s + font.lineHeight + 2 : s;
            key(g, font, m, 0, cy, half, mouseH, "LMB",
                    keycps ? String.valueOf(CLICKS_L.size()) : null, mc.options.keyAttack.isDown());
            key(g, font, m, half + gap, cy, cols - half - gap, mouseH, "RMB",
                    keycps ? String.valueOf(CLICKS_R.size()) : null, mc.options.keyUse.isDown());
            cy += mouseH + gap;
        }
        if (m.boolProp("space", true)) {
            int spaceH = Math.max(10, s * 2 / 3);
            key(g, font, m, 0, cy, cols, spaceH, "SPACE", null, mc.options.keyJump.isDown());
        }
        pose.popPose();
    }

    private static int lerpArgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (a >>> 24) & 0xFF, bA = (b >>> 24) & 0xFF;
        int aR = (a >>> 16) & 0xFF, bR = (b >>> 16) & 0xFF;
        int aG = (a >>> 8) & 0xFF, bG = (b >>> 8) & 0xFF;
        int aB = a & 0xFF, bB = b & 0xFF;
        return (Math.round(aA + (bA - aA) * t) << 24)
                | (Math.round(aR + (bR - aR) * t) << 16)
                | (Math.round(aG + (bG - aG) * t) << 8)
                | Math.round(aB + (bB - aB) * t);
    }

    private static final Map<String, Long> KEY_STAMP = new HashMap<>();

    private static void key(GuiGraphics g, Font font, Module m, int x, int y, int w, int h,
                            String label, String sub, boolean press) {
        // animaÃƒÂ§ÃƒÂ£o por tempo real (igual em 60 ou 400 fps)
        long nowNs = System.nanoTime();
        long last = KEY_STAMP.getOrDefault(label, nowNs);
        KEY_STAMP.put(label, nowNs);
        float dt = Math.min(0.05f, Math.max(0f, (nowNs - last) / 1_000_000_000f));
        float cur = KEY_ANIM.getOrDefault(label, 0f);
        float target = press ? 1f : 0f;
        float speed = press ? 34f : 14f;
        cur += (target - cur) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - cur) < 0.01f) {
            cur = target;
        }
        KEY_ANIM.put(label, cur);

        int baseBg = m.bg ? m.bgColor : 0x00000000;
        int pressBg = (0xE6 << 24) | (m.fgColor & 0x00FFFFFF);
        int bg = lerpArgb(baseBg, pressBg, cur);
        int pressFg = (0xFF << 24) | (m.bgColor & 0x00FFFFFF);
        if ((m.bgColor & 0x00FFFFFF) == (m.fgColor & 0x00FFFFFF)) {
            pressFg = 0xFF101318;
        }
        int fg = lerpArgb(m.fgColor, pressFg, cur);

        if (m.bg || cur > 0.02f) {
            // leve "afundar" ao pressionar (encolhe 1px nas bordas)
            int inset = Math.round(cur);
            Ui.rrect(g, x + inset, y + inset, w - inset * 2, h - inset * 2, bg, CHIP_R);
        }
        if ("SPACE".equals(label)) {
            int barW = Math.round(w * (0.38f + 0.14f * cur));
            Ui.rrect(g, x + (w - barW) / 2, y + h / 2 - 1, barW, 2, fg, 1);
        } else if (sub == null) {
            Ui.ctext(g, font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1, fg);
        } else {
            Ui.ctext(g, font, label, x + w / 2, y + 3, fg);
            Ui.ctext(g, font, sub, x + w / 2, y + 3 + font.lineHeight + 1,
                    (fg & 0x00FFFFFF) | 0xCC000000);
        }
    }

    /* ---- armadura: itens de verdade, aparecem conforme equipa ---- */

    /** Editor de HUD liga isso: mostra os mÃƒÂ³dulos "cheios" (dima full) pra ver o tamanho. */
    public static boolean preview = false;

    private static List<ItemStack> armorRows(Module m, Minecraft mc) {
        List<ItemStack> rows = new ArrayList<>();
        if (preview) {
            rows.add(previewPiece(Items.DIAMOND_HELMET, 1.0f));
            rows.add(previewPiece(Items.DIAMOND_CHESTPLATE, 0.75f));
            rows.add(previewPiece(Items.DIAMOND_LEGGINGS, 0.44f));
            rows.add(previewPiece(Items.DIAMOND_BOOTS, 0.12f));
            return rows;
        }
        if (mc.player == null) {
            return rows;
        }
        if (m.boolProp("hand", false)) {
            ItemStack hand = mc.player.getMainHandItem();
            if (!hand.isEmpty()) {
                rows.add(hand);
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = mc.player.getItemBySlot(slot);
            if (!s.isEmpty()) {
                rows.add(s);
            }
        }
        if (m.boolProp("offhand", false)) {
            ItemStack off = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!off.isEmpty()) {
                rows.add(off);
            }
        }
        return rows;
    }

    private static ItemStack previewPiece(net.minecraft.world.item.Item item, float ratio) {
        ItemStack st = new ItemStack(item);
        if (st.isDamageableItem() && ratio < 0.999f) {
            st.setDamageValue(Math.round(st.getMaxDamage() * (1f - ratio)));
        }
        return st;
    }

    private static float durRatio(ItemStack s) {
        if (!s.isDamageableItem()) {
            return 1f;
        }
        int max = Math.max(1, s.getMaxDamage());
        return Math.max(0f, Math.min(1f, (max - s.getDamageValue()) / (float) max));
    }

    /** Porcentagem ("100%") quando m.prefix == true ("Percentage (%)"), ou nÃƒÂºmero restante. */
    private static String durText(ItemStack s, Module m) {
        if (s.isDamageableItem()) {
            if (m.prefix) {
                return Math.round(durRatio(s) * 100f) + "%";
            }
            int left = Math.max(0, s.getMaxDamage() - s.getDamageValue());
            return m.boolProp("maxdur", false) ? left + "/" + s.getMaxDamage() : String.valueOf(left);
        }
        return m.boolProp("count", true) ? String.valueOf(s.getCount()) : "";
    }

    /** 5 faixas de cor idÃƒÂªnticas ao Rust (0x4ADE80 Ã¢â€ â€™ 0xA3E635 Ã¢â€ â€™ 0xFACC15 Ã¢â€ â€™ 0xFB923C Ã¢â€ â€™ 0xEF4444). */
    private static int durColor(ItemStack s, Module m) {
        if (!s.isDamageableItem() || !m.boolProp("dmgcolor", true)) {
            return m.fgColor;
        }
        float left = durRatio(s);
        int rgb;
        if (left > 0.75f) {
            rgb = 0x4ADE80;
        } else if (left > 0.50f) {
            rgb = 0xA3E635;
        } else if (left > 0.30f) {
            rgb = 0xFACC15;
        } else if (left > 0.15f) {
            rgb = 0xFB923C;
        } else {
            rgb = 0xEF4444;
        }
        int alpha = 0xFF;
        int threshold = (int) Math.round(m.numProp("lowpct", 20));
        if (m.boolProp("lowwarn", true) && left * 100f <= threshold) {
            alpha = (System.currentTimeMillis() / 280) % 2 == 0 ? 0xFF : 0x77;
        }
        return (alpha << 24) | rgb;
    }

    private static int armorBoxW(Font font, List<ItemStack> rows, Module m) {
        int textW = 0;
        for (ItemStack s : rows) {
            textW = Math.max(textW, font.width(durText(s, m)));
        }
        return Math.max(66, 26 + textW + 10);
    }

    private static void drawArmor(GuiGraphics g, Font font, Module m, int x, int y, Minecraft mc) {
        List<ItemStack> rows = armorRows(m, mc);
        if (rows.isEmpty()) {
            return;
        }
        int cw = armorBoxW(font, rows, m);
        int rowH = 18;
        int ch = rows.size() * rowH + 6;
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);
        if (m.bg) {
            chipBg(g, 0, 0, cw, ch, m.bgColor);
        }
        int leftPad = 24;
        int barW = Math.max(16, cw - leftPad - 6);
        for (int i = 0; i < rows.size(); i++) {
            ItemStack s = rows.get(i);
            int ry = 3 + i * rowH;
            String t = durText(s, m);
            float pct = durRatio(s);
            int color = durColor(s, m);
            g.renderItem(s, 4, ry + 1);
            softText(g, font, t, leftPad, ry + 2, color, m.shadow);
            Ui.rrect(g, leftPad, ry + rowH - 4, barW, 2, 0x22FFFFFF, 1);
            int fillW = Math.max(1, Math.round(barW * pct));
            Ui.rrect(g, leftPad, ry + rowH - 4, fillW, 2, (0xE8 << 24) | (color & 0xFFFFFF), 1);
        }
        pose.popPose();
    }

    /* ---- efeitos: ÃƒÂ­cone real de cada efeito ativo ---- */

    private static List<MobEffectInstance> effectRows(Module m, Minecraft mc) {
        if (preview) {
            // prÃƒÂ©via: trÃƒÂªs efeitos com ÃƒÂ­cone e tempo, pra ver o tamanho real
            return List.of(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1350, 1),
                    new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0),
                    new MobEffectInstance(MobEffects.DAMAGE_BOOST, 900, 0));
        }
        if (mc.player == null) {
            return List.of();
        }
        boolean hideAmbient = m.boolProp("hideambient", false);
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (hideAmbient && e.isAmbient()) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** NÃƒÂ­vel + nome + tempo, cada parte ligÃƒÂ¡vel nas opÃƒÂ§ÃƒÂµes do mÃƒÂ³dulo. */
    private static String potText(MobEffectInstance e, Module m) {
        List<String> parts = new ArrayList<>(3);
        // nÃƒÂ­vel vem primeiro ("2 Speed 1:07"), tempo por ÃƒÂºltimo
        if (m.boolProp("amplifier", true)) {
            parts.add(Integer.toString(e.getAmplifier() + 1));
        }
        if (m.boolProp("name", true)) {
            String path = e.getEffect().unwrapKey().map(k -> k.location().getPath()).orElse("effect");
            String n = potShortName(path);
            if (n == null) {
                n = path.replace('_', ' ');
                n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
            }
            parts.add(m.boolProp("uppercase", false) ? n.toUpperCase(java.util.Locale.ROOT) : n);
        }
        if (m.boolProp("duration", true)) {
            int dur = e.getDuration();
            if (dur >= 0) {
                int total = dur / 20;
                parts.add(String.format(java.util.Locale.ROOT, "%d:%02d", total / 60, total % 60));
            }
        }
        return String.join("  ", parts);
    }

    /**
     * Nome curto do efeito: "Night Vision II" vira "Night 2" em vez de ocupar
     * metade da tela. Efeito desconhecido devolve null (aÃƒÂ­ usa o nome do jogo).
     */
    private static String potShortName(String id) {
        return switch (id) {
            case "night_vision" -> "Night";
            case "fire_resistance" -> "Fire Res";
            case "water_breathing" -> "Water";
            case "invisibility" -> "Invis";
            case "jump_boost" -> "Jump";
            case "speed" -> "Speed";
            case "slowness" -> "Slow";
            case "strength" -> "Strength";
            case "weakness" -> "Weakness";
            case "poison" -> "Poison";
            case "regeneration" -> "Regen";
            case "resistance" -> "Resist";
            case "absorption" -> "Absorb";
            case "haste" -> "Haste";
            case "mining_fatigue" -> "Fatigue";
            case "hunger" -> "Hunger";
            case "instant_health" -> "Heal";
            case "instant_damage" -> "Harm";
            case "saturation" -> "Saturation";
            case "levitation" -> "Levitate";
            case "slow_falling" -> "Slow Fall";
            case "conduit_power" -> "Conduit";
            case "dolphins_grace" -> "Dolphin";
            case "bad_omen" -> "Bad Omen";
            case "hero_of_the_village" -> "Hero";
            case "glowing" -> "Glow";
            case "wither" -> "Wither";
            case "health_boost" -> "Health";
            case "luck" -> "Luck";
            case "unluck" -> "Unluck";
            case "blindness" -> "Blind";
            case "darkness" -> "Darkness";
            case "nausea" -> "Nausea";
            case "turtle_master" -> "Turtle";
            case "trial_omen" -> "Trial Omen";
            case "raid_omen" -> "Raid Omen";
            case "wind_charged" -> "Wind";
            case "weaving" -> "Weaving";
            case "oozing" -> "Oozing";
            case "infested" -> "Infested";
            default -> null;
        };
    }

    /** Pisca o efeito quando faltam poucos segundos. */
    private static int potColor(MobEffectInstance e, Module m) {
        int dur = e.getDuration();
        if (!m.boolProp("blink", true) || dur < 0 || dur > 200) {
            return m.fgColor;
        }
        boolean visible = (System.currentTimeMillis() / 250) % 2 == 0;
        return visible ? m.fgColor : (m.fgColor & 0x00FFFFFF) | 0x66000000;
    }

    private static String potTitle(MobEffectInstance e, Module m) {
        String path = e.getEffect().unwrapKey().map(k -> k.location().getPath()).orElse("effect");
        String n = potShortName(path);
        if (n == null) {
            n = path.replace('_', ' ');
            n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
        if (m.boolProp("uppercase", false)) {
            n = n.toUpperCase(java.util.Locale.ROOT);
        }
        int amp = e.getAmplifier();
        if (m.boolProp("amplifier", true) && amp > 0) {
            n += " " + romanNum(amp + 1);
        }
        return n;
    }

    private static String potDuration(MobEffectInstance e, Module m) {
        if (!m.boolProp("duration", true)) {
            return "";
        }
        int dur = e.getDuration();
        if (dur < 0) {
            return "**:**";
        }
        int total = dur / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private static String romanNum(int v) {
        return switch (v) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(v);
        };
    }

    private static int potCardW(Font font, List<MobEffectInstance> rows, Module m) {
        int maxW = 76;
        for (MobEffectInstance e : rows) {
            int nw = font.width(potTitle(e, m));
            int dw = font.width(potDuration(e, m));
            maxW = Math.max(maxW, nw + dw + 26);
        }
        return maxW;
    }

    private static void drawPotions(GuiGraphics g, Font font, Module m, int x, int y, Minecraft mc) {
        List<MobEffectInstance> rows = effectRows(m, mc);
        if (rows.isEmpty()) {
            return;
        }
        int cw = potCardW(font, rows, m);
        int rowH = 14;
        int ch = rows.size() * rowH + 6;
        var pose = g.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0f);
        pose.scale((float) m.scale, (float) m.scaleY, 1f);
        if (m.bg) {
            chipBg(g, 0, 0, cw, ch, m.bgColor);
        }
        int fgA = (m.fgColor >>> 24) & 0xFF;
        int preA = Math.max(0x40, Math.round(fgA * 0.72f));
        int preCol = (preA << 24) | (m.fgColor & 0x00FFFFFF);
        for (int i = 0; i < rows.size(); i++) {
            MobEffectInstance e = rows.get(i);
            int ry = 3 + i * rowH;
            com.astucy.client.ui.Skin.disc(g, 5, ry + 4, 4, 0xE622C55E);
            String title = potTitle(e, m);
            String dur = potDuration(e, m);
            softText(g, font, title, 13, ry + 2, potColor(e, m), m.shadow);
            if (!dur.isEmpty()) {
                int dw = font.width(dur);
                softText(g, font, dur, cw - 6 - dw, ry + 2, preCol, m.shadow);
            }
        }
        pose.popPose();
    }

    /** Tamanho (sem escala) das listas com ÃƒÂ­cone. */
    private static int[] iconListSize(Font font, Module m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.id.equals("armor")) {
            List<ItemStack> rows = armorRows(m, mc);
            return rows.isEmpty() ? new int[]{0, 0}
                    : new int[]{armorBoxW(font, rows, m), rows.size() * 18 + 6};
        }
        List<MobEffectInstance> rows = effectRows(m, mc);
        if (rows.isEmpty()) {
            return new int[]{0, 0};
        }
        return new int[]{potCardW(font, rows, m), rows.size() * 14 + 6};
    }

    public static void resetPreset() {
        java.util.Set<String> keep = java.util.Set.of("sprint", "fps", "ping", "cps");
        for (Module m : REGISTRY) {
            m.enabled = keep.contains(m.id);
        }
        com.astucy.client.config.AstucyConfig.save();
    }

    public static void resetPositions() {
        java.util.Map<String, double[]> defAnchors = new java.util.HashMap<>();
        defAnchors.put("fps", new double[]{0.012, 0.02});
        defAnchors.put("ping", new double[]{0.012, 0.06});
        defAnchors.put("server", new double[]{0.012, 0.098});
        defAnchors.put("memory", new double[]{0.012, 0.136});
        defAnchors.put("speed", new double[]{0.012, 0.174});
        defAnchors.put("direcao", new double[]{0.5, 0.02});
        defAnchors.put("sessao", new double[]{0.012, 0.25});
        defAnchors.put("hora", new double[]{0.845, 0.135});
        defAnchors.put("bioma", new double[]{0.845, 0.173});
        defAnchors.put("coords", new double[]{0.845, 0.02});
        defAnchors.put("efeitos", new double[]{0.845, 0.211});
        defAnchors.put("cps", new double[]{0.012, 0.88});
        defAnchors.put("keystrokes", new double[]{0.012, 0.62});
        defAnchors.put("reach", new double[]{0.845, 0.88});
        defAnchors.put("totem", new double[]{0.845, 0.94});
        defAnchors.put("armor", new double[]{0.845, 0.835});
        defAnchors.put("sprint", new double[]{0.012, 0.30});
        defAnchors.put("itens", new double[]{0.012, 0.338});
        defAnchors.put("health", new double[]{0.012, 0.414});
        defAnchors.put("hunger", new double[]{0.012, 0.452});
        defAnchors.put("combo", new double[]{0.012, 0.49});
        defAnchors.put("pack", new double[]{0.012, 0.376});
        defAnchors.put("target", new double[]{0.845, 0.28});
        defAnchors.put("armordur", new double[]{0.845, 0.34});
        defAnchors.put("daycounter", new double[]{0.012, 0.528});
        defAnchors.put("itemcount", new double[]{0.012, 0.566});
        defAnchors.put("watermark", new double[]{0.5, 0.955});
        defAnchors.put("fpsgraph", new double[]{0.845, 0.40});
        defAnchors.put("realtime", new double[]{0.845, 0.56});
        for (Module m : REGISTRY) {
            double[] a = defAnchors.get(m.id);
            if (a != null) {
                m.anchorX = a[0];
                m.anchorY = a[1];
            }
            m.scale = 1.0;
            m.scaleY = 1.0;
        }
        com.astucy.client.config.AstucyConfig.save();
    }

    /** MÃƒÂ³dulo tem o que desenhar agora? (armadura/efeitos podem estar vazios) */
    public static boolean hasContent(Module m) {
        if (m == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (m.id.equals("armor")) {
            return !armorRows(m, mc).isEmpty();
        }
        if (m.id.equals("efeitos")) {
            return !effectRows(m, mc).isEmpty();
        }
        return true;
    }

    /* ---- valores ---- */

    private static String coordsValue(Minecraft mc, Module m) {
        LocalPlayer p = mc.player;
        boolean dec = m.boolProp("decimal", false);
        int places = (int) Math.max(0, Math.min(4, Math.round(m.numProp("decimals", 1))));
        String base = coord(p.getX(), dec, places) + "  " + coord(p.getY(), dec, places)
                + "  " + coord(p.getZ(), dec, places);
        if (m.boolProp("nether", false)) {
            String dim = p.level().dimension().location().getPath();
            if (dim.endsWith("the_nether")) {
                base += "  [" + Math.round(p.getX() * 8) + " " + Math.round(p.getZ() * 8) + "]";
            }
        }
        return m.boolProp("facing", true) ? base + "  " + compassLetter(p.getYRot()) : base;
    }

    private static String coord(double v, boolean decimal, int places) {
        if (!decimal) {
            return String.valueOf((int) v);
        }
        return String.format(java.util.Locale.ROOT, "%." + places + "f", v);
    }

    public static String compassLetter(float yaw) {
        return switch (Math.floorMod((int) Math.round(yaw / 90.0), 4)) {
            case 0 -> "S";
            case 1 -> "W";
            case 2 -> "N";
            default -> "E";
        };
    }

    private static String pingValue(Minecraft mc) {
        if (mc.getConnection() == null) {
            return "-";
        }
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return (info == null ? 0 : info.getLatency()) + " ms";
    }

    /** FPS com opÃƒÂ§ÃƒÂµes: valor instantÃƒÂ¢neo, mÃƒÂ©dia da ÃƒÂºltima segunda e min/max. */
    private static String fpsText(Module m, Minecraft mc) {
        int now = mc.getFps();
        boolean avg = m.boolProp("avg", false);
        boolean mm = m.boolProp("minmax", false);
        if (!avg && !mm) {
            return String.valueOf(now);
        }
        int lo = Integer.MAX_VALUE;
        int hi = 0;
        int sum = 0;
        int n = 0;
        for (int v : FPS_HIST) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            sum += v;
            n++;
        }
        if (n == 0) {
            return String.valueOf(now);
        }
        String base = avg ? String.valueOf(sum / n) : String.valueOf(now);
        return mm ? base + " (" + lo + "-" + hi + ")" : base;
    }

    private static String pingText(Module m, Minecraft mc) {
        if (mc.getConnection() == null) {
            return "-";
        }
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int ms = info == null ? 0 : info.getLatency();
        return m.boolProp("ms", true) ? ms + " ms" : String.valueOf(ms);
    }

    private static String memoryText(Module m) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
        if (!m.boolProp("percent", false)) {
            return used + " MB";
        }
        long max = Math.max(1, rt.maxMemory() >> 20);
        return used + " / " + max + " MB (" + Math.round(used * 100f / max) + "%)";
    }

    private static String serverValue(Minecraft mc) {
        var sd = mc.getCurrentServer();
        return sd == null ? "singleplayer" : sd.ip;
    }

    private static String speedValue(Minecraft mc) {
        var v = mc.player.getDeltaMovement();
        return String.format("%.1f m/s", Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20.0);
    }

    private static String reachValue(Minecraft mc) {
        // distÃƒÂ¢ncia verdadeira de alcance: posiÃƒÂ§ÃƒÂ£o do OLHO Ã¢â€ â€™ ponto onde a
        // mira acerta a hitbox (o crosshairPickEntity ÃƒÂ© o mesmo alvo do hit)
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e == null || e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) {
                return null;
            }
            float d = (float) mc.player.getEyePosition().distanceTo(ehr.getLocation());
            return String.format(java.util.Locale.ROOT, "%.2f m", d);
        }
        return null;
    }

    private static String armorValue(Minecraft mc) {
        return Math.round(mc.player.getArmorCoverPercentage() * 100.0f) + "%";
    }

    /** Hora do mundo do servidor (0 tick = 06:00). */
    private static String worldTime(Minecraft mc) {
        long t = mc.player.level().getDayTime();
        long day = Math.floorMod(t, 24000L);
        int h = (int) ((day / 1000L + 6) % 24);
        int m = (int) ((day % 1000L) * 60L / 1000L);
        return String.format("%02d:%02d", h, m);
    }

    private static String biomeName(Minecraft mc) {
        try {
            var holder = mc.player.level().getBiome(mc.player.blockPosition());
            var key = holder.unwrapKey();
            if (key.isPresent()) {
                String path = key.get().location().getPath();
                if (path.length() > 0) {
                    path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
                }
                return path;
            }
        } catch (RuntimeException ignored) {
            // fallback silencioso
        }
        return "Ã¢â‚¬â€";
    }

    private static String sessionTime() {
        long ms = System.currentTimeMillis() - sessionStart;
        long s = Math.max(0, ms / 1000L);
        return String.format("%02d:%02d", s / 60L, s % 60L);
    }

    private static String effectCount(Minecraft mc) {
        int n = mc.player.getActiveEffects().size();
        return n + (n == 1 ? " efeito" : " efeitos");
    }

    /* ---- novos mÃƒÂ³dulos ---- */

    /** Alvo sob a mira: nome + vida (o chip some quando nÃƒÂ£o hÃƒÂ¡ alvo vivo). */
    private static String targetValue(Minecraft mc) {
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()) {
            return null;
        }
        String name = le.getName().getString();
        if (name.length() > 14) {
            name = name.substring(0, 14);
        }
        float hp = le.getHealth() + le.getAbsorptionAmount();
        return name + "  " + String.format(java.util.Locale.ROOT, "%.1f", hp);
    }

    /** Durabilidade das 4 peÃƒÂ§as equipadas (capacete Ã¢â€ â€™ botas). */
    private static String armorDurValue(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            var st = mc.player.getItemBySlot(slot);
            if (st.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(st.isDamageableItem() ? String.valueOf(st.getMaxDamage() - st.getDamageValue()) : "Ã¢â‚¬â€");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String dayValue(Minecraft mc) {
        long t = mc.player.level().getDayTime();
        return String.valueOf(t / 24000L + 1L);
    }

    private static String itemValue(Minecraft mc) {
        var st = mc.player.getMainHandItem();
        if (st.isEmpty()) {
            return null;
        }
        String s = st.getCount() + "x " + st.getHoverName().getString();
        if (st.isDamageableItem()) {
            s += "  " + (st.getMaxDamage() - st.getDamageValue());
        }
        return s;
    }
}
