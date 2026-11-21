package com.astucy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fontes do cliente (MC 1.21.4).
 */
public final class AstucyFonts {

    private static final ResourceLocation DISPLAY = ResourceLocation.fromNamespaceAndPath("astucy", "display");
    private static final ResourceLocation LABEL = ResourceLocation.fromNamespaceAndPath("astucy", "label");
    private static final ResourceLocation UI = ResourceLocation.fromNamespaceAndPath("astucy", "ui");
    private static final ResourceLocation BRAND = ResourceLocation.fromNamespaceAndPath("astucy", "brand");
    private static final ResourceLocation TITLE = ResourceLocation.fromNamespaceAndPath("astucy", "title");
    private static final ResourceLocation WORDMARK = ResourceLocation.fromNamespaceAndPath("astucy", "wordmark");
    private static final ResourceLocation UI8 = ResourceLocation.fromNamespaceAndPath("astucy", "ui8");
    private static final ResourceLocation MENU = ResourceLocation.fromNamespaceAndPath("astucy", "menu");
    private static final ResourceLocation MENU_BOLD = ResourceLocation.fromNamespaceAndPath("astucy", "menu-bold");
    private static final ResourceLocation MENU_TITLE = ResourceLocation.fromNamespaceAndPath("astucy", "menu-title");

    private static Field fontManagerField;
    private static Field fontSetsField;
    private static Field texturesField;
    private static final Map<ResourceLocation, Font> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, FontSet> SEEN = new HashMap<>();
    private static final Map<FontSet, Integer> SMOOTHED_COUNT = new HashMap<>();
    private static final Set<FontTexture> LINEAR_TEXTURES =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static boolean logged;

    private AstucyFonts() {}

    public static void markLinearTexture(FontTexture tex) {
        if (tex != null) {
            LINEAR_TEXTURES.add(tex);
            try {
                tex.setFilter(true, false);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isLinearTexture(Object tex) {
        return tex instanceof FontTexture ft && LINEAR_TEXTURES.contains(ft);
    }

    /** Fonte da interface e do HUD. */
    public static Font ui() {
        return fontFor(UI);
    }

    public static Font bold() {
        return fontFor(MENU_BOLD);
    }

    public static Font mid() {
        return fontFor(TITLE);
    }

    public static Font midBold() {
        return fontFor(MENU_TITLE);
    }

    /** Wordmark (TTF do tema); cai pra vanilla se o pack não existir. */
    public static Font display() {
        return fontFor(DISPLAY);
    }

    /** Rótulo dos botões — só na interface do client. */
    public static Font label() {
        return fontFor(LABEL);
    }

    /** Wordmark do cabeçalho do menu. */
    public static Font brand() {
        return fontFor(BRAND);
    }

    /** Texto pequeno da tela de título. */
    public static Font title() {
        return fontFor(TITLE);
    }

    /** Wordmark da tela de título. */
    public static Font wordmark() {
        return fontFor(WORDMARK);
    }

    /** Texto de botão/tela de título menor. */
    public static Font ui8() {
        return fontFor(UI8);
    }

    /** Fonte do menu do client. */
    public static Font menu() {
        return fontFor(MENU);
    }

    /** SemiBold 9.5px (rótulos, títulos de seção). */
    public static Font menuBold() {
        return fontFor(MENU_BOLD);
    }

    /** SemiBold 11px (títulos de página). */
    public static Font menuTitle() {
        return fontFor(MENU_TITLE);
    }

    private static Font fontFor(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        try {
            FontManager fm = fontManager(mc);
            Map<ResourceLocation, FontSet> sets = fontSets(fm);
            if (sets == null) {
                return mc.font;
            }
            FontSet set = sets.get(id);
            if (set == null) {
                return mc.font;
            }
            Font cached = CACHE.get(id);
            if (cached == null || SEEN.get(id) != set) {
                SEEN.put(id, set);
                final FontSet target = set;
                cached = new Font(ignored -> target, false);
                CACHE.put(id, cached);
            }
            smooth(set);
            return cached;
        } catch (Throwable t) {
            return mc.font;
        }
    }

    private static void smooth(FontSet set) {
        try {
            List<?> textures = findTexturesList(set);
            if (textures == null || textures.isEmpty()) {
                return;
            }
            Integer prevCount = SMOOTHED_COUNT.get(set);
            if (prevCount != null && prevCount == textures.size()) {
                return;
            }
            int n = 0;
            for (Object t : textures) {
                if (t instanceof FontTexture ft) {
                    LINEAR_TEXTURES.add(ft);
                    ft.setFilter(true, false);
                    n++;
                }
            }
            if (n > 0) {
                SMOOTHED_COUNT.put(set, textures.size());
                if (!logged) {
                    logged = true;
                    System.out.println("[astucy] fontes: filtro LINEAR aplicado em " + n + " atlas");
                }
            }
        } catch (Throwable t) {
            if (!logged) {
                logged = true;
                System.out.println("[astucy] fontes: filtro LINEAR FALHOU: " + t);
            }
        }
    }

    private static List<?> findTexturesList(FontSet set) throws Exception {
        if (texturesField != null) {
            return (List<?>) texturesField.get(set);
        }
        try {
            Field fd = FontSet.class.getDeclaredField("textures");
            fd.setAccessible(true);
            texturesField = fd;
            return (List<?>) fd.get(set);
        } catch (NoSuchFieldException ignored) {
        }
        for (Field fd : FontSet.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(fd.getType())) {
                fd.setAccessible(true);
                Object val = fd.get(set);
                if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof FontTexture) {
                    texturesField = fd;
                    return list;
                }
            }
        }
        return null;
    }

    private static FontManager fontManager(Minecraft mc) throws Exception {
        if (fontManagerField == null) {
            for (Field fd : mc.getClass().getDeclaredFields()) {
                if (fd.getType() == FontManager.class) {
                    fd.setAccessible(true);
                    fontManagerField = fd;
                    break;
                }
            }
        }
        FontManager fm = fontManagerField == null ? null : (FontManager) fontManagerField.get(mc);
        if (fm == null) {
            throw new IllegalStateException("FontManager não achado");
        }
        return fm;
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, FontSet> fontSets(FontManager fm) {
        try {
            if (fontSetsField == null) {
                for (Field fd : fm.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(fd.getType())) {
                        fd.setAccessible(true);
                        fontSetsField = fd;
                        break;
                    }
                }
            }
            return fontSetsField == null ? null : (Map<ResourceLocation, FontSet>) fontSetsField.get(fm);
        } catch (Throwable t) {
            return null;
        }
    }
}
