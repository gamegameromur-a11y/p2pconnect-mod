package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.util.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * "Multiplayer" (Sunucuya Katıl) ekranı açıldığında sağ üst köşeye
 * "P2P Connect" butonu ekler.
 */
public class ScreenInjector {

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof JoinMultiplayerScreen screen)) return;

        int buttonWidth = 100;
        int x = screen.width - buttonWidth - 6;
        int y = 6;

        Button p2pButton = Button.builder(Component.literal("P2P Connect"), b -> openEntryPoint())
                .bounds(x, y, buttonWidth, 20)
                .build();

        event.addListener(p2pButton);
    }

    private void openEntryPoint() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.hasUsername()) {
            mc.setScreen(new UsernamePromptScreen(mc.screen));
        } else {
            mc.setScreen(new P2PMainScreen(mc.screen));
        }
    }
}
