package com.p2pconnect.mod.network;

import com.p2pconnect.mod.util.PendingState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * "Kabul Et" dendikten sonra kullanıcı dünya oluşturma ekranına yönlendirilir.
 * Dünya oluşup gerçekten oyun içine girildiğinde (mc.level != null, integrated
 * server hazır) bu sınıf bunu tespit edip otomatik olarak LAN + bore + MQTT
 * yayınını başlatır ve istek sahibine bağlantı bilgisini yollar.
 */
public class AutoHostTrigger {

    private boolean triggering = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PendingState.pendingAutoHostForRequester == null) return;
        if (triggering) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getSingleplayerServer() != null && mc.player != null) {
            triggering = true;
            String requester = PendingState.pendingAutoHostForRequester;
            PendingState.pendingAutoHostForRequester = null;

            HostingUtil.startHostingCurrentWorld(requester,
                    id -> triggering = false,
                    error -> {
                        triggering = false;
                        mc.player.displayClientMessage(Component.literal("§c[P2P Connect] Otomatik host başlatılamadı: " + error), false);
                    });
        }
    }
}
