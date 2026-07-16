package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.network.HostingUtil;
import com.p2pconnect.mod.util.AdminAuth;
import com.p2pconnect.mod.util.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Manage Server" panel - the Aternos-style control panel for your own
 * hosted server: toggle whether it's listed on the Public Servers browser,
 * edit its description, set/change the admin password, and stop it.
 *
 * Honest scope note: the admin password gates THIS panel (and is checked as
 * a hint against incoming join requests) - it is not a network-level login
 * system. There's no server-side enforcement stopping someone from
 * connecting straight to the bore.pub address with a vanilla client,
 * because this mod opens a plain "Open to LAN" world and tunnels it - it
 * doesn't add real server-side authentication. Treat it as a courtesy gate,
 * not a lock. Settings you can't change here (max players, difficulty,
 * per-player kicks) would need deeper server-side networking work that
 * isn't included in this pass.
 */
public class AdminPanelScreen extends Screen {

    // --- Locked view ---
    private static final int LOCK_TITLE_Y = -50;
    private static final int LOCK_BOX_Y = -20;
    private static final int LOCK_UNLOCK_Y = 10;
    private static final int LOCK_BACK_Y = 36;

    // --- Settings view ---
    private static final int TITLE_Y = -130;
    private static final int DESC_LABEL_Y = -112;
    private static final int DESC_BOX_Y = -100;
    private static final int PUBLIC_TOGGLE_Y = -74;
    private static final int CHANGE_PW_BUTTON_Y = -48;
    private static final int SAVE_BUTTON_Y = -22;
    private static final int HOSTING_STATUS_Y = 12;
    private static final int COPY_ID_BUTTON_Y = 24;
    private static final int STOP_BUTTON_Y = 50;
    private static final int BACK_BUTTON_Y_HOSTING = 76;
    private static final int BACK_BUTTON_Y_IDLE = 24;

    // --- Change-password sub-view ---
    private static final int CPW_TITLE_Y = -50;
    private static final int CPW_BOX_Y = -20;
    private static final int CPW_CONFIRM_Y = 10;
    private static final int CPW_CANCEL_Y = 36;

    private final Screen parent;
    private boolean unlocked;
    private boolean changingPassword = false;
    private String status = "";

    private EditBox unlockBox;
    private EditBox descBox;
    private EditBox newPasswordBox;

    public AdminPanelScreen(Screen parent) {
        super(Component.literal("P2P Connect - Manage Server"));
        this.parent = parent;
        this.unlocked = !ClientConfig.hasAdminPassword();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (!unlocked) {
            buildLockedView(cx, cy);
        } else if (changingPassword) {
            buildChangePasswordView(cx, cy);
        } else {
            buildSettingsView(cx, cy);
        }
    }

    private void buildLockedView(int cx, int cy) {
        unlockBox = new EditBox(this.font, cx - 100, cy + LOCK_BOX_Y, 200, 20, Component.literal("Admin password"));
        this.addRenderableWidget(unlockBox);
        this.setInitialFocus(unlockBox);

        this.addRenderableWidget(Button.builder(Component.literal("Unlock"), b -> tryUnlock())
                .bounds(cx - 100, cy + LOCK_UNLOCK_Y, 200, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 100, cy + LOCK_BACK_Y, 200, 20)
                .build());
    }

