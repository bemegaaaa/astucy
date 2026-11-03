package com.astucy.client.compat;

import com.astucy.client.module.Module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Ponte opcional para o mod Natural Motion Blur.
 *
 * O blur continua sendo o mod (LGPL, shader de verdade) — aqui só ligamos a
 * chave dele pelo nosso menu, sem reiniciar: o ConfigManager do mod guarda um
 * objeto estático com campos públicos, então basta refletir e salvar.
 */
public final class BlurBridge {

    private static boolean init;
    private static boolean available;
    private static Object config;
    private static Field fEnabled;
    private static Field fStrength;
    private static Method save;

    private BlurBridge() {}

    public static boolean available() {
        init();
        return available;
    }

    private static void init() {
        if (init) {
            return;
        }
        init = true;
        try {
            Class<?> cm = Class.forName("net.natural.motionblur.config.ConfigManager");
            Object cfg = cm.getMethod("getConfig").invoke(null);
            if (cfg == null) {
                return;
            }
            Class<?> ce = cfg.getClass();
            fEnabled = ce.getField("enabled");
            fStrength = ce.getField("motionBlurStrength");
            save = cm.getMethod("saveConfig");
            config = cfg;
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    /**
     * Primeira sincronização: adota o estado que o mod já tinha (o usuário
     * não perde o blur que estava ligado antes do módulo existir).
     */
    private static void seed(Module m) {
        try {
            m.enabled = fEnabled.getBoolean(config);
            m.props.put("strength", (double) fStrength.getFloat(config));
            m.props.put("seeded", Boolean.TRUE);
        } catch (Throwable ignored) {
            m.props.put("seeded", Boolean.TRUE);
        }
    }

    /** Empurra o estado do módulo para o mod (vale na hora, sem reiniciar). */
    public static void sync(Module m) {
        init();
        if (!available || m == null) {
            return;
        }
        if (!m.props.containsKey("seeded")) {
            seed(m);
            return;
        }
        try {
            boolean on = m.enabled;
            double strength = m.numProp("strength", 1.0);
            boolean changed = false;
            if (fEnabled.getBoolean(config) != on) {
                fEnabled.setBoolean(config, on);
                changed = true;
            }
            if (Math.abs(fStrength.getFloat(config) - (float) strength) > 0.001f) {
                fStrength.setFloat(config, (float) strength);
                changed = true;
            }
            if (changed && save != null) {
                save.invoke(null);
            }
        } catch (Throwable ignored) {
            // mod atualizou e mudou a API: o módulo só para de controlar
        }
    }
}
