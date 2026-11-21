package com.astucy.client;

import com.astucy.client.config.AstucyConfig;
import com.astucy.client.gui.HudEditorScreen;
import com.astucy.client.gui.RShiftScreen;
import com.astucy.client.module.Modules;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

public class AstucyClient implements ClientModInitializer {
    public static KeyBinding MENU_KEY;
    public static KeyBinding HUD_KEY;
    public static KeyBinding ZOOM_KEY;

    @Override
    public void onInitializeClient() {
        AstucyConfig.load();
        AstucyConfig.applyToModules();

        MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("Astucy Menu", Keyboard.KEY_RSHIFT, "Astucy"));
        HUD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("Astucy HUD Editor", Keyboard.KEY_NONE, "Astucy"));
        ZOOM_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("Astucy Zoom", Keyboard.KEY_C, "Astucy"));

        HudRenderCallback.EVENT.register(new HudRenderCallback() {
            @Override
            public void onHudRender(MinecraftClient client, float tickDelta) {
                Modules.renderHud(client);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            @Override
            public void onEndTick(MinecraftClient client) {
                if (client == null) return;
                while (MENU_KEY.wasPressed()) {
                    if (client.currentScreen == null) {
                        client.setScreen(new RShiftScreen());
                    } else if (client.currentScreen instanceof RShiftScreen) {
                        client.setScreen(null);
                    }
                }
                while (HUD_KEY.wasPressed()) {
                    if (client.currentScreen == null) {
                        client.setScreen(new HudEditorScreen(null));
                    }
                }
                boolean zDown = ZOOM_KEY.getCode() != 0 && Keyboard.isKeyDown(ZOOM_KEY.getCode()) && client.currentScreen == null;
                Modules.setZoomHeld(zDown);
                Modules.onClientTick(client);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(new ClientLifecycleEvents.ClientStopping() {
            @Override
            public void onClientStopping(MinecraftClient client) {
                AstucyConfig.flushNow();
            }
        });
    }
}
