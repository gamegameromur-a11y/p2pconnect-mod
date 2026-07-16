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
    }

    private void ensureMqttConnected() {
        if (P2PConnectMod.MQTT.isConnected()) {
            statusText = "Connected: " + ClientConfig.mqttBrokerUrl;
            startListeningIfNeeded();
            return;
        }
        statusText = "Connecting to broker...";
        P2PConnectMod.MQTT.connect(ClientConfig.mqttBrokerUrl,
                () -> {
                    statusText = "Connected!";
                    startListeningIfNeeded();
                },
                err -> statusText = "Error: " + err);
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
        graphics.drawCenteredString(this.font, statusText, this.width / 2, this.height / 2 - 48, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
