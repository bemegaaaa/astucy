package com.astucy.client.module;

import com.astucy.client.config.AstucyConfig;
import com.astucy.client.gui.HudEditorScreen;
import com.astucy.client.ui.Skin;
import com.astucy.client.ui.Ui;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.resource.ResourcePackLoader;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Modules {
    public static final List<Module> ALL = new ArrayList<Module>();
    private static final Map<String, Module> BY_ID = new LinkedHashMap<String, Module>();

    // Default active preset: sprint, fps, ping, cps
    public static final Module SPRINT = reg(new Module("sprint", "Auto Sprint", "Automatically holds sprint while moving", ModuleCategory.COMBATE, true).pos(8f, 8f).prop("label", true));
    public static final Module FPS = reg(new Module("fps", "FPS Counter", "Live frames per second readout", ModuleCategory.INFORMACAO, true).pos(8f, 28f));
    public static final Module PING = reg(new Module("ping", "Ping", "Current server network latency", ModuleCategory.INFORMACAO, true).pos(8f, 48f));
    public static final Module CPS = reg(new Module("cps", "CPS Counter", "Left and right clicks per second", ModuleCategory.COMBATE, true).pos(8f, 68f).prop("rmb", true));

    public static final Module ARMOR = reg(new Module("armor", "Armor Status", "Equipped armor icons, percentage and 5-tier durability bar", ModuleCategory.COMBATE, false)
        .pos(8f, 110f).prop("percent", true).prop("bar", true).prop("prefix", true).prop("horizontal", false).prop("low_blink", true));
    public static final Module EFEITOS = reg(new Module("efeitos", "Effects", "Active potion effects and remaining timers", ModuleCategory.VISAO, false)
        .pos(8f, 195f).prop("compact", false));
    public static final Module CROSSHAIR = reg(new Module("crosshair", "Crosshair", "Custom 17x17 pixel crosshair editor", ModuleCategory.COMBATE, false).nonHud());
    public static final Module WATERMARK = reg(new Module("watermark", "Watermark", "Astucy Iris emblem and brand pill", ModuleCategory.VISAO, false).pos(8f, 260f));

    public static final Module KEYSTROKES = reg(new Module("keystrokes", "Keystrokes", "WASD, mouse clicks, and spacebar display", ModuleCategory.COMBATE, false)
        .pos(100f, 8f).prop("mouse", true).prop("space", true).prop("cps", true));
    public static final Module COORDS = reg(new Module("coords", "Coordinates", "X, Y, Z coordinates and facing direction", ModuleCategory.INFORMACAO, false).pos(100f, 90f));
    public static final Module DIRECAO = reg(new Module("direcao", "Compass", "Smooth cardinal direction strip", ModuleCategory.INFORMACAO, false).pos(180f, 8f));
    public static final Module SERVER = reg(new Module("server", "Server IP", "Current server address or Singleplayer", ModuleCategory.INFORMACAO, false).pos(100f, 110f));
    public static final Module MEMORY = reg(new Module("memory", "Memory", "JVM heap usage in megabytes", ModuleCategory.INFORMACAO, false).pos(100f, 130f));
    public static final Module SPEED = reg(new Module("speed", "Speed", "Horizontal movement speed in m/s", ModuleCategory.INFORMACAO, false).pos(100f, 150f));
    public static final Module REACH = reg(new Module("reach", "Reach", "Last attack hit distance in blocks", ModuleCategory.COMBATE, false).pos(100f, 170f));
    public static final Module COMBO = reg(new Module("combo", "Combo", "Consecutive hits landed without taking damage", ModuleCategory.COMBATE, false).pos(100f, 190f));
    public static final Module BIOMA = reg(new Module("bioma", "Biome", "Current world biome name", ModuleCategory.INFORMACAO, false).pos(100f, 210f));
    public static final Module HORA = reg(new Module("hora", "World Time", "In-game 24h clock", ModuleCategory.INFORMACAO, false).pos(100f, 230f));
    public static final Module REALTIME = reg(new Module("realtime", "Real Time", "Local wall-clock time", ModuleCategory.INFORMACAO, false).pos(100f, 250f));
    public static final Module DAYCOUNTER = reg(new Module("daycounter", "Day Counter", "Total Minecraft days elapsed", ModuleCategory.INFORMACAO, false).pos(100f, 270f));
    public static final Module SESSAO = reg(new Module("sessao", "Session Timer", "Elapsed play session time", ModuleCategory.INFORMACAO, false).pos(100f, 290f));
    public static final Module HEALTH = reg(new Module("health", "Health", "Numeric HP and absorption readout", ModuleCategory.COMBATE, false).pos(190f, 40f));
    public static final Module HUNGER = reg(new Module("hunger", "Hunger", "Food and saturation bar readout", ModuleCategory.INFORMACAO, false).pos(190f, 60f));
    public static final Module ITENS = reg(new Module("itens", "Held Item", "Active main-hand item and durability", ModuleCategory.INFORMACAO, false).pos(190f, 80f));
    public static final Module PACK = reg(new Module("pack", "Resource Pack", "Active resource pack display", ModuleCategory.INFORMACAO, false).pos(190f, 100f));
    public static final Module ARMORDUR = reg(new Module("armordur", "Armor %", "Aggregate armor durability percentage", ModuleCategory.COMBATE, false).pos(190f, 120f));
    public static final Module ITEMCOUNT = reg(new Module("itemcount", "Item Counter", "Total count of held item across inventory", ModuleCategory.INFORMACAO, false).pos(190f, 140f));
    public static final Module TARGET = reg(new Module("target", "Target HUD", "Focused entity name and health bar", ModuleCategory.COMBATE, false).pos(220f, 170f));
    public static final Module FPSGRAPH = reg(new Module("fpsgraph", "FPS Graph", "Frame rate history sparkline", ModuleCategory.INFORMACAO, false).pos(220f, 220f));

    public static final Module ZOOM = reg(new Module("zoom", "Zoom", "Hold C to smoothly zoom camera", ModuleCategory.VISAO, false).nonHud().prop("sens_scale", true).prop("smooth", true));
    public static final Module FULLBRIGHT = reg(new Module("fullbright", "Fullbright", "Maximum brightness in caves and night", ModuleCategory.VISAO, false).nonHud());
    public static final Module HURTCAM = reg(new Module("hurtcam", "No Hurt Cam", "Disables camera shake on taking damage", ModuleCategory.VISAO, false).nonHud());
    public static final Module SNEAK = reg(new Module("sneak", "Toggle Sneak", "Tap sneak key to toggle crouch state", ModuleCategory.UTILIDADE, false).nonHud());
    public static final Module SPEEDFX = reg(new Module("speedfx", "No Speed FOV", "Keeps FOV steady when sprinting or sped up", ModuleCategory.VISAO, false).nonHud());
    public static final Module CIT = reg(new Module("cit", "CIT Resewn (CIT)", "OptiFine custom item textures", ModuleCategory.VISAO, false).nonHud().isNew());

    static {
        for (String id : new String[] { "sprint", "fps", "ping", "cps", "cit" }) {
            Module m = byId(id);
            if (m != null) {
                m.enabled = true;
            }
        }
    }

    private static Module reg(Module m) {
        ALL.add(m);
        BY_ID.put(m.id, m);
        return m;
    }

    public static Module byId(String id) {
        return BY_ID.get(id);
    }

    // Runtime state
    private static final Deque<Long> lmbClicks = new ArrayDeque<Long>();
    private static final Deque<Long> rmbClicks = new ArrayDeque<Long>();
    private static boolean prevLmb = false;
    private static boolean prevRmb = false;

    private static final int[] fpsHistory = new int[90];
    private static int fpsCursor = 0;
    private static long lastFpsSample = 0L;

    private static boolean zoomHeld = false;
    private static float zoomProgress = 0f;
    private static boolean sensAdjusted = false;
    private static float savedSensitivity = 0.5f;

    private static float prevGamma = -1f;
    private static boolean sneakToggled = false;
    private static boolean prevSneakKey = false;

    private static double lastPx = 0.0, lastPz = 0.0;
    private static double currentSpeedBps = 0.0;

    private static String lastReachStr = "0.00";
    private static long lastReachAt = 0L;

    private static int comboCount = 0;
    private static long lastComboAt = 0L;
    private static int lastHurtTime = 0;

    private static LivingEntity lastTarget = null;
    private static long lastTargetAt = 0L;

    private static long sessionStartMs = System.currentTimeMillis();
    private static boolean sentJoinHint = false;
    private static long joinAnimUntil = 0L;

    private static float lastDeltaSec = 0.016f;
    private static long lastRenderNs = System.nanoTime();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    private Modules() {}

    public static void setZoomHeld(boolean held) {
        zoomHeld = held;
    }

    public static boolean isZoomActive() {
        return ZOOM.enabled && zoomProgress > 0.005f;
    }

    public static float applyZoomFov(float baseFov) {
        if (!ZOOM.enabled) {
            if (sensAdjusted) {
                MinecraftClient.getInstance().options.sensitivity = savedSensitivity;
                sensAdjusted = false;
            }
            return baseFov;
        }
        float target = zoomHeld ? 1f : 0f;
        float speed = ZOOM.bool("smooth", true) ? 14f : 60f;
        zoomProgress += (target - zoomProgress) * Math.min(1f, lastDeltaSec * speed);
        if (Math.abs(target - zoomProgress) < 0.001f) zoomProgress = target;

        if (ZOOM.bool("sens_scale", true)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (zoomProgress > 0.05f) {
                if (!sensAdjusted) {
                    savedSensitivity = mc.options.sensitivity;
                    sensAdjusted = true;
                }
                float mult = 1f - 0.65f * zoomProgress;
                mc.options.sensitivity = savedSensitivity * mult;
            } else if (sensAdjusted) {
                mc.options.sensitivity = savedSensitivity;
                sensAdjusted = false;
            }
        }
        float zoomed = baseFov * 0.27f;
        return baseFov + (zoomed - baseFov) * zoomProgress;
    }

    public static boolean isSneakToggled() {
        return SNEAK.enabled && sneakToggled;
    }

    public static void onClientTick(MinecraftClient mc) {
        AstucyConfig.tickFlush();
        if (mc == null) return;

        if (mc.world == null || mc.player == null) {
            sentJoinHint = false;
            comboCount = 0;
            lastTarget = null;
            return;
        }

        if (!sentJoinHint) {
            sentJoinHint = true;
            sessionStartMs = System.currentTimeMillis();
            joinAnimUntil = System.currentTimeMillis() + 3600L;
            mc.player.addMessage(new LiteralText(
                Formatting.GREEN + "[Astucy] " + Formatting.WHITE + "Client loaded - press "
                    + Formatting.AQUA + "R-SHIFT" + Formatting.WHITE + " for menu"
            ));
        }

        ClientPlayerEntity player = mc.player;

        // Auto Sprint
        if (SPRINT.enabled && mc.currentScreen == null) {
            KeyBinding.setKeyPressed(mc.options.sprintKey.getCode(), true);
        }

        // Toggle Sneak
        if (SNEAK.enabled && mc.currentScreen == null) {
            boolean down = Keyboard.isKeyDown(mc.options.sneakKey.getCode());
            if (down && !prevSneakKey) {
                sneakToggled = !sneakToggled;
            }
            prevSneakKey = down;
        } else {
            sneakToggled = false;
            prevSneakKey = false;
        }

        // Fullbright
        if (FULLBRIGHT.enabled) {
            if (prevGamma < 0f) prevGamma = mc.options.gamma;
            mc.options.gamma = 12f;
        } else if (prevGamma >= 0f) {
            mc.options.gamma = prevGamma;
            prevGamma = -1f;
        }

        // Hurtcam cancel
        if (HURTCAM.enabled) {
            player.hurtTime = 0;
        }

        // Speed tracking
        double dx = player.x - lastPx;
        double dz = player.z - lastPz;
        double rawBps = Math.hypot(dx, dz) * 20.0;
        if (rawBps < 80.0) {
            currentSpeedBps = currentSpeedBps * 0.65 + rawBps * 0.35;
        }
        lastPx = player.x;
        lastPz = player.z;

        // Combo reset on hurt or timeout
        if (player.hurtTime > lastHurtTime && player.hurtTime > 0) {
            comboCount = 0;
        }
        lastHurtTime = player.hurtTime;
        if (comboCount > 0 && System.currentTimeMillis() - lastComboAt > 4000L) {
            comboCount = 0;
        }

        // Target HUD tracking
        if (mc.targetedEntity instanceof LivingEntity) {
            lastTarget = (LivingEntity) mc.targetedEntity;
            lastTargetAt = System.currentTimeMillis();
        } else if (lastTarget != null && (!lastTarget.isAlive() || System.currentTimeMillis() - lastTargetAt > 3500L)) {
            lastTarget = null;
        }

        // FPS graph sampling (~10Hz)
        long now = System.currentTimeMillis();
        if (now - lastFpsSample >= 100L) {
            lastFpsSample = now;
            fpsHistory[fpsCursor] = Math.max(0, MinecraftClient.getCurrentFps());
            fpsCursor = (fpsCursor + 1) % fpsHistory.length;
        }
    }

    public static void sampleClicks(MinecraftClient mc) {
        boolean inGui = mc.currentScreen != null;
        boolean lDown = !inGui && Mouse.isButtonDown(0);
        boolean rDown = !inGui && Mouse.isButtonDown(1);
        long now = System.currentTimeMillis();

        if (lDown && !prevLmb) {
            lmbClicks.addLast(now);
            onLeftAttackSample(mc, now);
        }
        if (rDown && !prevRmb) {
            rmbClicks.addLast(now);
        }
        prevLmb = lDown;
        prevRmb = rDown;

        while (!lmbClicks.isEmpty() && now - lmbClicks.peekFirst() > 1000L) lmbClicks.removeFirst();
        while (!rmbClicks.isEmpty() && now - rmbClicks.peekFirst() > 1000L) rmbClicks.removeFirst();
    }

    private static void onLeftAttackSample(MinecraftClient mc, long now) {
        if (mc.player == null) return;
        if (mc.result != null && mc.result.type == BlockHitResult.Type.ENTITY && mc.result.entity != null) {
            Entity target = mc.result.entity;
            double dist = Math.sqrt(
                Math.pow(target.x - mc.player.x, 2)
                    + Math.pow((target.y + target.getEyeHeight() * 0.5) - (mc.player.y + mc.player.getEyeHeight()), 2)
                    + Math.pow(target.z - mc.player.z, 2)
            );
            lastReachStr = String.format(Locale.US, "%.2f", dist);
            lastReachAt = now;
            comboCount++;
            lastComboAt = now;
        }
    }

    public static int lmbCps() {
        return lmbClicks.size();
    }

    public static int rmbCps() {
        return rmbClicks.size();
    }

    public static void renderHud(MinecraftClient mc) {
        long nowNs = System.nanoTime();
        lastDeltaSec = Math.min(0.1f, Math.max(0.001f, (nowNs - lastRenderNs) / 1_000_000_000f));
        lastRenderNs = nowNs;

        if (mc == null || mc.player == null || mc.world == null) return;
        sampleClicks(mc);
        if (mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) return; // editor renders its own preview

        Window win = new Window(mc);
        int sw = (int) win.getScaledWidth();
        int sh = (int) win.getScaledHeight();

        // Crosshair renders first at screen center
        if (CROSSHAIR.enabled && mc.currentScreen == null && mc.options.perspective == 0) {
            renderCustomCrosshair(sw, sh);
        }

        for (Module m : ALL) {
            if (m.hud && m.enabled) {
                renderModule(mc, m, sw, sh, false);
            }
        }

        // Subtle join toast animation
        long left = joinAnimUntil - System.currentTimeMillis();
        if (left > 0L) {
            float alpha = left < 500L ? left / 500f : (left > 3100L ? (3600L - left) / 500f : 1f);
            float tw = 158f;
            float tx = (sw - tw) * 0.5f;
            float ty = 12f - (1f - alpha) * 10f;
            Ui.rrect(tx, ty, tw, 22f, 6f, Ui.withA(0xEE0B0E14, alpha));
            Ui.rframe(tx, ty, tw, 22f, 6f, 1f, Ui.withA(Ui.accent(), alpha * 0.8f));
            Skin.drawTex(Skin.logoIris(28, Ui.accent()), tx + 6f, ty + 4f, 14f, 14f, Ui.setA(0xFFFFFF, Math.round(255 * alpha)));
            Ui.text("ASTUCY CLIENT READY", tx + 26f, ty + 7f, Ui.withA(0xFFF3F6FA, alpha));
        }
    }

    public static void renderModule(MinecraftClient mc, Module m, int sw, int sh, boolean preview) {
        float x = resolveX(m, sw);
        float y = resolveY(m, sh);
        float s = Math.max(0.5f, m.scale);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(s, s, 1f);

        float w = 48f;
        float h = 16f;

        if ("armor".equals(m.id)) {
            float[] dims = renderArmor(mc, m, preview);
            w = dims[0];
            h = dims[1];
        } else if ("efeitos".equals(m.id)) {
            float[] dims = renderEffects(mc, m, preview);
            w = dims[0];
            h = dims[1];
        } else if ("keystrokes".equals(m.id)) {
            float[] dims = renderKeystrokes(mc, m);
            w = dims[0];
            h = dims[1];
        } else if ("direcao".equals(m.id)) {
            float[] dims = renderCompass(mc, m);
            w = dims[0];
            h = dims[1];
        } else if ("watermark".equals(m.id)) {
            float[] dims = renderWatermark(m);
            w = dims[0];
            h = dims[1];
        } else if ("target".equals(m.id)) {
            float[] dims = renderTargetHud(mc, m, preview);
            w = dims[0];
            h = dims[1];
        } else if ("fpsgraph".equals(m.id)) {
            float[] dims = renderFpsGraph(m);
            w = dims[0];
            h = dims[1];
        } else {
            String label = chipText(mc, m, preview);
            float padX = 6f;
            float padY = 4f;
            w = Math.max(36f, Ui.tw(label) + padX * 2f);
            h = Ui.th() + padY * 2f;
            drawCard(m, w, h);
            Ui.textS(label, padX, padY, Ui.withA(m.fgColor, m.opacity));
        }

        GlStateManager.popMatrix();

        m.lastX = x;
        m.lastY = y;
        m.lastW = w * s;
        m.lastH = h * s;
    }

    public static float resolveX(Module m, int sw) {
        if (m.anchorX > 0.5f) {
            return sw - m.x - m.lastW;
        }
        return Ui.clamp(m.x, 2f, Math.max(2f, sw - 16f));
    }

    public static float resolveY(Module m, int sh) {
        if (m.anchorY > 0.5f) {
            return sh - m.y - m.lastH;
        }
        return Ui.clamp(m.y, 2f, Math.max(2f, sh - 12f));
    }

    private static void drawCard(Module m, float w, float h) {
        if (m.bg) {
            int c = Ui.withA(m.bgColor, m.opacity);
            if (m.radius > 0.5f) Ui.rrect(0f, 0f, w, h, m.radius, c);
            else Ui.rect(0f, 0f, w, h, c);
        }
        if (m.border) {
            int bc = Ui.withA(m.borderColor, m.opacity);
            if (m.radius > 0.5f) Ui.rframe(0f, 0f, w, h, m.radius, 1f, bc);
            else Ui.frame(0f, 0f, w, h, 1f, bc);
        }
    }

    private static String chipText(MinecraftClient mc, Module m, boolean preview) {
        ClientPlayerEntity p = mc.player;
        String id = m.id;
        if ("fps".equals(id)) {
            return MinecraftClient.getCurrentFps() + " FPS";
        }
        if ("cps".equals(id)) {
            return m.bool("rmb", true) ? (lmbCps() + " | " + rmbCps() + " CPS") : (lmbCps() + " CPS");
        }
        if ("ping".equals(id)) {
            int ping = 0;
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            if (nh != null && p != null) {
                PlayerListEntry entry = nh.getPlayerListEntry(p.getUuid());
                if (entry != null) ping = entry.getLatency();
            }
            return (mc.isInSingleplayer() && ping <= 0) ? "0 ms" : (ping + " ms");
        }
        if ("sprint".equals(id)) {
            boolean active = p != null && p.isSprinting();
            return active ? "[Sprinting (Toggled)]" : "[Sprint (Toggled)]";
        }
        if ("coords".equals(id)) {
            if (p == null) return "0, 64, 0";
            return String.format(Locale.US, "XYZ %d %d %d (%s)",
                (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), cardinal(p.yaw));
        }
        if ("server".equals(id)) {
            ServerInfo si = mc.getCurrentServerEntry();
            return si != null && si.address != null ? si.address : "Singleplayer";
        }
        if ("memory".equals(id)) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
            long max = rt.maxMemory() / (1024L * 1024L);
            return "RAM " + used + "/" + max + " MB";
        }
        if ("speed".equals(id)) {
            return String.format(Locale.US, "%.2f m/s", currentSpeedBps);
        }
        if ("reach".equals(id)) {
            if (System.currentTimeMillis() - lastReachAt > 3000L && !preview) return "0.00 blocks";
            return (preview ? "2.84" : lastReachStr) + " blocks";
        }
        if ("combo".equals(id)) {
            int c = preview ? 4 : comboCount;
            return c + " Combo";
        }
        if ("bioma".equals(id)) {
            if (mc.world == null || p == null) return "Plains";
            return mc.world.getBiome(p.getBlockPos()).name;
        }
        if ("hora".equals(id)) {
            long ticks = mc.world != null ? (mc.world.getTimeOfDay() % 24000L) : 0L;
            int hours = (int) ((ticks / 1000L + 6L) % 24L);
            int mins = (int) ((ticks % 1000L) * 60L / 1000L);
            return String.format(Locale.US, "%02d:%02d", hours, mins);
        }
        if ("realtime".equals(id)) {
            return TIME_FMT.format(new Date());
        }
        if ("daycounter".equals(id)) {
            long days = mc.world != null ? (mc.world.getTimeOfDay() / 24000L) : 0L;
            return "Day " + days;
        }
        if ("sessao".equals(id)) {
            long sec = Math.max(0L, (System.currentTimeMillis() - sessionStartMs) / 1000L);
            long h = sec / 3600L;
            long min = (sec % 3600L) / 60L;
            long s = sec % 60L;
            return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, min, s)
                         : String.format(Locale.US, "%02d:%02d", min, s);
        }
        if ("health".equals(id)) {
            if (p == null) return "20.0 HP";
            return String.format(Locale.US, "%.1f HP", p.getHealth() + p.getAbsorption());
        }
        if ("hunger".equals(id)) {
            if (p == null) return "20 Food";
            return p.getHungerManager().getFoodLevel() + " Food";
        }
        if ("itens".equals(id)) {
            ItemStack st = p != null ? p.inventory.getMainHandStack() : null;
            if (st == null) return preview ? "Diamond Sword" : "Empty Hand";
            if (st.isDamageable()) {
                int rem = st.getMaxDamage() - st.getDamage();
                return st.getCustomName() + " (" + rem + ")";
            }
            return st.getCustomName() + " x" + st.count;
        }
        if ("pack".equals(id)) {
            List<ResourcePackLoader.Entry> sel = mc.getResourcePackLoader().getSelectedResourcePacks();
            if (sel != null && !sel.isEmpty()) {
                return sel.get(sel.size() - 1).getName();
            }
            return "Default Pack";
        }
        if ("armordur".equals(id)) {
            if (p == null) return "Armor 100%";
            int totMax = 0, totRem = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack st = p.inventory.armor[i];
                if (st != null && st.isDamageable()) {
                    totMax += st.getMaxDamage();
                    totRem += (st.getMaxDamage() - st.getDamage());
                }
            }
            int pct = totMax > 0 ? Math.round(100f * totRem / totMax) : (preview ? 100 : 0);
            return "Armor " + pct + "%";
        }
        if ("itemcount".equals(id)) {
            if (p == null) return "0 items";
            ItemStack held = p.inventory.getMainHandStack();
            if (held == null) return preview ? "64 items" : "0 items";
            int count = 0;
            for (ItemStack st : p.inventory.main) {
                if (st != null && st.getItem() == held.getItem() && st.getData() == held.getData()) {
                    count += st.count;
                }
            }
            return held.getCustomName() + ": " + count;
        }
        return m.name;
    }

    private static float[] renderArmor(MinecraftClient mc, Module m, boolean preview) {
        ClientPlayerEntity p = mc.player;
        boolean showPct = m.bool("percent", true);
        boolean showBar = m.bool("bar", true);
        boolean showPrefix = m.bool("prefix", true);
        boolean horizontal = m.bool("horizontal", false);
        boolean lowBlink = m.bool("low_blink", true);

        List<ItemStack> pieces = new ArrayList<ItemStack>(4);
        if (p != null) {
            for (int i = 3; i >= 0; i--) {
                ItemStack st = p.inventory.armor[i];
                if (st != null) pieces.add(st);
            }
        }
        if (pieces.isEmpty() && preview) {
            // Show subtle placeholder card in HUD editor
            float pw = 76f, ph = 22f;
            drawCard(m, pw, ph);
            Ui.textS("Armor HUD", 8f, 6f, Ui.withA(m.fgColor, m.opacity));
            return new float[] { pw, ph };
        }
        if (pieces.isEmpty()) {
            return new float[] { 40f, 16f };
        }

        if (horizontal) {
            float slotW = 24f;
            float ph = showBar ? 30f : 24f;
            float pw = pieces.size() * slotW + 6f;
            drawCard(m, pw, ph);
            for (int i = 0; i < pieces.size(); i++) {
                ItemStack st = pieces.get(i);
                float sx = 3f + i * slotW;
                renderGuiItem(mc, st, Math.round(sx + 4f), 3);
                if (st.isDamageable()) {
                    float pct = clamp01((st.getMaxDamage() - st.getDamage()) / (float) Math.max(1, st.getMaxDamage()));
                    int tier = armorTierColor(pct, lowBlink);
                    if (showBar) {
                        Ui.rect(sx + 3f, 22f, 18f, 2.5f, 0x88000000);
                        Ui.rect(sx + 3f, 22f, 18f * pct, 2.5f, Ui.withA(tier, m.opacity));
                    }
                }
            }
            return new float[] { pw, ph };
        }

        float rowH = 19f;
        float maxTextW = 0f;
        String[] labels = new String[pieces.size()];
        int[] colors = new int[pieces.size()];
        float[] pcts = new float[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            ItemStack st = pieces.get(i);
            if (st.isDamageable()) {
                int max = Math.max(1, st.getMaxDamage());
                int rem = Math.max(0, max - st.getDamage());
                pcts[i] = clamp01(rem / (float) max);
                colors[i] = armorTierColor(pcts[i], lowBlink);
                String num = showPct ? (Math.round(pcts[i] * 100f) + "%") : String.valueOf(rem);
                labels[i] = showPrefix ? (armorSlotPrefix(st) + " " + num) : num;
            } else {
                pcts[i] = 1f;
                colors[i] = 0xFF06D6A0;
                labels[i] = st.count > 1 ? ("x" + st.count) : "";
            }
            maxTextW = Math.max(maxTextW, Ui.tw(labels[i]));
        }

        float pw = 24f + maxTextW + 8f;
        float ph = pieces.size() * rowH + 4f;
        drawCard(m, pw, ph);

        for (int i = 0; i < pieces.size(); i++) {
            ItemStack st = pieces.get(i);
            float ry = 3f + i * rowH;
            renderGuiItem(mc, st, 4, Math.round(ry));
            if (!labels[i].isEmpty()) {
                Ui.textS(labels[i], 24f, ry + 3f, Ui.withA(colors[i], m.opacity));
            }
            if (showBar && st.isDamageable()) {
                float bw = Math.max(22f, pw - 28f);
                Ui.rect(24f, ry + 13f, bw, 2f, 0x80000000);
                Ui.rect(24f, ry + 13f, bw * pcts[i], 2f, Ui.withA(colors[i], m.opacity));
            }
        }
        return new float[] { pw, ph };
    }

    private static String armorSlotPrefix(ItemStack st) {
        if (st.getItem() instanceof ArmorItem) {
            int slot = ((ArmorItem) st.getItem()).slot;
            if (slot == 0) return "H";
            if (slot == 1) return "C";
            if (slot == 2) return "L";
            if (slot == 3) return "B";
        }
        return "";
    }

    private static int armorTierColor(float pct, boolean lowBlink) {
        if (lowBlink && pct <= 0.15f && ((System.currentTimeMillis() / 350L) % 2L == 0L)) {
            return 0xFFFF3B30;
        }
        if (pct > 0.80f) return 0xFF06D6A0;
        if (pct > 0.60f) return 0xFF6BCB77;
        if (pct > 0.40f) return 0xFFFFD166;
        if (pct > 0.20f) return 0xFFFF9F1C;
        return 0xFFE63946;
    }

    private static void renderGuiItem(MinecraftClient mc, ItemStack stack, int x, int y) {
        if (stack == null) return;
        GlStateManager.pushMatrix();
        DiffuseLighting.enableNormally();
        GlStateManager.enableDepthTest();
        mc.getItemRenderer().renderInGuiWithOverrides(stack, x, y);
        DiffuseLighting.disable();
        GlStateManager.disableDepthTest();
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static float[] renderEffects(MinecraftClient mc, Module m, boolean preview) {
        ClientPlayerEntity p = mc.player;
        List<StatusEffectInstance> list = new ArrayList<StatusEffectInstance>();
        if (p != null) {
            Collection<StatusEffectInstance> col = p.getStatusEffectInstances();
            if (col != null) list.addAll(col);
        }
        if (list.isEmpty()) {
            if (!preview) return new float[] { 70f, 18f };
            float pw = 104f, ph = 24f;
            drawCard(m, pw, ph);
            Ui.disc(11f, 12f, 4f, Ui.accent());
            Ui.textS("Speed II  01:45", 20f, 8f, Ui.withA(m.fgColor, m.opacity));
            return new float[] { pw, ph };
        }

        float rowH = 16f;
        float maxW = 60f;
        String[] lines = new String[list.size()];
        int[] cols = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            StatusEffectInstance inst = list.get(i);
            int id = inst.getEffectId();
            StatusEffect eff = (id >= 0 && id < StatusEffect.STATUS_EFFECTS.length) ? StatusEffect.STATUS_EFFECTS[id] : null;
            String name = eff != null ? I18n.translate(eff.getTranslationKey()) : I18n.translate(inst.getTranslationKey());
            String amp = romanAmp(inst.getAmplifier());
            String dur = formatDuration(inst.getDuration());
            lines[i] = name + amp + "  " + dur;
            cols[i] = eff != null ? (eff.getColor() | 0xFF000000) : Ui.accent();
            maxW = Math.max(maxW, 18f + Ui.tw(lines[i]) + 8f);
        }

        float ph = list.size() * rowH + 6f;
        drawCard(m, maxW, ph);
        for (int i = 0; i < list.size(); i++) {
            StatusEffectInstance inst = list.get(i);
            float ry = 4f + i * rowH;
            boolean blink = inst.getDuration() < 200 && ((System.currentTimeMillis() / 300L) % 2L == 0L);
            Ui.disc(9f, ry + 6f, 3.2f, Ui.withA(cols[i], m.opacity));
            int tc = blink ? 0xFFE63946 : m.fgColor;
            Ui.textS(lines[i], 17f, ry + 2f, Ui.withA(tc, m.opacity));
        }
        return new float[] { maxW, ph };
    }

    private static float[] renderKeystrokes(MinecraftClient mc, Module m) {
        GameOptions o = mc.options;
        boolean w = GameOptions.isPressed(o.forwardKey);
        boolean a = GameOptions.isPressed(o.leftKey);
        boolean s = GameOptions.isPressed(o.backKey);
        boolean d = GameOptions.isPressed(o.rightKey);
        boolean sp = GameOptions.isPressed(o.jumpKey);
        boolean lmb = Mouse.isButtonDown(0) && mc.currentScreen == null;
        boolean rmb = Mouse.isButtonDown(1) && mc.currentScreen == null;

        float k = 22f;
        float gap = 2f;
        float totalW = k * 3f + gap * 2f;
        drawKey(m, k + gap, 0f, k, k, "W", w);
        drawKey(m, 0f, k + gap, k, k, "A", a);
        drawKey(m, k + gap, k + gap, k, k, "S", s);
        drawKey(m, (k + gap) * 2f, k + gap, k, k, "D", d);

        float curY = (k + gap) * 2f;
        if (m.bool("mouse", true)) {
            float mw = (totalW - gap) * 0.5f;
            String lt = m.bool("cps", true) ? ("LMB\n" + lmbCps()) : "LMB";
            String rt = m.bool("cps", true) ? ("RMB\n" + rmbCps()) : "RMB";
            drawKeyTwoLine(m, 0f, curY, mw, 22f, lt, lmb);
            drawKeyTwoLine(m, mw + gap, curY, mw, 22f, rt, rmb);
            curY += 22f + gap;
        }
        if (m.bool("space", true)) {
            drawKey(m, 0f, curY, totalW, 14f, "-----", sp);
            curY += 14f;
        }
        return new float[] { totalW, curY };
    }

    private static void drawKey(Module m, float x, float y, float w, float h, String label, boolean active) {
        int bg = active ? Ui.withA(Ui.accent(), 0.85f * m.opacity) : Ui.withA(m.bgColor, m.opacity);
        int fg = active ? 0xFF0A0D12 : Ui.withA(m.fgColor, m.opacity);
        Ui.rrect(x, y, w, h, Math.min(4f, m.radius), bg);
        if (m.border) Ui.rframe(x, y, w, h, Math.min(4f, m.radius), 1f, Ui.withA(m.borderColor, m.opacity));
        Ui.ctext(label, x + w * 0.5f, y + (h - Ui.th()) * 0.5f, fg, !active);
    }

    private static void drawKeyTwoLine(Module m, float x, float y, float w, float h, String txt, boolean active) {
        int bg = active ? Ui.withA(Ui.accent(), 0.85f * m.opacity) : Ui.withA(m.bgColor, m.opacity);
        int fg = active ? 0xFF0A0D12 : Ui.withA(m.fgColor, m.opacity);
        Ui.rrect(x, y, w, h, Math.min(4f, m.radius), bg);
        String[] parts = txt.split("\n");
        if (parts.length == 2) {
            Ui.ctextScaled(parts[0], x + w * 0.5f, y + 3f, 0.8f, fg, !active);
            Ui.ctextScaled(parts[1], x + w * 0.5f, y + 12f, 0.75f, fg, !active);
        } else {
            Ui.ctext(txt, x + w * 0.5f, y + (h - Ui.th()) * 0.5f, fg, !active);
        }
    }

    private static float[] renderCompass(MinecraftClient mc, Module m) {
        float w = 160f, h = 22f;
        drawCard(m, w, h);
        float yaw = mc.player != null ? (((mc.player.yaw % 360f) + 360f) % 360f) : 0f;
        String[] dirs = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
        float pxPerDeg = 1.15f;
        float cx = w * 0.5f;

        for (int i = 0; i < 8; i++) {
            float deg = i * 45f;
            float diff = ((deg - yaw + 540f) % 360f) - 180f;
            float tx = cx + diff * pxPerDeg;
            if (tx >= 8f && tx <= w - 8f) {
                int col = dirs[i].length() == 1 ? m.fgColor : Ui.fgMuted();
                Ui.ctextScaled(dirs[i], tx, 6f, 0.9f, Ui.withA(col, m.opacity), true);
            }
        }
        Ui.rect(cx - 0.5f, 1f, 1f, 4f, Ui.accent());
        return new float[] { w, h };
    }

    private static float[] renderWatermark(Module m) {
        float w = 96f, h = 22f;
        drawCard(m, w, h);
        float ang = (System.currentTimeMillis() % 4000L) / 4000f * 360f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(13f, 11f, 0f);
        GlStateManager.rotate(ang, 0f, 0f, 1f);
        Skin.drawTex(Skin.logoIris(32, Ui.accent()), -7f, -7f, 14f, 14f, Ui.setA(0xFFFFFF, Math.round(255 * m.opacity)));
        GlStateManager.popMatrix();
        Ui.textS("ASTUCY", 26f, 7f, Ui.withA(m.fgColor, m.opacity));
        return new float[] { w, h };
    }

    private static float[] renderTargetHud(MinecraftClient mc, Module m, boolean preview) {
        LivingEntity t = lastTarget;
        if (t == null && !preview) return new float[] { 120f, 34f };
        String name = t != null ? t.getName().asUnformattedString() : "AstucyTarget";
        if (name.length() > 16) name = name.substring(0, 16);
        float hp = t != null ? t.getHealth() : 15.5f;
        float max = t != null ? Math.max(1f, t.getMaxHealth()) : 20f;
        float ratio = clamp01(hp / max);

        float w = 132f, h = 34f;
        drawCard(m, w, h);
        Ui.textS(name, 8f, 6f, Ui.withA(m.fgColor, m.opacity));
        String hpTxt = String.format(Locale.US, "%.1f", hp);
        Ui.textS(hpTxt, w - 8f - Ui.tw(hpTxt), 6f, Ui.withA(0xFF06D6A0, m.opacity));
        Ui.rrect(8f, 20f, w - 16f, 6f, 3f, 0x80000000);
        int barCol = armorTierColor(ratio, false);
        Ui.rrect(8f, 20f, Math.max(6f, (w - 16f) * ratio), 6f, 3f, Ui.withA(barCol, m.opacity));
        return new float[] { w, h };
    }

    private static float[] renderFpsGraph(Module m) {
        float w = 110f, h = 36f;
        drawCard(m, w, h);
        int max = 60;
        for (int v : fpsHistory) if (v > max) max = v;
        float gx = 4f, gy = 13f, gw = w - 8f, gh = h - 17f;
        float step = gw / fpsHistory.length;
        for (int i = 0; i < fpsHistory.length; i++) {
            int idx = (fpsCursor + i) % fpsHistory.length;
            float norm = clamp01(fpsHistory[idx] / (float) max);
            float bh = Math.max(1f, gh * norm);
            Ui.rect(gx + i * step, gy + gh - bh, Math.max(1f, step - 0.3f), bh, Ui.withA(Ui.accent(), m.opacity * 0.85f));
        }
        Ui.scaledText(MinecraftClient.getCurrentFps() + " FPS", 5f, 3f, 0.8f, Ui.withA(m.fgColor, m.opacity), true);
        return new float[] { w, h };
    }

    public static void renderCustomCrosshair(int sw, int sh) {
        AstucyConfig cfg = AstucyConfig.get();
        long[] grid = cfg.crosshair_grid;
        if (grid == null || grid.length != 17) return;
        int cx = sw / 2;
        int cy = sh / 2;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        if (cfg.crosshair_invert) {
            GlStateManager.blendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        } else {
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        int col = cfg.crosshair_color;
        for (int r = 0; r < 17; r++) {
            long row = grid[r];
            if (row == 0L) continue;
            for (int c = 0; c < 17; c++) {
                if ((row & (1L << c)) != 0L) {
                    int px = cx - 8 + c;
                    int py = cy - 8 + r;
                    Ui.rect(px, py, 1f, 1f, col);
                }
            }
        }
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.popMatrix();
    }

    private static String cardinal(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        if (y < 22.5f || y >= 337.5f) return "S";
        if (y < 67.5f) return "SW";
        if (y < 112.5f) return "W";
        if (y < 157.5f) return "NW";
        if (y < 202.5f) return "N";
        if (y < 247.5f) return "NE";
        if (y < 292.5f) return "E";
        return "SE";
    }

    private static String romanAmp(int amp) {
        if (amp <= 0) return "";
        if (amp == 1) return " II";
        if (amp == 2) return " III";
        if (amp == 3) return " IV";
        return " " + (amp + 1);
    }

    private static String formatDuration(int ticks) {
        int sec = Math.max(0, ticks / 20);
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
