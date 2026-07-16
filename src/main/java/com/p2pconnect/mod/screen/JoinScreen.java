package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.network.ConnectUtil;
import com.p2pconnect.mod.util.ClientConfig;
import com.p2pconnect.mod.util.ModListUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class JoinScreen extends Screen {

    // Layout constants - kept together so rows never drift into each other again.
    private static final int TITLE_Y = -90;
    private static final int STATUS_Y = -76;
    private static final int USERNAME_BOX_Y = -60;
    private static final int SEND_BUTTON_Y = -34;
    private static final int SEPARATOR_Y = -2;
    private static final int ID_TITLE_Y = 10;
    private static final int ID_BOX_Y = 24;
    private static final int ID_BUTTON_Y = 50;
    private static final int BACK_BUTTON_Y = 78;

    private final Screen parent;
    private EditBox usernameBox;
    private EditBox idBox;
    private String status = "";

    public JoinScreen(Screen parent) {
        super(Component.literal("P2P Connect - Join"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // --- Send a request by username ---
        usernameBox = new EditBox(this.font, cx - 100, cy + USERNAME_BOX_Y, 200, 20, Component.literal("Friend's username"));
        this.addRenderableWidget(usernameBox);

        this.addRenderableWidget(Button.builder(Component.literal("Send Request"), b -> sendRequest())
                .bounds(cx - 100, cy + SEND_BUTTON_Y, 200, 20)
                .build());

        // --- Connect directly by ID ---
        idBox = new EditBox(this.font, cx - 100, cy + ID_BOX_Y, 200, 20, Component.literal("bore.pub:12345"));
        this.addRenderableWidget(idBox);

        this.addRenderableWidget(Button.builder(Component.literal("Connect by ID"), b -> connectById())
                .bounds(cx - 100, cy + ID_BUTTON_Y, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 100, cy + BACK_BUTTON_Y, 200, 20)
                .build());
    }

    private void sendRequest() {
        String target = usernameBox.getValue().trim();
        if (target.isEmpty() || target.equalsIgnoreCase(ClientConfig.username)) {
            status = "§cEnter a valid username.";
            return;
        }
        if (!P2PConnectMod.MQTT.isConnected()) {
            status = "§cNot connected to the broker yet, wait a second and try again.";
            return;
        }

        status = "§7Request sent, waiting for " + target + "'s response...";

        P2PConnectMod.MQTT.listenForResponse(ClientConfig.username, response -> {
            if (response == null) return;
            String st = response.has("status") ? response.get("status").getAsString() : "denied";
            Minecraft.getInstance().execute(() -> {
                if (st.equals("accepted")) {
                    handleAccepted(target, response);
                } else {
                    status = "§c" + target + " declined the request.";
                }
            });
        });

        P2PConnectMod.MQTT.sendJoinRequest(target, ClientConfig.username);
    }

    private void handleAccepted(String target, com.google.gson.JsonObject response) {
        String host = response.get("host").getAsString();
        int port = response.get("port").getAsInt();
        String address = host + ":" + port;

        List<String> theirMods = new ArrayList<>();
        if (response.has("modlist")) {
            response.getAsJsonArray("modlist").forEach(e -> theirMods.add(e.getAsString()));
        }
        var diff = ModListUtil.compare(ModListUtil.getInstalledMods(), theirMods);

        if (diff.isCompatible()) {
            ConnectUtil.connect(parent, address, target + "'s world");
        } else {
            StringBuilder msg = new StringBuilder();
            if (!diff.missingOnMySide.isEmpty())
                msg.append("Missing mods: ").append(String.join(", ", diff.missingOnMySide)).append(". ");
            if (!diff.versionMismatch.isEmpty())
                msg.append("Version mismatch: ").append(String.join(", ", diff.versionMismatch)).append(".");

            Minecraft.getInstance().setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            ConnectUtil.connect(parent, address, target + "'s world");
                        } else {
                            Minecraft.getInstance().setScreen(this);
                        }
                    },
                    Component.literal("Mod list doesn't match"),
                    Component.literal(msg.toString() + " Connect anyway?")
            ));
        }
    }

    private void connectById() {
        String id = idBox.getValue().trim();
        if (!id.contains(":")) {
            status = "§cInvalid ID. Example: bore.pub:12345";
            return;
        }
        ConnectUtil.connect(parent, id, "P2P Server");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "Send a Request by Username", cx, cy + TITLE_Y, 0xFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, cx, cy + STATUS_Y, 0xFFFF55);
        }
        graphics.drawCenteredString(this.font, "— or —", cx, cy + SEPARATOR_Y, 0x888888);
        graphics.drawCenteredString(this.font, "Connect Directly by ID", cx, cy + ID_TITLE_Y, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
