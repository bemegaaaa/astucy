package com.astucy.client.module;

/** Categoria do menu R-SHIFT (organização própria em 4 tabs). */
public enum ModuleCategory {
    COMBATE,
    INFORMACAO,
    VISAO,
    UTILIDADE;

    public String label() {
        return switch (this) {
            case COMBATE -> "Combat";
            case INFORMACAO -> "Info";
            case VISAO -> "Vision";
            case UTILIDADE -> "Utility";
        };
    }
}
