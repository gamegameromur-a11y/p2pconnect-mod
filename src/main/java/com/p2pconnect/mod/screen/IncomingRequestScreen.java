package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.util.PendingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.Collections;

/**
 * Bir kullanıcıdan "birlikte oynayalım" isteği geldiğinde gösterilen ekran.
 * "Kabul Et" -> dünya oluşturma ekranına yönlendirir; dünya oluşturulup oyuna
 * girildiğinde AutoHostTrigger otomatik olarak hosting'i başlatır ve istek
 * sahibine bağlantı bilgisini gönderir.
 */
public class IncomingRequestScreen extends Screen {

    private final String fromUsername;

    public IncomingRequestScreen(String fromUsername) {
        super(Component.literal("P2P Connect - Gelen İstek"));
        this.fromUsername = fromUsername;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Kabul Et"), b -> accept())
                .bounds(this.width / 2 - 105, this.height / 2 + 10, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Reddet"), b -> deny())
                .bounds(this.width / 2 + 5, this.height / 2 + 10, 100, 20)
                .build());
    }

    private void accept() {
        PendingState.pendingAutoHostForRequester = fromUsername;
        Minecraft mc = Minecraft.getInstance();
        // Vanilla "Yeni Dünya Oluştur" ekranını açıyoruz; CreateWorldScreen.openFresh
        // 1.20.1'de void döner ve ekranı zaten kendi içinde mc.setScreen ile açar,
        // bu yüzden dönüş değerini tekrar setScreen'e sarmıyoruz. Dünya oluşturulup
        // oyuna girildiği an AutoHostTrigger devreye girecek.
        CreateWorldScreen.openFresh(mc, null);
    }

    private void deny() {
        P2PConnectMod.MQTT.respondToRequest(fromUsername, false, null, 0, Collections.emptyList());
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§e" + fromUsername + " §fseninle oynamak istiyor!",
                this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Kabul edersen yeni bir dünya oluşturacaksın ve o sana katılacak.",
                this.width / 2, this.height / 2 - 12, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
