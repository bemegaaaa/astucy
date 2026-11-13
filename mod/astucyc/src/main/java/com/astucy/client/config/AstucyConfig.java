package com.astucy.client.config;

import com.astucy.client.module.Module;
import com.astucy.client.module.Modules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistência de astucyc.json: módulos ligados/desligados + customização
 * completa (âncoras, escala, cores, fundo, sombra, prefixo).
 */
public final class AstucyConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, Map<String, Object>> data = new HashMap<>();

    private AstucyConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("astucyc.json");
    }

    public static void load() {
        // o registry precisa existir ANTES de aplicar: sem isso nada era
        // restaurado no boot e o primeiro save sobrescrevia tudo com defaults.
        Modules.registerAll();
        try {
            if (Files.exists(file())) {
                Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
                Map<String, Map<String, Object>> read = GSON.fromJson(Files.readString(file()), type);
                if (read != null) {
                    data = read;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // config corrompida: recomeça do zero, nunca trava o jogo
        }
        migrate();
        for (Module m : Modules.all()) {
            Map<String, Object> d = data.get(m.id);
            if (d == null) {
                continue;
            }
            applyStyle(m, d);
        }
    }

    /**
     * v2: fundo transparente + sem sombra por padrão. Estilos antigos são
     * descartados (mantém só on/off, posição e escala).
     * v3: HUD com fundo quase preto (0xE0000000) e fonte vanilla.
     * v4: fundo do texto vira preto MEIO transparente (0xB3000000).
     * v5: preto 50% (0x80000000) + CPS sem prefixo, estilo "0 | 0".
     * v6: sem fio de borda e preto 60% (0x99000000).
     */
    private static void migrate() {
        Map<String, Object> ver = data.get("_versao");
        int v = ver != null && ver.get("v") instanceof Number n ? n.intValue() : 1;
        if (v < 4) {
            if (v < 2) {
                for (Module m : Modules.all()) {
                    Map<String, Object> d = data.get(m.id);
                    if (d != null) {
                        d.remove("bgColor");
                        d.remove("shadow");
                    }
                }
            }
            if (v < 3) {
                for (Module m : Modules.all()) {
                    Map<String, Object> d = data.get(m.id);
                    if (d != null) {
                        d.put("bgColor", 0xE0000000);
                        d.put("bg", true);
                        d.put("shadow", false);
                    }
                }
                // bússola virou boss bar no topo central
                Map<String, Object> compass = data.get("direcao");
                if (compass != null) {
                    compass.put("ax", 0.5);
                    compass.put("ay", 0.02);
                }
            }
            // v4: quem estava no preto cheio (ou quase) ganha o preto translúcido
            for (Module m : Modules.all()) {
                Map<String, Object> d = data.get(m.id);
                if (d == null) {
                    continue;
                }
                Object bgc = d.get("bgColor");
                if (bgc instanceof Number num) {
                    int argb = num.intValue();
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha >= 0xD0 && (argb & 0xFFFFFF) == 0) {
                        d.put("bgColor", (0xB3 << 24));
                    }
                }
            }
        }
        if (v < 5) {
            for (Module m : Modules.all()) {
                Map<String, Object> d = data.get(m.id);
                if (d == null) {
                    continue;
                }
                Object bgc = d.get("bgColor");
                if (bgc instanceof Number num) {
                    int argb = num.intValue();
                    if ((argb & 0xFFFFFF) == 0) {
                        d.put("bgColor", (0x80 << 24));
                    }
                }
            }
            Map<String, Object> cps = data.get("cps");
            if (cps != null) {
                cps.put("prefix", false);
            }
        }
        if (v < 6) {
            // v6: HUD sem fio de borda e um tom mais escuro (0x99000000)
            for (Module m : Modules.all()) {
                Map<String, Object> d = data.get(m.id);
                if (d == null) {
                    continue;
                }
                Object bgc = d.get("bgColor");
                if (bgc instanceof Number num && (num.intValue() & 0xFFFFFF) == 0) {
                    d.put("bgColor", (0x99 << 24));
                }
            }
            Map<String, Object> stamp = new HashMap<>();
            stamp.put("v", 6);
            data.put("_versao", stamp);
            flush();
        }
        if (v < 7) {
            // v7: migra módulos antigos para fonte anti-aliased ("font": true) e ativa Motion Blur de alta velocidade
            for (var entry : data.entrySet()) {
                Map<String, Object> val = entry.getValue();
                if (val == null) {
                    continue;
                }
                if (entry.getKey().startsWith("_perfil_")) {
                    for (Object sub : val.values()) {
                        if (sub instanceof Map<?, ?> subMap) {
                            upgradeModuleMap((Map<String, Object>) subMap, null);
                        }
                    }
                } else if (!entry.getKey().startsWith("_")) {
                    upgradeModuleMap(val, entry.getKey());
                }
            }
            Map<String, Object> stamp = new HashMap<>();
            stamp.put("v", 7);
            data.put("_versao", stamp);
            flush();
        }
    }

    @SuppressWarnings("unchecked")
    private static void upgradeModuleMap(Map<String, Object> d, String id) {
        Object rawProps = d.get("props");
        Map<String, Object> pm;
        if (rawProps instanceof Map<?, ?> existing) {
            pm = (Map<String, Object>) existing;
        } else {
            pm = new HashMap<>();
            d.put("props", pm);
        }
        pm.put("font", Boolean.TRUE);
        if ("blur".equals(id)) {
            d.put("enabled", Boolean.TRUE);
            Object st = pm.get("strength");
            if (!(st instanceof Number n) || n.doubleValue() <= 0.35) {
                pm.put("strength", 1.25);
            }
            pm.put("speed_boost", Boolean.TRUE);
            pm.put("hybrid", Boolean.TRUE);
            pm.put("weighted", Boolean.TRUE);
        }
    }

    /** Preferências do client (não são por módulo): animações, sons, etc. */
    public static Map<String, Object> prefs() {
        return data.computeIfAbsent("_client", k -> new HashMap<>());
    }

    public static boolean prefBool(String key, boolean def) {
        Object v = prefs().get(key);
        return v instanceof Boolean b ? b : def;
    }

    public static void setPref(String key, Object value) {
        prefs().put(key, value);
        flush();
    }

    public static void save() {
        for (Module m : Modules.all()) {
            data.put(m.id, styleMap(m));
        }
        flush();
    }

    /** Salva todos os módulos sob um perfil nomeado (sidebar do menu). */
        /** Nome exibido do perfil (indice 0..); padrao Profile 1/2/3. */
    public static String profileName(int idx) {
        Object v = prefs().get("_profile_name_" + idx);
        return v instanceof String s && !s.isBlank() ? s : ("Profile " + (idx + 1));
    }

    /** Renomeia o perfil e move os dados ja salvos se existirem. */
    public static void renameProfile(int idx, String newName) {
        String clean = newName == null ? "" : newName.trim();
        if (clean.isEmpty()) {
            return;
        }
        String old = profileName(idx);
        Map<String, Object> prof = data.remove("_perfil_" + old);
        if (prof != null) {
            data.put("_perfil_" + clean, prof);
        }
        prefs().put("_profile_name_" + idx, clean);
        flush();
    }

    public static void saveProfile(String name) {
        Map<String, Object> prof = new HashMap<>();
        for (Module m : Modules.all()) {
            prof.put(m.id, styleMap(m));
        }
        data.put("_perfil_" + name, prof);
        flush();
    }

    /** Aplica um perfil nomeado; silencioso se não existir. */
    public static void applyProfile(String name) {
        Object raw = data.get("_perfil_" + name);
        if (!(raw instanceof Map<?, ?> prof)) {
            return;
        }
        for (Module m : Modules.all()) {
            Object d = prof.get(m.id);
            if (d instanceof Map<?, ?> dm) {
                applyStyle(m, dm);
            }
        }
    }

    private static void flush() {
        try {
            Path target = file();
            Path dir = target.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            // grava em .tmp e move: queda de energia no meio do save nao
            // deixa o json pela metade (o proximo boot perdia tudo)
            Path tmp = target.resolveSibling("astucyc.json.tmp");
            Files.writeString(tmp, GSON.toJson(data));
            try {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // sem permissão de escrita: segue sem persistir
        }
    }

    /** Estado completo de um módulo (para perfil e para save simples). */
    private static Map<String, Object> styleMap(Module m) {
        Map<String, Object> d = new HashMap<>();
        d.put("enabled", m.enabled);
        d.put("ax", m.anchorX);
        d.put("ay", m.anchorY);
        d.put("scale", m.scale);
        d.put("scaleY", m.scaleY);
        d.put("bg", m.bg);
        d.put("bgc", (long) m.bgColor & 0xFFFFFFFFL);
        d.put("fgc", (long) m.fgColor & 0xFFFFFFFFL);
        d.put("shadow", m.shadow);
        d.put("prefix", m.prefix);
        if (m.customLabel != null && !m.customLabel.isEmpty()) {
            d.put("label", m.customLabel);
        }
        if (!m.props.isEmpty()) {
            d.put("props", new HashMap<>(m.props));
        }
        return d;
    }

    private static void applyStyle(Module m, Map<?, ?> d) {
        if (d.get("enabled") instanceof Boolean b) m.enabled = b;
        if (d.get("ax") instanceof Number n) m.anchorX = clamp01(n.doubleValue());
        if (d.get("ay") instanceof Number n) m.anchorY = clamp01(n.doubleValue());
        if (d.get("scale") instanceof Number n) m.scale = Math.max(0.6, Math.min(2.0, n.doubleValue()));
        if (d.get("scaleY") instanceof Number n) m.scaleY = Math.max(0.6, Math.min(3.0, n.doubleValue()));
        if (d.get("bg") instanceof Boolean b) m.bg = b;
        if (d.get("bgc") instanceof Number n) m.bgColor = (int) (n.longValue() & 0xFFFFFFFFL);
        if (d.get("fgc") instanceof Number n) m.fgColor = (int) (n.longValue() & 0xFFFFFFFFL);
        if (d.get("shadow") instanceof Boolean b) m.shadow = b;
        if (d.get("prefix") instanceof Boolean b) m.prefix = b;
        if (d.get("label") instanceof String s) m.customLabel = s;
        if (d.get("props") instanceof Map<?, ?> pm) {
            for (var e : pm.entrySet()) {
                if (e.getKey() instanceof String k && e.getValue() != null) {
                    m.props.put(k, e.getValue());
                }
            }
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
