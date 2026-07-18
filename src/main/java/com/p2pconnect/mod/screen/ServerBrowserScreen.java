package com.p2pconnect.mod.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Browses public servers that other players hosting with this mod have
 * opted into listing (via the "List publicly" setting in Manage Server).
 * Listings arrive over MQTT as retained messages, so anything already live
 * shows up immediately when this screen opens; new/stopped servers update
 * the list live while it's open.
 *
 * Honest scope note: this only reflects what hosts voluntarily publish to a
 * public, unauthenticated MQTT topic. A "password protected" tag here is a
 * courtesy flag the host set, not a network-level guarantee - see
 * AdminAuth's class comment for the full threat-model note.
 */
public class ServerBrowserScreen extends Screen {

    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 24;

    // --- Browser view layout ---
    private static final int TITLE_Y = -120;
    private static final int STATUS_Y = -104;
    private static final int TOOLBAR_Y = -82;
    private static final int LIST_TOP_Y = -54;

    // --- Password-prompt sub-view layout ---
    private static final int PW_TITLE_Y = -50;
    private static final int PW_BOX_Y = -20;
    private static final int PW_SEND_Y = 10;
    private static final int PW_CANCEL_Y = 36;

    private final Screen parent;
    private final Map<String, JsonObject> listings = new LinkedHashMap<>();
    private String status = "§7Connecting...";

    private String pendingJoinUsername = null;
    private EditBox passwordBox;

    public ServerBrowserScreen(Screen parent) {
        super(Component.literal("P2P Connect - Public Servers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureConnectedThenSubscribe();
        rebuildRows();
    }

    /**
     * This screen used to assume MQTT was already connected (true only if
     * "P2P Connect" had been opened first) and would otherwise fail silently
     * inside a try/catch - the list would just stay empty forever with no
     * visible reason why, and Refresh looked unresponsive because it hit the
     * exact same silent failure. This makes the connection state explicit.
     */
    private void ensureConnectedThenSubscribe() {
        if (P2PConnectMod.MQTT.isConnected()) {
            status = "§7Listening for public servers...";
            subscribe();
            return;
        }
        status = "§7Connecting to broker...";
        P2PConnectMod.MQTT.connect(ClientConfig.mqttBrokerUrl,
                () -> {
                    status = "§7Listening for public servers...";
                    subscribe();
                    rebuildRows();
                },
                err -> {
                    status = "§cCouldn't connect: " + err;
                    rebuildRows();
                });
    }

    private void subscribe() {
        P2PConnectMod.MQTT.listenPublicListings((username, payload) -> Minecraft.getInstance().execute(() -> {
            if (payload == null) {
                listings.remove(username);
            } else {
                listings.put(username, payload);
            }
            rebuildRows();
        }));
    }

    private void rebuildRows() {
        this.clearWidgets();
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (pendingJoinUsername != null) {
            passwordBox = new EditBox(this.font, cx - 100, cy + PW_BOX_Y, 200, 20, Component.literal("Password"));
            this.addRenderableWidget(passwordBox);
            this.setInitialFocus(passwordBox);

            this.addRenderableWidget(Button.builder(Component.literal("Send Request"), b -> sendPasswordedRequest())
                    .bounds(cx - 100, cy + PW_SEND_Y, 200, 20)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                        pendingJoinUsername = null;
                        rebuildRows();
                    })
                    .bounds(cx - 100, cy + PW_CANCEL_Y, 200, 20)
                    .build());
            return;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> refresh())
                .bounds(cx - 154, cy + TOOLBAR_Y, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 54, cy + TOOLBAR_Y, 100, 20)
                .build());

