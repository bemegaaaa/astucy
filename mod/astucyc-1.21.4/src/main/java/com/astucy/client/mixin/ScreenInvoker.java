package com.astucy.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Acesso a membros protegidos/privados de Screen usados pela tela de tÃ­tulo. */
@Mixin(Screen.class)
public interface ScreenInvoker {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T astucy$addWidget(T widget);

    /** Desenha o panorama do menu (protected em Screen). */
    @Invoker("renderPanorama")
    void astucy$panorama(GuiGraphics g, float partial);

    /** Lista de tudo que a tela desenha (botÃµes, etc.). */
    @Accessor("renderables")
    List<Renderable> astucy$renderables();
}
