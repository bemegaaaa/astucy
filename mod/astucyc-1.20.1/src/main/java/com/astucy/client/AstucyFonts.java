package com.astucy.client;

import com.astucy.client.mixin.FontManagerAccessor;
import com.astucy.client.mixin.FontSetAccessor;
import com.astucy.client.mixin.MinecraftAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fontes do cliente (MC 1.20.1):
 *  - ui/menu/title/wordmark/etc.: fontes TTF Geist registradas em assets/astucy/font/*.json.
 *  - Aplicamos filtro LINEAR nas páginas de FontTexture quando disponíveis.
 */
public final class AstucyFonts {

    private static final ResourceLocation DISPLAY = new ResourceLocation("astucy", "display");
    private static final ResourceLocation LABEL = new ResourceLocation("astucy", "label");
    private static final ResourceLocation UI = new ResourceLocation("astucy", "ui");
    private static final ResourceLocation BRAND = new ResourceLocation("astucy", "brand");
    private static final ResourceLocation TITLE = new ResourceLocation("astucy", "title");
    private static final ResourceLocation WORDMARK = new ResourceLocation("astucy", "wordmark");
    private static final ResourceLocation UI8 = new ResourceLocation("astucy", "ui8");
    private static final ResourceLocation MENU = new ResourceLocation("astucy", "menu");
    private static final ResourceLocation MENU_BOLD = new ResourceLocation("astucy", "menu-bold");
    private static final ResourceLocation MENU_TITLE = new ResourceLocation("astucy", "menu-title");

    private static final Map<ResourceLocation, Font> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, FontSet> SEEN = new HashMap<>();
    private static final Map<FontSet, Integer> SMOOTHED_COUNT = new HashMap<>();
    private static final Set<AbstractTexture> LINEAR_TEXTURES =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static boolean logged;

    private AstucyFonts() {}

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

    public static Font display() {
        return fontFor(DISPLAY);
    }

    public static Font label() {
        return fontFor(LABEL);
    }

    public static Font brand() {
        return fontFor(BRAND);
    }

    public static Font title() {
        return fontFor(TITLE);
    }

    public static Font wordmark() {
        return fontFor(WORDMARK);
    }

    public static Font ui8() {
        return fontFor(UI8);
    }

    public static Font menu() {
        return fontFor(MENU);
    }

    public static Font menuBold() {
        return fontFor(MENU_BOLD);
    }

    public static Font menuTitle() {
        return fontFor(MENU_TITLE);
    }

    private static Font fontFor(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        try {
            FontManager fm = ((MinecraftAccessor) mc).astucy$getFontManager();
            if (fm == null) {
                return mc.font;
            }
            Map<ResourceLocation, FontSet> sets = ((FontManagerAccessor) fm).astucy$getFontSets();
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
                cached = new Font(rl -> {
                    smooth(set);
                    return set;
                }, false);
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
            if (!RenderSystem.isOnRenderThreadOrInit()) {
                return;
            }
            List<FontTexture> textures = ((FontSetAccessor) (Object) set).astucy$getTextures();
            if (textures == null || textures.isEmpty()) {
                return;
            }
            Integer prevCount = SMOOTHED_COUNT.get(set);
            if (prevCount != null && prevCount == textures.size()) {
                return;
            }
            int n = 0;
            for (FontTexture ft : textures) {
                if (ft != null) {
                    markLinearTexture(ft);
                    ft.bind();
                    ft.setFilter(true, false);
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

    public static void markLinearTexture(AbstractTexture texture) {
        if (texture != null) {
            LINEAR_TEXTURES.add(texture);
        }
    }

    public static boolean isLinearTexture(AbstractTexture texture) {
        return texture != null && LINEAR_TEXTURES.contains(texture);
    }
}
