package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.util.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Adds P2P Connect's entry points to vanilla screens:
 *  - "Multiplayer" (server list) screen: "P2P Connect" + "Public Servers" buttons.
 *  - In-game pause menu: "P2P Connect" button.
 * The pause menu entry is what makes hosting your *current* world possible -
 * without it, P2P Connect was only reachable from the main menu, where you're
 * never actually inside a world yet.
 */
public class ScreenInjector {

    private static final int BUTTON_WIDTH = 100;
    private static final int SERVERS_BUTTON_WIDTH = 90;
    private static final int MARGIN = 6;

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof JoinMultiplayerScreen screen) {
            int y = MARGIN;
            int p2pX = screen.width - BUTTON_WIDTH - MARGIN;
            int serversX = p2pX - SERVERS_BUTTON_WIDTH - MARGIN;

            event.addListener(Button.builder(Component.literal("Public Servers"), b -> openServerBrowser())
                    .bounds(serversX, y, SERVERS_BUTTON_WIDTH, 20)
                    .build());

            event.addListener(Button.builder(Component.literal("P2P Connect"), b -> openEntryPoint())
                    .bounds(p2pX, y, BUTTON_WIDTH, 20)
                    .build());
            return;
        }

        if (event.getScreen() instanceof PauseScreen screen) {
            int x = screen.width - BUTTON_WIDTH - MARGIN;
            int y = MARGIN;

            event.addListener(Button.builder(Component.literal("P2P Connect"), b -> openEntryPoint())
                    .bounds(x, y, BUTTON_WIDTH, 20)
                    .build());
        }
    }

    private void openEntryPoint() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.hasUsername()) {
            mc.setScreen(new UsernamePromptScreen(mc.screen));
        } else {
            mc.setScreen(new P2PMainScreen(mc.screen));
        }
    }

    private void openServerBrowser() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.hasUsername()) {
            mc.setScreen(new UsernamePromptScreen(mc.screen));
        } else {
            mc.setScreen(new ServerBrowserScreen(mc.screen));
        }
    }
}
