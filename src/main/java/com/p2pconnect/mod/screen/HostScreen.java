package com.p2pconnect.mod.screen;

import com.p2pconnect.mod.network.HostingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Manuel hosting ekranı: kullanıcı şu an içinde bulunduğu (singleplayer)
 * dünyayı elle LAN + bore ile dışarı açmak istediğinde buradan başlatır.
 * Otomatik akış (bir isteği kabul ettikten sonra) için AutoHostTrigger +
 * HostingUtil.startHostingCurrentWorld zaten kullanılıyor; bu ekran aynı
 * metodu requesterUsername = null ile çağırıp sadece bağlantı ID'sini
 * gösterir.
 */
public class HostScreen extends Screen {

    private final Screen parent;
    private String status = "Bir dünyanın içindeyken \"Yayını Başlat\"a bas.";
    private boolean hosting = false;

    public HostScreen(Screen parent) {
        super(Component.literal("P2P Connect - Sunucu Aç"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Yayını Başlat"), b -> startHosting())
                .bounds(cx - 100, this.height / 2 - 10, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Yayını Durdur"), b -> stopHosting())
                .bounds(cx - 100, this.height / 2 + 16, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Geri"), b -> onClose())
                .bounds(cx - 100, this.height / 2 + 42, 200, 20)
                .build());
    }

    private void startHosting() {
        if (hosting) {
            status = "§7Zaten yayındasın.";
            return;
        }
        status = "§7Dünya LAN'a açılıp tünelleniyor...";
        HostingUtil.startHostingCurrentWorld(null,
                id -> {
                    hosting = true;
                    status = "§aYayında! Bağlantı ID'n: §f" + id;
                },
                error -> status = "§cHost başlatılamadı: " + error);
    }

    private void stopHosting() {
        HostingUtil.stopHosting();
        hosting = false;
        status = "§7Yayın durduruldu.";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "Sunucu Aç (Host)", this.width / 2, this.height / 2 - 45, 0xFFFFFF);
        graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 30, 0xFFFF55);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
