package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.util.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UsernamePromptScreen extends Screen {

    private final Screen parent;
    private EditBox usernameBox;
    private Button confirmButton;

    public UsernamePromptScreen(Screen parent) {
        super(Component.literal("P2P Connect - Kullanıcı Adı"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int boxWidth = 200;
        usernameBox = new EditBox(this.font, this.width / 2 - boxWidth / 2, this.height / 2 - 30, boxWidth, 20,
                Component.literal("Kullanıcı adı"));
        usernameBox.setMaxLength(24);
        usernameBox.setValue(ClientConfig.username);
        usernameBox.setResponder(s -> confirmButton.active = isValid(s));
        this.addWidget(usernameBox);
        this.setInitialFocus(usernameBox);

        confirmButton = Button.builder(Component.literal("Devam Et"), b -> confirm())
                .bounds(this.width / 2 - 100, this.height / 2, 200, 20)
                .build();
        confirmButton.active = isValid(usernameBox.getValue());
        this.addRenderableWidget(confirmButton);

        this.addRenderableWidget(Button.builder(Component.literal("İptal"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 26, 200, 20)
                .build());

        this.addRenderableWidget(usernameBox);
    }

    private boolean isValid(String s) {
        return s != null && s.trim().length() >= 3 && s.trim().length() <= 24 && s.trim().matches("[A-Za-z0-9_]+");
    }

    private void confirm() {
        ClientConfig.username = usernameBox.getValue().trim();
        ClientConfig.save();
        Minecraft.getInstance().setScreen(new P2PMainScreen(parent));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "P2P Connect'e hoş geldin! Önce bir kullanıcı adı seç:",
                this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
