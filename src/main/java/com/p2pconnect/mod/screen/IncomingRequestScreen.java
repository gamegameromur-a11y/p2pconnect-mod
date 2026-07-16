package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.network.HostingUtil;
import com.p2pconnect.mod.util.AdminAuth;
import com.p2pconnect.mod.util.ClientConfig;
import com.p2pconnect.mod.util.PendingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.Collections;

/**
 * Shown when someone asks to play with you. "Accept" now hosts immediately
 * if you're already in a world (this screen can pop up mid-game since the
 * pause menu can open P2P Connect too), and only sends you to pick/create a
 * world if you weren't in one yet - it no longer forces a brand new world
 * every time.
 */
public class IncomingRequestScreen extends Screen {

    private static final int TITLE_Y = -30;
    private static final int SUBTITLE_Y = -14;
    private static final int PASSWORD_NOTE_Y = 2;
    private static final int BUTTON_Y = 20;

    private final String fromUsername;
    private final String providedPassword;
    private Button acceptButton;
    private Button denyButton;
    private String status = null;

    public IncomingRequestScreen(String fromUsername) {
        this(fromUsername, null);
    }

    public IncomingRequestScreen(String fromUsername, String providedPassword) {
        super(Component.literal("P2P Connect - Incoming Request"));
        this.fromUsername = fromUsername;
        this.providedPassword = providedPassword;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        acceptButton = Button.builder(Component.literal("Accept"), b -> accept())
                .bounds(cx - 105, cy + BUTTON_Y, 100, 20)
                .build();
        this.addRenderableWidget(acceptButton);

        denyButton = Button.builder(Component.literal("Decline"), b -> deny())
                .bounds(cx + 5, cy + BUTTON_Y, 100, 20)
                .build();
        this.addRenderableWidget(denyButton);
    }

    private void accept() {
        acceptButton.active = false;
        denyButton.active = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getSingleplayerServer() != null) {
            // Already playing - host this world right now, no redirect needed.
            status = "§7Setting up hosting...";
            HostingUtil.startHostingCurrentWorld(fromUsername,
                    id -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(null)),
                    error -> Minecraft.getInstance().execute(() -> {
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.literal("§c[P2P Connect] Could not start hosting: " + error), false);
                        }
                        Minecraft.getInstance().setScreen(null);
                    }));
        } else {
            // Not in a world yet - let the player pick an existing world or create one.
            PendingState.pendingAutoHostForRequester = fromUsername;
            mc.setScreen(new SelectWorldScreen(this));
        }
    }

    private void deny() {
        P2PConnectMod.MQTT.respondToRequest(fromUsername, false, null, 0, Collections.emptyList());
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "§e" + fromUsername + " §fwants to play with you!", cx, cy + TITLE_Y, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Accepting will host your current (or next) world for them.", cx, cy + SUBTITLE_Y, 0xAAAAAA);

        if (ClientConfig.hasAdminPassword()) {
            boolean matches = AdminAuth.matches(providedPassword, ClientConfig.adminPasswordHash);
            String note = matches ? "§aThis request included the correct server password."
                    : "§cThis request did NOT include your server password.";
            graphics.drawCenteredString(this.font, note, cx, cy + PASSWORD_NOTE_Y, matches ? 0x55FF55 : 0xFF5555);
        }

        if (status != null) {
            graphics.drawCenteredString(this.font, status, cx, cy + BUTTON_Y + 26, 0xFFFF55);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    // No isPauseScreen() override - this can now appear while actively playing
    // (not just at the main menu), so it should pause singleplayer like any
    // other full-screen menu, instead of leaving the player exposed while they
    // read the request.
}
