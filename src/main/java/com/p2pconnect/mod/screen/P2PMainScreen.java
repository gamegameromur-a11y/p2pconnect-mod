package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.util.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class P2PMainScreen extends Screen {

    private final Screen parent;
    private static boolean listeningForRequests = false; // set up once per game session, then stays active
    private String statusText = "Connecting...";
    private boolean connectionFailed = false;
    private Button retryButton;

    public P2PMainScreen(Screen parent) {
        super(Component.literal("P2P Connect"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureMqttConnected();

        this.addRenderableWidget(Button.builder(Component.literal("Start Broadcast (Host)"), b ->
                        Minecraft.getInstance().setScreen(new HostScreen(this)))
                .bounds(this.width / 2 - 100, this.height / 2 - 30, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Connect to a Friend (Join)"), b ->
                        Minecraft.getInstance().setScreen(new JoinScreen(this)))
                .bounds(this.width / 2 - 100, this.height / 2 - 4, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Change Username (" + ClientConfig.username + ")"), b ->
                        // Return to this screen afterwards, not all the way back to the multiplayer
                        // list - previously this passed `parent`, which skipped back past this menu.
                        Minecraft.getInstance().setScreen(new UsernamePromptScreen(this)))
                .bounds(this.width / 2 - 100, this.height / 2 + 22, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), this::onClosePressed)
                .bounds(this.width / 2 - 100, this.height / 2 + 48, 200, 20)
                .build());

        // Only shown when the broker connection actually failed - lets the player retry without
        // having to close and reopen this whole menu.
        retryButton = Button.builder(Component.literal("Retry Connection"), b -> {
                    connectionFailed = false;
                    retryButton.visible = false;
                    ensureMqttConnected();
                })
                .bounds(this.width / 2 - 100, this.height / 2 + 74, 200, 20)
                .build();
        retryButton.visible = connectionFailed;
        this.addRenderableWidget(retryButton);
    }

    private void ensureMqttConnected() {
        if (P2PConnectMod.MQTT.isConnected()) {
            statusText = "Connected: " + ClientConfig.mqttBrokerUrl;
            connectionFailed = false;
            startListeningIfNeeded();
            return;
        }
        statusText = "Connecting to broker...";
        P2PConnectMod.MQTT.connect(ClientConfig.mqttBrokerUrl,
                () -> {
                    statusText = "Connected!";
                    connectionFailed = false;
                    startListeningIfNeeded();
                },
                err -> {
                    statusText = "Error: " + err;
                    connectionFailed = true;
                    if (retryButton != null) retryButton.visible = true;
                });

        P2PConnectMod.MQTT.setOnReconnected(() -> Minecraft.getInstance().execute(() -> {
            statusText = "Reconnected: " + ClientConfig.mqttBrokerUrl;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§a[P2P Connect] Reconnected to the broker."), false);
            }
        }));
    }

    /** Listening for incoming requests is set up ONLY ONCE - then stays active until the game closes. */
    private void startListeningIfNeeded() {
        if (listeningForRequests) return;
        listeningForRequests = true;
        P2PConnectMod.MQTT.listenForRequests(ClientConfig.username, request -> {
            if (request == null) return;
            String from = request.has("from") ? request.get("from").getAsString() : "Unknown";
            String password = request.has("password") ? request.get("password").getAsString() : null;
            // The MQTT callback arrives on a background thread - the screen has to be opened on the Minecraft main thread.
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().setScreen(new IncomingRequestScreen(from, password)));
        });
    }

    private void onClosePressed(Button b) {
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "P2P Connect", this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        graphics.drawCenteredString(this.font, statusText, this.width / 2, this.height / 2 - 48, connectionFailed ? 0xFF5555 : 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
