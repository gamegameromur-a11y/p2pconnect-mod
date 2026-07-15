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

    private final Screen parent;
    private EditBox usernameBox;
    private EditBox idBox;
    private String status = "";
    private boolean waitingForResponse = false;

    public JoinScreen(Screen parent) {
        super(Component.literal("P2P Connect - Bağlan"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // --- Kullanıcı adı ile istek gönderme ---
        usernameBox = new EditBox(this.font, cx - 100, this.height / 2 - 55, 200, 20, Component.literal("Arkadaşının kullanıcı adı"));
        this.addRenderableWidget(usernameBox);

        this.addRenderableWidget(Button.builder(Component.literal("İstek Gönder"), b -> sendRequest())
                .bounds(cx - 100, this.height / 2 - 30, 200, 20)
                .build());

        // --- ID ile doğrudan bağlanma ---
        idBox = new EditBox(this.font, cx - 100, this.height / 2 + 20, 200, 20, Component.literal("bore.pub:12345"));
        this.addRenderableWidget(idBox);

        this.addRenderableWidget(Button.builder(Component.literal("ID ile Bağlan"), b -> connectById())
                .bounds(cx - 100, this.height / 2 + 45, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Geri"), b -> onClose())
                .bounds(cx - 100, this.height / 2 + 75, 200, 20)
                .build());
    }

    private void sendRequest() {
        String target = usernameBox.getValue().trim();
        if (target.isEmpty() || target.equalsIgnoreCase(ClientConfig.username)) {
            status = "§cGeçerli bir kullanıcı adı yaz.";
            return;
        }
        if (!P2PConnectMod.MQTT.isConnected()) {
            status = "§cHenüz broker'a bağlı değilsin, bir saniye bekleyip tekrar dene.";
            return;
        }

        waitingForResponse = true;
        status = "§7İstek gönderildi, " + target + "'in cevabı bekleniyor...";

        P2PConnectMod.MQTT.listenForResponse(ClientConfig.username, response -> {
            waitingForResponse = false;
            if (response == null) return;
            String st = response.has("status") ? response.get("status").getAsString() : "denied";
            Minecraft.getInstance().execute(() -> {
                if (st.equals("accepted")) {
                    handleAccepted(target, response);
                } else {
                    status = "§c" + target + " isteği reddetti.";
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
            ConnectUtil.connect(parent, address, target + "'in dünyası");
        } else {
            StringBuilder msg = new StringBuilder();
            if (!diff.missingOnMySide.isEmpty())
                msg.append("Eksik modların: ").append(String.join(", ", diff.missingOnMySide)).append(". ");
            if (!diff.versionMismatch.isEmpty())
                msg.append("Versiyon uyuşmazlığı: ").append(String.join(", ", diff.versionMismatch)).append(".");

            Minecraft.getInstance().setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            ConnectUtil.connect(parent, address, target + "'in dünyası");
                        } else {
                            Minecraft.getInstance().setScreen(this);
                        }
                    },
                    Component.literal("Mod listesi uyuşmuyor"),
                    Component.literal(msg.toString() + " Yine de bağlanmak istiyor musun?")
            ));
        }
    }

    private void connectById() {
        String id = idBox.getValue().trim();
        if (!id.contains(":")) {
            status = "§cGeçersiz ID. Örnek: bore.pub:12345";
            return;
        }
        ConnectUtil.connect(parent, id, "P2P Sunucu");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "Kullanıcı Adı ile İstek Gönder", this.width / 2, this.height / 2 - 68, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "— ya da —", this.width / 2, this.height / 2 - 3, 0x888888);
        graphics.drawCenteredString(this.font, "Doğrudan ID ile Bağlan", this.width / 2, this.height / 2 + 7, 0xFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 42, 0xFFFF55);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
