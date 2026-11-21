package com.astucy.client.module;

public enum ModuleCategory {
    COMBATE("Combat"),
    INFORMACAO("HUD"),
    VISAO("Visuals"),
    UTILIDADE("Utility");

    public final String label;

    ModuleCategory(String label) {
        this.label = label;
    }
}
