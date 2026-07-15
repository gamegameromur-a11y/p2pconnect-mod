package com.p2pconnect.mod.network;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.util.ClientConfig;
import com.p2pconnect.mod.util.ModListUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;

/**
 * Şu an içinde bulunulan (singleplayer/integrated) dünyayı LAN'a açar, bore ile
 * dışarı tünelleyip herkese açık bir adres alır ve bunu MQTT üzerinden yayınlar.
 *
 * NOT: `publishServer` çağrısının imzası kullandığın Forge/MCP mapping sürümüne
 * göre ufak farklılık gösterebilir. Derlerken hata alırsan, vanilla
 * `OpenToLanScreen` sınıfının decompile edilmiş haline bak (ForgeGradle
 * `genClientSources`/`genIntellijRuns` sonrası) ve bu metodu ona göre uyarla.
 */
public class HostingUtil {

    /**
     * @param requesterUsername null ise "manuel ID paylaşımı" modudur (sadece ID gösterilir).
     *                          Dolu ise bir join isteğini kabul ettikten sonraki otomatik akıştır
     *                          (hosting hazır olunca requester'a MQTT ile cevap gönderilir).
     */
    public static void startHostingCurrentWorld(String requesterUsername, java.util.function.Consumer<String> onIdReady,
                                                  java.util.function.Consumer<String> onError) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() == null) {
            onError.accept("Şu an açık/tek kişilik bir dünyada değilsin.");
            return;
        }

        var server = mc.getSingleplayerServer();
        int localPort;
        try {
            if (!server.isPublished()) {
                localPort = HttpUtil.getAvailablePort();
                boolean ok = server.publishServer(GameType.SURVIVAL, false, localPort);
                if (!ok) {
                    onError.accept("Dünya LAN'a açılamadı.");
                    return;
                }
            } else {
                localPort = server.getPort();
            }
        } catch (Exception e) {
            onError.accept("LAN'a açma hatası: " + e.getMessage());
            return;
        }

        mc.player.displayClientMessage(Component.literal("§7[P2P Connect] Dünya LAN'a açıldı, bore ile dışarı tünelleniyor..."), false);

        P2PConnectMod.BORE.start(localPort, borePort -> {
            String id = "bore.pub:" + borePort;
            var modList = ModListUtil.getInstalledMods();

            P2PConnectMod.MQTT.publishHosting(ClientConfig.username, "bore.pub", borePort, modList);

            if (requesterUsername != null) {
                P2PConnectMod.MQTT.respondToRequest(requesterUsername, true, "bore.pub", borePort, modList);
            }

            mc.execute(() -> {
                mc.player.displayClientMessage(Component.literal("§a[P2P Connect] Yayında! Bağlantı ID'n: §f" + id), false);
                onIdReady.accept(id);
            });
        }, error -> mc.execute(() -> onError.accept(error)));
    }

    public static void stopHosting() {
        P2PConnectMod.BORE.stop();
        P2PConnectMod.MQTT.stopHosting(ClientConfig.username);
    }
}
