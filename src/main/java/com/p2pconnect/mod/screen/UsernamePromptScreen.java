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
        super(Component.literal("P2P Connect - Username"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int boxWidth = 200;
        usernameBox = new EditBox(this.font, this.width / 2 - boxWidth / 2, this.height / 2 - 30, boxWidth, 20,
                Component.literal("Username"));
        usernameBox.setMaxLength(24);
        usernameBox.setValue(ClientConfig.username);
        usernameBox.setResponder(s -> confirmButton.active = isValid(s));
        // NOTE: this used to be registered twice (addWidget + addRenderableWidget),
        // which double-counted it in the screen's widget list and could make the
        // box render/handle input oddly. addRenderableWidget alone is enough - it
        // both renders the widget and wires up input handling.
        this.addRenderableWidget(usernameBox);
        this.setInitialFocus(usernameBox);

        confirmButton = Button.builder(Component.literal("Continue"), b -> confirm())
                .bounds(this.width / 2 - 100, this.height / 2, 200, 20)
                .build();
        confirmButton.active = isValid(usernameBox.getValue());
        this.addRenderableWidget(confirmButton);

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 26, 200, 20)
                .build());
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
        graphics.drawCenteredString(this.font, "Welcome to P2P Connect! Pick a username first:",
                this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
