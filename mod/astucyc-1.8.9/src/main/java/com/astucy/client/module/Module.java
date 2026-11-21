package com.astucy.client.module;

import java.util.LinkedHashMap;
import java.util.Map;

public class Module {
    public static final int[] SWATCHES = new int[] {
        0xFFFFFFFF, 0xFF06D6A0, 0xFF3A86FF, 0xFF9B5DE5,
        0xFFF15BB5, 0xFFE63946, 0xFFFF9F1C, 0xFFFFD166
    };

    public final String id;
    public final String name;
    public final String desc;
    public final ModuleCategory category;

    public boolean enabled;
    public boolean hud = true;
    public boolean isNew = false;

    public float x = 10f;
    public float y = 10f;
    public float anchorX = 0f;
    public float anchorY = 0f;
    public float scale = 1f;
    public float opacity = 1f;
    public boolean bg = true;
    public int bgColor = 0xAA0C0F14;
    public int fgColor = 0xFFFFFFFF;
    public boolean border = false;
    public int borderColor = 0x33FFFFFF;
    public float radius = 4f;
    public String font = "ui";

    public final Map<String, Object> props = new LinkedHashMap<String, Object>();

    // Last rendered bounds (logical screen pixels) for HUD editor hit testing
    public transient float lastX = 10f;
    public transient float lastY = 10f;
    public transient float lastW = 48f;
    public transient float lastH = 16f;

    public Module(String id, String name, String desc, ModuleCategory category, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.enabled = defaultEnabled;
    }

    public Module nonHud() {
        this.hud = false;
        return this;
    }

    public Module isNew() {
        this.isNew = true;
        return this;
    }

    public Module pos(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Module prop(String key, Object value) {
        this.props.put(key, value);
        return this;
    }

    public void resetStyle() {
        this.scale = 1f;
        this.opacity = 1f;
        this.bg = true;
        this.bgColor = 0xAA0C0F14;
        this.fgColor = 0xFFFFFFFF;
        this.border = false;
        this.borderColor = 0x33FFFFFF;
        this.radius = 4f;
        this.font = "ui";
    }

    public boolean bool(String key, boolean def) {
        Object v = props.get(key);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return def;
    }

    public float num(String key, float def) {
        Object v = props.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        if (v instanceof String) {
            try { return Float.parseFloat((String) v); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public int integer(String key, int def) {
        Object v = props.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public String str(String key, String def) {
        Object v = props.get(key);
        return v != null ? String.valueOf(v) : def;
    }
}
