package com.astucy.client.compat;

import com.astucy.client.module.Module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Ponte para o mod Natural Motion Blur com suporte a perfil ponderado (WEIGHTED),
 * algoritmo híbrido/velocidade e amplificação dinâmica em altas velocidades.
 */
public final class BlurBridge {

    private static boolean init;
    private static boolean available;
    private static Object config;
    private static Field fEnabled;
    private static Field fStrength;
    private static Field fRefreshScaling;
    private static Field fAlgorithm;
    private static Field fProfile;
    private static Object algVelocity;
    private static Object algHybrid;
    private static Object profWeighted;
    private static Object profUniform;
    private static Method save;

    private static boolean lastSavedOn = false;
    private static float lastSavedBaseStrength = -1f;
    private static boolean lastSavedHybrid = false;
    private static boolean lastSavedWeighted = true;

    private BlurBridge() {}

    public static boolean available() {
        init();
        return available;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
            try {
                fRefreshScaling = ce.getField("refreshRateScaling");
                fAlgorithm = ce.getField("blurAlgorithm");
                fProfile = ce.getField("blurProfile");
                Class<? extends Enum> algEnum = (Class<? extends Enum>) fAlgorithm.getType();
                Class<? extends Enum> profEnum = (Class<? extends Enum>) fProfile.getType();
                algVelocity = Enum.valueOf(algEnum, "VELOCITY_BASED");
                algHybrid = Enum.valueOf(algEnum, "HYBRID_BLENDING");
                profWeighted = Enum.valueOf(profEnum, "WEIGHTED");
                profUniform = Enum.valueOf(profEnum, "UNIFORM");
            } catch (Throwable ignored) {
            }
            save = cm.getMethod("saveConfig");
            config = cfg;
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    /** Empurra o estado do módulo para o mod (com boost dinâmico em alta velocidade). */
    public static void sync(Module m) {
        sync(m, 0f);
    }

    public static void sync(Module m, float highSpeedBoost) {
        init();
        if (!available || m == null) {
            return;
        }
        try {
            boolean on = m.enabled;
            float baseStrength = (float) m.numProp("strength", 1.25);
            boolean speedBoost = m.boolProp("speed_boost", true);
            boolean hybrid = m.boolProp("hybrid", true);
            boolean weighted = m.boolProp("weighted", true);

            float effStrength = baseStrength;
            if (speedBoost && highSpeedBoost > 0.01f) {
                effStrength = Math.min(3.0f, baseStrength * (1.0f + highSpeedBoost * 0.75f));
            }

            if (fEnabled.getBoolean(config) != on) {
                fEnabled.setBoolean(config, on);
            }
            if (Math.abs(fStrength.getFloat(config) - effStrength) > 0.005f) {
                fStrength.setFloat(config, effStrength);
            }
            if (fRefreshScaling != null && !fRefreshScaling.getBoolean(config)) {
                fRefreshScaling.setBoolean(config, true);
            }
            if (fAlgorithm != null && algVelocity != null && algHybrid != null) {
                Object targetAlg = hybrid ? algHybrid : algVelocity;
                if (fAlgorithm.get(config) != targetAlg) {
                    fAlgorithm.set(config, targetAlg);
                }
            }
            if (fProfile != null && profWeighted != null && profUniform != null) {
                Object targetProf = weighted ? profWeighted : profUniform;
                if (fProfile.get(config) != targetProf) {
                    fProfile.set(config, targetProf);
                }
            }

            // Salva no disco apenas quando as opções base mudam (não a cada frame de boost)
            if (lastSavedOn != on
                    || Math.abs(lastSavedBaseStrength - baseStrength) > 0.01f
                    || lastSavedHybrid != hybrid
                    || lastSavedWeighted != weighted) {
                lastSavedOn = on;
                lastSavedBaseStrength = baseStrength;
                lastSavedHybrid = hybrid;
                lastSavedWeighted = weighted;
                if (save != null) {
                    save.invoke(null);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
