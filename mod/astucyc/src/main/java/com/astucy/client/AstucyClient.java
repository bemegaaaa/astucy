package com.astucy.client;

import com.astucy.client.config.AstucyConfig;
import com.astucy.client.gui.AccountMenu;
import com.astucy.client.gui.HudEditorScreen;
import com.astucy.client.gui.RShiftScreen;
import com.astucy.client.module.Modules;
import com.astucy.client.share.ScreenshotShare;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * astucyc — módulos internos do Astucy (0.1.0).
 * R-SHIFT abre o menu; HUD por HudElementRegistry; tudo client-sided.
 */
public class AstucyClient implements ClientModInitializer {

    private static KeyMapping menuKey;
    private static KeyMapping editorKey;
    private static KeyMapping zoomKey;
    private static boolean hintShown = false;

    @Override
    public void onInitializeClient() {
        Modules.registerAll();
        AstucyConfig.load();

        // Tema visual (fonte Poppins) vem de resource pack REAL instalado
        // e ativado pelo launcher no gameDir do perfil (engine.rs).

        var category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("astucyc", "main"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.astucyc.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        editorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.astucyc.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.astucyc.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        Modules.zoomKey = zoomKey;

        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("astucyc", "hud"),
                (gfx, deltaTracker) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof RShiftScreen || mc.gui.screen() instanceof HudEditorScreen) {
                        // menu/editor desenham por conta própria, mas a lógica
                        // (zoom, CPS, sneak) continua viva
                        Modules.frame(mc);
                        return;
                    }
                    Modules.renderHud(gfx);
                });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Modules.tick(mc);
            ScreenshotShare.tick(mc);
            AccountMenu.tick(mc);
            while (menuKey.consumeClick()) {
                mc.gui.setScreen(new RShiftScreen());
            }
            while (editorKey.consumeClick()) {
                mc.gui.setScreen(new HudEditorScreen());
            }
            if (mc.player == null) {
                hintShown = false;
            } else if (!hintShown) {
                hintShown = true;
                Modules.triggerJoinIntro();
                mc.player.sendSystemMessage(
                        Component.literal("§fAstucy§8: §7R-SHIFT opens the module menu"));
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            Modules.restoreViewportOnExit(mc);
            AstucyConfig.save();
        });
    }
}
