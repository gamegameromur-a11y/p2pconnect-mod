package com.p2pconnect.mod;

import com.p2pconnect.mod.network.BoreManager;
import com.p2pconnect.mod.network.MqttService;
import com.p2pconnect.mod.screen.ScreenInjector;
import com.p2pconnect.mod.util.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(P2PConnectMod.MOD_ID)
public class P2PConnectMod {

    public static final String MOD_ID = "p2pconnect";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // Tüm mod boyunca tek bir MQTT servis ve bore yöneticisi kullanılıyor.
    // MQTT herkese açık, kimseye ait olmayan bir broker'a bağlanır - yayıncının
    // (bu modu dağıtan kişinin) hiçbir sunucu barındırmasına gerek yoktur.
    public static final MqttService MQTT = new MqttService();
    public static final BoreManager BORE = new BoreManager();

    public P2PConnectMod() {
        ClientConfig.load();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);

        // Multiplayer ekranına butonumuzu enjekte eden sınıfı ve otomatik host
        // tetikleyicisini forge event bus'a kaydet
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new ScreenInjector());
            MinecraftForge.EVENT_BUS.register(new com.p2pconnect.mod.network.AutoHostTrigger());
        }
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("P2P Connect istemci tarafı hazırlanıyor...");
        // MQTT bağlantısı ihtiyaç anında (kullanıcı P2P menüsünü açtığında) kurulacak,
        // gereksiz yere oyun açılışında bağlantı denemesi yapmıyoruz.
    }
}
