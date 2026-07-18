package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.util.AdminAuth;
import com.p2pconnect.mod.util.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Picks (or logs back into) a username, now backed by a password so someone
 * else can't just start using your name. See MqttService's "Username
 * registry" section for the honest scope note - this is a best-effort claim
 * check over a shared public broker, not a real accounts system.
 */
public class UsernamePromptScreen extends Screen {

    private static final int TITLE_Y = -70;
    private static final int USERNAME_BOX_Y = -46;
    private static final int PASSWORD_BOX_Y = -20;
    private static final int STATUS_Y = 8;
    private static final int CONFIRM_BUTTON_Y = 22;
    private static final int CANCEL_BUTTON_Y = 48;

    private final Screen parent;
    private EditBox usernameBox;
    private EditBox passwordBox;
    private Button confirmButton;
    private String status = "";

    public UsernamePromptScreen(Screen parent) {
        super(Component.literal("P2P Connect - Username"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int boxWidth = 200;

        usernameBox = new EditBox(this.font, cx - boxWidth / 2, cy + USERNAME_BOX_Y, boxWidth, 20, Component.literal("Username"));
        usernameBox.setMaxLength(24);
        usernameBox.setValue(ClientConfig.username);
        this.addRenderableWidget(usernameBox);
        this.setInitialFocus(usernameBox);

        passwordBox = new EditBox(this.font, cx - boxWidth / 2, cy + PASSWORD_BOX_Y, boxWidth, 20, Component.literal("Password"));
        passwordBox.setMaxLength(64);
        this.addRenderableWidget(passwordBox);

        confirmButton = Button.builder(Component.literal("Continue"), b -> confirm())
                .bounds(cx - 100, cy + CONFIRM_BUTTON_Y, 200, 20)
                .build();
        this.addRenderableWidget(confirmButton);

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - 100, cy + CANCEL_BUTTON_Y, 200, 20)
                .build());
    }

    private boolean isValidUsername(String s) {
        return s != null && s.length() >= 3 && s.length() <= 24 && s.matches("[A-Za-z0-9_]+");
    }

    private void confirm() {
        String username = usernameBox.getValue().trim();
        String password = passwordBox.getValue();

        if (!isValidUsername(username)) {
            status = "§cUsername must be 3-24 letters/numbers/underscore.";
            return;
        }
        if (password.isEmpty()) {
            status = "§cEnter a password to protect this username.";
            return;
        }

        confirmButton.active = false;
        if (P2PConnectMod.MQTT.isConnected()) {
            checkAndRegister(username, password);
        } else {
            status = "§7Connecting...";
            P2PConnectMod.MQTT.connect(ClientConfig.mqttBrokerUrl,
                    () -> checkAndRegister(username, password),
                    err -> {
                        status = "§cCouldn't connect: " + err;
                        confirmButton.active = true;
                    });
        }
    }

    private void checkAndRegister(String username, String password) {
        status = "§7Checking availability...";
        String hash = AdminAuth.sha256Hex(password);

        P2PConnectMod.MQTT.checkUsernameRegistration(username, existing -> Minecraft.getInstance().execute(() -> {
            if (existing == null) {
                // Free - claim it.
                P2PConnectMod.MQTT.registerUsername(username, hash);
                applyAndProceed(username, hash);
                return;
            }

            String existingHash = existing.has("passwordHash") ? existing.get("passwordHash").getAsString() : "";
            if (existingHash.equals(hash)) {
                // Already yours (e.g. logging back in on a fresh config) - no need to re-publish.
                applyAndProceed(username, hash);
            } else {
                status = "§cThat username is already taken with a different password.";
                confirmButton.active = true;
            }
        }));
    }

    private void applyAndProceed(String username, String passwordHash) {
        ClientConfig.username = username;
        ClientConfig.accountPasswordHash = passwordHash;
        ClientConfig.save();
        Minecraft.getInstance().setScreen(new P2PMainScreen(parent));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "Pick a username and password:", cx, cy + TITLE_Y, 0xFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, cx, cy + STATUS_Y, 0xFFFF55);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
