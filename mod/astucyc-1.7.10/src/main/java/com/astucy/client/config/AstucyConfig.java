package com.astucy.client.config;

import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AstucyConfig {
    private static final int SCHEMA_VERSION = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("astucyc.json");

    private static final Set<String> DEFAULT_PRESET_IDS = new LinkedHashSet<String>(
        Arrays.asList("sprint", "fps", "ping", "cps", "cit")
    );

    public int schema_version = SCHEMA_VERSION;
    public int menu_accent = 0xFF06D6A0;
    public boolean light_mode = false;
    public boolean menu_particles = true;
    public boolean hud_snapping = true;
    public boolean show_watermark = true;
    public boolean compact_cards = false;

    // 17x17 crosshair bitmask (rows 0..16, bits 0..16)
    public long[] crosshair_grid = defaultCrossGrid();
    public int crosshair_color = 0xFFFFFFFF;
    public boolean crosshair_invert = false;

    public List<String> profile_names = new ArrayList<String>(Arrays.asList("Profile 1", "Profile 2", "Profile 3"));
    public int active_profile = 0;

    public Map<String, ModuleData> modules = new LinkedHashMap<String, ModuleData>();
    public List<Map<String, ModuleData>> profile_snapshots = new ArrayList<Map<String, ModuleData>>();

    private static AstucyConfig instance;
    private static boolean dirty;
    private static long lastDirtyAt;

    public static class ModuleData {
        public boolean enabled;
        public float x;
        public float y;
        public float anchorX;
        public float anchorY;
        public float scale = 1f;
        public float opacity = 1f;
        public boolean bg = true;
        public int bgColor = 0xAA0C0F14;
        public int fgColor = 0xFFFFFFFF;
        public boolean border = false;
        public int borderColor = 0x33FFFFFF;
        public float radius = 4f;
        public String font = "ui";
        public Map<String, Object> props = new LinkedHashMap<String, Object>();
    }

    public static AstucyConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static long[] defaultCrossGrid() {
        long[] g = new long[17];
        for (int r = 4; r <= 6; r++) g[r] |= (1L << 8);
        for (int r = 10; r <= 12; r++) g[r] |= (1L << 8);
        for (int c = 4; c <= 6; c++) g[8] |= (1L << c);
        for (int c = 10; c <= 12; c++) g[8] |= (1L << c);
        return g;
    }

    public static void applyCrossPreset(String preset) {
        AstucyConfig cfg = get();
        long[] g = new long[17];
        if ("Cross".equalsIgnoreCase(preset)) {
            g = defaultCrossGrid();
        } else if ("Dot".equalsIgnoreCase(preset)) {
            g[7] |= (1L << 8);
            g[8] |= (1L << 7) | (1L << 8) | (1L << 9);
            g[9] |= (1L << 8);
        } else if ("Gap".equalsIgnoreCase(preset)) {
            for (int r = 2; r <= 5; r++) g[r] |= (1L << 8);
            for (int r = 11; r <= 14; r++) g[r] |= (1L << 8);
            for (int c = 2; c <= 5; c++) g[8] |= (1L << c);
            for (int c = 11; c <= 14; c++) g[8] |= (1L << c);
        } else if ("X".equalsIgnoreCase(preset)) {
            for (int d = 2; d <= 4; d++) {
                g[8 - d] |= (1L << (8 - d)) | (1L << (8 + d));
                g[8 + d] |= (1L << (8 - d)) | (1L << (8 + d));
            }
            g[8] |= (1L << 8);
        }
        cfg.crosshair_grid = g;
        markDirty();
    }

    public static void load() {
        AstucyConfig loaded = null;
        File f = CONFIG_PATH.toFile();
        if (f.isFile()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
                loaded = GSON.fromJson(reader, AstucyConfig.class);
            } catch (Throwable ignored) {
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Throwable ignored) {}
                }
            }
        }
        if (loaded == null) {
            loaded = new AstucyConfig();
        }
        if (loaded.crosshair_grid == null || loaded.crosshair_grid.length != 17) {
            loaded.crosshair_grid = defaultCrossGrid();
        }
        if (loaded.profile_names == null || loaded.profile_names.size() < 3) {
            loaded.profile_names = new ArrayList<String>(Arrays.asList("Profile 1", "Profile 2", "Profile 3"));
        }
        if (loaded.modules == null) {
            loaded.modules = new LinkedHashMap<String, ModuleData>();
        }
        if (loaded.profile_snapshots == null) {
            loaded.profile_snapshots = new ArrayList<Map<String, ModuleData>>();
        }
        instance = loaded;
        migrateIfNeeded();
    }

    private static void migrateIfNeeded() {
        if (instance.schema_version >= SCHEMA_VERSION) return;
        if (instance.schema_version < 8) {
            for (Map.Entry<String, ModuleData> e : instance.modules.entrySet()) {
                if (e.getValue() != null) {
                    e.getValue().enabled = DEFAULT_PRESET_IDS.contains(e.getKey());
                }
            }
            ModuleData armor = instance.modules.get("armor");
            if (armor != null) {
                if (armor.props == null) armor.props = new LinkedHashMap<String, Object>();
                if (!armor.props.containsKey("prefix")) {
                    armor.props.put("prefix", Boolean.TRUE);
                }
            }
        }
        instance.schema_version = SCHEMA_VERSION;
        markDirty();
    }

    public static void applyToModules() {
        AstucyConfig cfg = get();
        for (Module m : Modules.ALL) {
            ModuleData d = cfg.modules.get(m.id);
            if (d == null) {
                cfg.modules.put(m.id, snapshotModule(m));
                continue;
            }
            m.enabled = d.enabled;
            m.x = d.x;
            m.y = d.y;
            m.anchorX = d.anchorX;
            m.anchorY = d.anchorY;
            m.scale = d.scale > 0f ? d.scale : 1f;
            m.opacity = clamp(d.opacity, 0.1f, 1f);
            m.bg = d.bg;
            m.bgColor = d.bgColor;
            m.fgColor = d.fgColor;
            m.border = d.border;
            m.borderColor = d.borderColor;
            m.radius = d.radius;
            m.font = d.font != null ? d.font : "ui";
            if (d.props != null) {
                for (Map.Entry<String, Object> p : d.props.entrySet()) {
                    m.props.put(p.getKey(), p.getValue());
                }
            }
        }
    }

    public static void captureFromModules() {
        AstucyConfig cfg = get();
        for (Module m : Modules.ALL) {
            cfg.modules.put(m.id, snapshotModule(m));
        }
    }

    private static ModuleData snapshotModule(Module m) {
        ModuleData d = new ModuleData();
        d.enabled = m.enabled;
        d.x = m.x;
        d.y = m.y;
        d.anchorX = m.anchorX;
        d.anchorY = m.anchorY;
        d.scale = m.scale;
        d.opacity = m.opacity;
        d.bg = m.bg;
        d.bgColor = m.bgColor;
        d.fgColor = m.fgColor;
        d.border = m.border;
        d.borderColor = m.borderColor;
        d.radius = m.radius;
        d.font = m.font;
        d.props = new LinkedHashMap<String, Object>(m.props);
        return d;
    }

    public static void saveCurrentProfileSnapshot() {
        AstucyConfig cfg = get();
        captureFromModules();
        while (cfg.profile_snapshots.size() <= cfg.active_profile) {
            cfg.profile_snapshots.add(new LinkedHashMap<String, ModuleData>());
        }
        Map<String, ModuleData> copy = new LinkedHashMap<String, ModuleData>();
        for (Module m : Modules.ALL) {
            copy.put(m.id, snapshotModule(m));
        }
        cfg.profile_snapshots.set(cfg.active_profile, copy);
        markDirty();
    }

    public static void switchProfile(int index) {
        AstucyConfig cfg = get();
        if (index < 0 || index >= cfg.profile_names.size()) return;
        saveCurrentProfileSnapshot();
        cfg.active_profile = index;
        if (index < cfg.profile_snapshots.size()) {
            Map<String, ModuleData> snap = cfg.profile_snapshots.get(index);
            if (snap != null && !snap.isEmpty()) {
                cfg.modules.putAll(snap);
                applyToModules();
            }
        }
        markDirty();
    }

    public static void markDirty() {
        dirty = true;
        lastDirtyAt = System.currentTimeMillis();
    }

    public static void tickFlush() {
        if (dirty && System.currentTimeMillis() - lastDirtyAt >= 400L) {
            flushNow();
        }
    }

    public static void flushNow() {
        if (instance == null) return;
        captureFromModules();
        dirty = false;
        BufferedWriter writer = null;
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = CONFIG_PATH.resolveSibling("astucyc.json.tmp");
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp.toFile()), StandardCharsets.UTF_8));
            GSON.toJson(instance, writer);
            writer.flush();
            writer.close();
            writer = null;
            try {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable ignored) {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