    private void buildSettingsView(int cx, int cy) {
        descBox = new EditBox(this.font, cx - 100, cy + DESC_BOX_Y, 200, 20, Component.literal("Description"));
        descBox.setMaxLength(64);
        descBox.setValue(ClientConfig.serverDescription == null ? "" : ClientConfig.serverDescription);
        this.addRenderableWidget(descBox);

        this.addRenderableWidget(Button.builder(publicToggleLabel(), b -> {
                    ClientConfig.publicListingEnabled = !ClientConfig.publicListingEnabled;
                    rebuild();
                })
                .bounds(cx - 100, cy + PUBLIC_TOGGLE_Y, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.literal(ClientConfig.hasAdminPassword() ? "Change Admin Password" : "Set Admin Password"),
                        b -> { changingPassword = true; rebuild(); })
                .bounds(cx - 100, cy + CHANGE_PW_BUTTON_Y, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Save Settings"), b -> saveSettings())
                .bounds(cx - 100, cy + SAVE_BUTTON_Y, 200, 20)
                .build());

        boolean hosting = HostingUtil.isHosting();
        if (hosting) {
            this.addRenderableWidget(Button.builder(Component.literal("Copy Connection ID"), b -> copyId())
                    .bounds(cx - 100, cy + COPY_ID_BUTTON_Y, 200, 20)
                    .build());
            this.addRenderableWidget(Button.builder(Component.literal("Stop Hosting"), b -> {
                        HostingUtil.stopHosting();
                        status = "§7Hosting stopped.";
                        rebuild();
                    })
                    .bounds(cx - 100, cy + STOP_BUTTON_Y, 200, 20)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 100, cy + (hosting ? BACK_BUTTON_Y_HOSTING : BACK_BUTTON_Y_IDLE), 200, 20)
                .build());
    }

    private void buildChangePasswordView(int cx, int cy) {
        newPasswordBox = new EditBox(this.font, cx - 100, cy + CPW_BOX_Y, 200, 20, Component.literal("New password (blank = remove)"));
        this.addRenderableWidget(newPasswordBox);
        this.setInitialFocus(newPasswordBox);

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> {
                    ClientConfig.setAdminPassword(newPasswordBox.getValue());
                    ClientConfig.save();
                    changingPassword = false;
                    status = ClientConfig.hasAdminPassword() ? "§aAdmin password updated." : "§7Admin password removed.";
                    rebuild();
                })
                .bounds(cx - 100, cy + CPW_CONFIRM_Y, 200, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> { changingPassword = false; rebuild(); })
                .bounds(cx - 100, cy + CPW_CANCEL_Y, 200, 20)
                .build());
    }

    private Component publicToggleLabel() {
        return Component.literal("Public Listing: " + (ClientConfig.publicListingEnabled ? "ON" : "OFF"));
    }

    private void tryUnlock() {
        if (AdminAuth.matches(unlockBox.getValue(), ClientConfig.adminPasswordHash)) {
            unlocked = true;
            status = "";
            rebuild();
        } else {
            status = "§cWrong password.";
        }
    }

    private void saveSettings() {
        ClientConfig.serverDescription = descBox.getValue();
        ClientConfig.save();
        HostingUtil.refreshListingMetadata();
        status = "§aSettings saved" + (HostingUtil.isHosting() ? " and applied live." : ".");
    }

    private void copyId() {
        String id = HostingUtil.getActiveId();
        if (id == null) return;
        // KeyboardHandler.setClipboard(String) is a stable vanilla API, but if your
        // mapping version differs, check net.minecraft.client.KeyboardHandler.
        Minecraft.getInstance().keyboardHandler.setClipboard(id);
        status = "§aCopied " + id + " to clipboard.";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (!unlocked) {
            graphics.drawCenteredString(this.font, "Enter the admin password to manage this server", cx, cy + LOCK_TITLE_Y, 0xFFFFFF);
        } else if (changingPassword) {
            graphics.drawCenteredString(this.font, "Set a new admin password", cx, cy + CPW_TITLE_Y, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(this.font, "Manage Server", cx, cy + TITLE_Y, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "Description (shown in the public server list)", cx, cy + DESC_LABEL_Y, 0xAAAAAA);
            graphics.drawCenteredString(this.font, HostingUtil.isHosting()
                            ? "§aLive - ID: §f" + HostingUtil.getActiveId()
                            : "§7Not currently hosting.",
                    cx, cy + HOSTING_STATUS_Y, 0xFFFFFF);
        }

        if (!status.isEmpty()) {
            int statusY = !unlocked ? cy + LOCK_TITLE_Y + 14
                    : changingPassword ? cy + CPW_TITLE_Y + 14
                    : cy + TITLE_Y + 14;
            graphics.drawCenteredString(this.font, status, cx, statusY, 0xFFFF55);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
