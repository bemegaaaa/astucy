package com.astucy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fontes do cliente:
 *  - ui/bold/mid: fonte vanilla (bitmap nítida, 9px) — HUD e menus do cliente;
 *  - display: TTF Inter semibold em 44px (tema), usada no wordmark da tela
 *    de título. TTF em tamanho grande é suave; em 8px ficava serrilhado.
 */
public final class AstucyFonts {

    private static final Identifier DISPLAY = Identifier.fromNamespaceAndPath("astucy", "display");
    private static final Identifier LABEL = Identifier.fromNamespaceAndPath("astucy", "label");
    private static final Identifier UI = Identifier.fromNamespaceAndPath("astucy", "ui");
    private static final Identifier BRAND = Identifier.fromNamespaceAndPath("astucy", "brand");
    private static final Identifier TITLE = Identifier.fromNamespaceAndPath("astucy", "title");
    private static final Identifier WORDMARK = Identifier.fromNamespaceAndPath("astucy", "wordmark");
    private static final Identifier UI8 = Identifier.fromNamespaceAndPath("astucy", "ui8");
    private static final Identifier MENU = Identifier.fromNamespaceAndPath("astucy", "menu");
    private static final Identifier MENU_BOLD = Identifier.fromNamespaceAndPath("astucy", "menu-bold");
    private static final Identifier MENU_TITLE = Identifier.fromNamespaceAndPath("astucy", "menu-title");

    private static Field fontManagerField;
    private static Field fontSetsField;
    private static Field stitcherField;
    private static Field texturesField;
    private static Field samplerField;
    private static final Map<Identifier, Font> CACHE = new HashMap<>();
    private static final Map<Identifier, FontSet> SEEN = new HashMap<>();
    private static final Map<FontSet, Integer> SMOOTHED_COUNT = new HashMap<>();
    private static final Set<GpuTextureView> LINEAR_VIEWS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static boolean logged; 

    private AstucyFonts() {}

    public static void markLinearView(GpuTextureView view) {
        if (view != null) {
            LINEAR_VIEWS.add(view);
        }
    }

    /**
     * Retorna {@code true} se a view de textura pertence a um atlas de fonte
     * TTF do Astucy (que deve ser amostrado com {@link FilterMode#LINEAR}).
     */
    public static boolean isLinearView(GpuTextureView view) {
        if (view == null) {
            return false;
        }
        if (LINEAR_VIEWS.contains(view)) {
            return true;
        }
        try {
            GpuTexture tex = view.texture();
            if (tex != null) {
                String label = tex.getLabel();
                if (label != null && label.startsWith("astucy:")) {
                    LINEAR_VIEWS.add(view);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Fonte da interface e do HUD (Montserrat Medium 9px com oversample 8 + LINEAR AA).
     */
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

    /** Wordmark (TTF 44px do tema); cai pra vanilla se o pack não existir. */
    public static Font display() {
        return fontFor(DISPLAY);
    }

    /** Rótulo dos botões (Poppins 12px) — só na interface do client. */
    public static Font label() {
        return fontFor(LABEL);
    }

    /** Wordmark do cabeçalho do menu (DM Sans Bold 16px). */
    public static Font brand() {
        return fontFor(BRAND);
    }

    /** Texto pequeno da tela de título (DM Sans 10px). */
    public static Font title() {
        return fontFor(TITLE);
    }

    /**
     * Wordmark da tela de título (DM Sans Bold 32px). Texto de verdade em vez
     * de PNG reduzido: a fonte é rasterizada em alta e nunca serrilha.
     */
    public static Font wordmark() {
        return fontFor(WORDMARK);
    }

    /** Texto de botão/tela de título: DM Sans 8.5px (menor e mais clean). */
    public static Font ui8() {
        return fontFor(UI8);
    }

    /** Fonte do menu do client: Montserrat Medium 9px. */
    public static Font menu() {
        return fontFor(MENU);
    }

    /** Montserrat SemiBold 9px (rótulos, títulos de seção). */
    public static Font menuBold() {
        return fontFor(MENU_BOLD);
    }

    /** Montserrat SemiBold 11px (títulos de página). */
    public static Font menuTitle() {
        return fontFor(MENU_TITLE);
    }

    private static Font fontFor(Identifier id) {
        Minecraft mc = Minecraft.getInstance();
        try {
            FontManager fm = fontManager(mc);
            Map<Identifier, FontSet> sets = fontSets(fm);
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
                GlyphSource source = set.source(false);
                EffectGlyph effect = set.whiteGlyph();
                cached = new Font(new Provider(set, source, effect));
                CACHE.put(id, cached);
            }
            smooth(set);
            return cached;
        } catch (Throwable t) {
            return mc.font;
        }
    }

    /**
     * O atlas de fontes do Minecraft nasce com filtro NEAREST — com TTF isso
     * serrilha as bordas (o glifo é rasterizado em alta e reduzido no vizinho
     * mais próximo). Trocamos o sampler do atlas para LINEAR: as letras ganham
     * antialiasing de verdade sempre que uma página nova do atlas é criada.
     */
    private static void smooth(FontSet set) {
        try {
            if (stitcherField == null) {
                stitcherField = FontSet.class.getDeclaredField("stitcher");
                stitcherField.setAccessible(true);
                texturesField = GlyphStitcher.class.getDeclaredField("textures");
                texturesField.setAccessible(true);
                samplerField = AbstractTexture.class.getDeclaredField("sampler");
                samplerField.setAccessible(true);
            }
            Object stitcher = stitcherField.get(set);
            if (stitcher == null) {
                return;
            }
            List<?> textures = (List<?>) texturesField.get(stitcher);
            if (textures == null || textures.isEmpty()) {
                return;
            }
            Integer prevCount = SMOOTHED_COUNT.get(set);
            if (prevCount != null && prevCount == textures.size()) {
                return;
            }
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            int n = 0;
            for (Object t : textures) {
                if (t instanceof FontTexture ft) {
                    samplerField.set(ft, linear);
                    GpuTextureView view = ft.getTextureView();
                    if (view != null) {
                        LINEAR_VIEWS.add(view);
                    }
                    n++;
                }
            }
            SMOOTHED_COUNT.put(set, textures.size());
            if (!logged && n > 0) {
                logged = true;
                System.out.println("[astucy] fontes: filtro LINEAR aplicado em " + n + " atlas");
            }
        } catch (Throwable t) {
            if (!logged) {
                logged = true;
                System.out.println("[astucy] fontes: filtro LINEAR FALHOU: " + t);
            }
        }
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
    private static Map<Identifier, FontSet> fontSets(FontManager fm) {
        try {
            if (fontSetsField == null) {
                Field fd = fm.getClass().getDeclaredField("fontSets");
                fd.setAccessible(true);
                fontSetsField = fd;
            }
            return (Map<Identifier, FontSet>) fontSetsField.get(fm);
        } catch (Throwable t) {
            return null;
        }
    }

    private record Provider(FontSet set, GlyphSource source, EffectGlyph effect) implements Font.Provider {
        @Override
        public GlyphSource glyphs(FontDescription description) {
            smooth(set);
            return source;
        }

        @Override
        public EffectGlyph effect() {
            smooth(set);
            return effect;
        }
    }
}
