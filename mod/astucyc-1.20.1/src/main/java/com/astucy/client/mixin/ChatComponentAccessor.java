package com.astucy.client.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> astucy$getTrimmedMessages();

    @Invoker("screenToChatX")
    double astucy$screenToChatX(double mouseX);

    @Invoker("screenToChatY")
    double astucy$screenToChatY(double mouseY);

    @Invoker("getMessageLineIndexAt")
    int astucy$getMessageLineIndexAt(double chatX, double chatY);
}
