package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.network.HostingUtil;
import com.p2pconnect.mod.util.PendingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * Hosting screen. "Start Broadcast" behaves in one of two ways:
 *  - Already inside a singleplayer world -> hosts that world immediately.
 *  - Not in a world (opened from the main menu) -> sends the player to the
 *    vanilla world list, where they can load an existing world or create a
 *    new one themselves. AutoHostTrigger picks up PendingState.pendingManualHost
 *    once a world actually finishes loading and starts hosting it.
 * This used to be unreachable while already playing (only wired into the
 * main-menu Multiplayer screen) and always forced world creation - both are
 * fixed now: ScreenInjector also adds an entry point to the pause menu, and
 * this no longer forces CreateWorldScreen specifically.
 */
public class HostScreen extends Screen {

    private static final int TITLE_Y = -60;
    private static final int STATUS_Y = -44;
    private static final int START_BUTTON_Y = -20;
    private static final int STOP_BUTTON_Y = 6;
    private static final int MANAGE_BUTTON_Y = 32;
    private static final int BACK_BUTTON_Y = 58;

    private final Screen parent;
    private String status;

    public HostScreen(Screen parent) {
        super(Component.literal("P2P Connect - Host"));
        this.parent = parent;
        this.status = HostingUtil.isHosting()
                ? "§aAlready live. Your connection ID: §f" + HostingUtil.getActiveId()
                : "In a world already? Hit \"Start Broadcast\". Otherwise you'll be able to pick or create one.";
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Start Broadcast"), b -> startHosting())
                .bounds(cx - 100, cy + START_BUTTON_Y, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Stop Broadcast"), b -> stopHosting())
                .bounds(cx - 100, cy + STOP_BUTTON_Y, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Manage Server"), b ->
                        Minecraft.getInstance().setScreen(new AdminPanelScreen(this)))
                .bounds(cx - 100, cy + MANAGE_BUTTON_Y, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 100, cy + BACK_BUTTON_Y, 200, 20)
                .build());
    }

    private void startHosting() {
        if (HostingUtil.isHosting()) {
            status = "§7Already live: §f" + HostingUtil.getActiveId();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getSingleplayerServer() != null) {
            status = "§7Opening your world to LAN and tunnelling out...";
            HostingUtil.startHostingCurrentWorld(null,
                    id -> status = "§aLive! Your connection ID: §f" + id,
                    error -> status = "§cCouldn't start hosting: " + error);
        } else {
            // No world loaded yet - let the player choose or create one instead
            // of forcing a specific flow on them. AutoHostTrigger starts hosting
            // automatically once that world finishes loading.
            PendingState.pendingManualHost = true;
            mc.setScreen(new SelectWorldScreen(this));
        }
    }

    private void stopHosting() {
        HostingUtil.stopHosting();
        status = "§7Broadcast stopped.";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "Host a Server", cx, cy + TITLE_Y, 0xFFFFFF);
        graphics.drawCenteredString(this.font, status, cx, cy + STATUS_Y, 0xFFFF55);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