        List<String> names = new ArrayList<>(listings.keySet());
        int shown = Math.min(names.size(), MAX_VISIBLE_ROWS);
        for (int i = 0; i < shown; i++) {
            String username = names.get(i);
            JsonObject listing = listings.get(username);
            int rowY = cy + LIST_TOP_Y + (i * ROW_HEIGHT);
            boolean isProtected = listing.has("passwordProtected") && listing.get("passwordProtected").getAsBoolean();

            String label = username + (isProtected ? "  [locked]" : "");
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> tryJoin(username))
                    .bounds(cx - 154, rowY, 308, 20)
                    .build());
        }
    }

    private void refresh() {
        listings.clear();
        if (P2PConnectMod.MQTT.isConnected()) {
            P2PConnectMod.MQTT.unsubscribePublicListings();
        }
        status = "§7Refreshing...";
        ensureConnectedThenSubscribe();
        rebuildRows();
    }

    private void tryJoin(String username) {
        JsonObject listing = listings.get(username);
        if (listing == null) return;
        boolean isProtected = listing.has("passwordProtected") && listing.get("passwordProtected").getAsBoolean();

        if (isProtected) {
            pendingJoinUsername = username;
            rebuildRows();
        } else {
            connectDirect(username, listing);
        }
    }

    private void sendPasswordedRequest() {
        String username = pendingJoinUsername;
        String password = passwordBox.getValue();
        pendingJoinUsername = null;

        status = "§7Sending request to " + username + "...";
        P2PConnectMod.MQTT.listenForResponse(ClientConfig.username, response -> {
            if (response == null) return;
            String st = response.has("status") ? response.get("status").getAsString() : "denied";
            Minecraft.getInstance().execute(() -> {
                if (st.equals("accepted")) {
                    handleAccepted(username, response);
                } else {
                    status = "§c" + username + " declined the request (wrong password?).";
                    rebuildRows();
                }
            });
        });
        P2PConnectMod.MQTT.sendJoinRequest(username, ClientConfig.username, password);
        rebuildRows();
    }

    private void handleAccepted(String username, JsonObject response) {
        String host = response.get("host").getAsString();
        int port = response.get("port").getAsInt();
        JsonArray modlist = response.has("modlist") ? response.getAsJsonArray("modlist") : null;
        connectWithCompatCheck(username, host, port, modlist);
    }

    private void connectDirect(String username, JsonObject listing) {
        String host = listing.get("host").getAsString();
        int port = listing.get("port").getAsInt();
        JsonArray modlist = listing.has("modlist") ? listing.getAsJsonArray("modlist") : null;
        connectWithCompatCheck(username, host, port, modlist);
    }

    private void connectWithCompatCheck(String username, String host, int port, JsonArray modlistJson) {
        String address = host + ":" + port;
        List<String> theirMods = new ArrayList<>();
        if (modlistJson != null) {
            modlistJson.forEach(e -> theirMods.add(e.getAsString()));
        }
        var diff = ModListUtil.compare(ModListUtil.getInstalledMods(), theirMods);

        if (diff.isCompatible()) {
            ConnectUtil.connect(parent, address, username + "'s server");
        } else {
            StringBuilder msg = new StringBuilder();
            if (!diff.missingOnMySide.isEmpty())
                msg.append("Missing mods: ").append(String.join(", ", diff.missingOnMySide)).append(". ");
            if (!diff.versionMismatch.isEmpty())
                msg.append("Version mismatch: ").append(String.join(", ", diff.versionMismatch)).append(".");

            Minecraft.getInstance().setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            ConnectUtil.connect(parent, address, username + "'s server");
                        } else {
                            Minecraft.getInstance().setScreen(this);
                        }
                    },
                    Component.literal("Mod list doesn't match"),
                    Component.literal(msg.toString() + " Connect anyway?")
            ));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (pendingJoinUsername != null) {
            graphics.drawCenteredString(this.font, "Password for " + pendingJoinUsername + "'s server", cx, cy + PW_TITLE_Y, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawCenteredString(this.font, "Public Servers", cx, cy + TITLE_Y, 0xFFFFFF);
        graphics.drawCenteredString(this.font, status, cx, cy + STATUS_Y, 0xAAAAAA);

        if (listings.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No public servers right now.", cx, cy + LIST_TOP_Y + 10, 0xAAAAAA);
        } else if (listings.size() > MAX_VISIBLE_ROWS) {
            int noteY = cy + LIST_TOP_Y + (MAX_VISIBLE_ROWS * ROW_HEIGHT) + 6;
            graphics.drawCenteredString(this.font, "+" + (listings.size() - MAX_VISIBLE_ROWS) + " more not shown", cx, noteY, 0x888888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (P2PConnectMod.MQTT.isConnected()) {
            P2PConnectMod.MQTT.unsubscribePublicListings();
        }
        Minecraft.getInstance().setScreen(parent);
    }
}
