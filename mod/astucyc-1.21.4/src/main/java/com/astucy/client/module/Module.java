package com.astucy.client.module;

/**
 * Um módulo do Astucy com customização completa:
 * posição (âncora normalizada 0..1), escala, cores de texto e fundo,
 * opacidade do fundo, sombra e prefixo. Persistido em astucyc.json.
 */
public class Module {
    public final String id;
    public final String name;
    public final String description;
    public final ModuleCategory category;
    /** true = chip de HUD; false = só comportamento. */
    public final boolean hud;

    public boolean enabled;
    public double anchorX = 0.01;
    public double anchorY = 0.01;

    // ---- estilo (customizável no menu R-SHIFT) ----
    public double scale = 1.0;        // 0.60 .. 2.00
    public double scaleY = 1.0;       // altura separada (editor: SHIFT + roda)
    public boolean bg = true;         // fundo ligado
    public int bgColor = 0x99000000;  // ARGB do fundo (preto 60%, estilo client)
    public int fgColor = 0xFFECEFF3;  // ARGB do texto
    public boolean shadow = false;    // sombra no texto

    /** Marcado como adição recente (aparece no filtro NOVOS). */
    public boolean isNew = false;

    public Module isNew() {
        this.isNew = true;
        return this;
    }

    /** Propriedades de comportamento por módulo (ex.: opções do Zoom). */
    public final java.util.HashMap<String, Object> props = new java.util.HashMap<>();

    public boolean boolProp(String key, boolean def) {
        Object v = props.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public double numProp(String key, double def) {
        Object v = props.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
    public boolean prefix = true;     // "CPS: 12" em vez de "12"
    public String customLabel = "";   // texto próprio antes do valor (ex.: "SPRINT")

    /** Paleta de swatches (marca + apoio). */
    public static final int[] SWATCHES = {
        0xFFECEFF3, 0xFFC9CFD6, 0xFF878E96, 0xFF12161B,
        0xFFFF5D5D, 0xFFFFB454, 0xFFFFE066, 0xFF6FE3A2,
        0xFF5AD6E8, 0xFF6BA8FF, 0xFFB48FFF, 0xFFFF8FD6
    };

    public Module(String id, String name, String description, ModuleCategory category, boolean hud) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.hud = hud;
    }

    public Module at(double ax, double ay) {
        this.anchorX = ax;
        this.anchorY = ay;
        return this;
    }

    public void resetStyle() {
        this.scale = 1.0;
        this.scaleY = 1.0;
        this.bg = true;
        this.bgColor = 0x99000000;
        this.fgColor = 0xFFECEFF3;
        this.shadow = false;
        this.prefix = true;
        // comportamento também volta pro default (zoom: toggle/smooth/nível,
        // cps: teclas/mouse, …). O blur é re-escrito pelo mod dele a cada tick.
        this.props.clear();
    }
}
