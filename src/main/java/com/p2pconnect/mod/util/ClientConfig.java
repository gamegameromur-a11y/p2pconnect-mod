package com.p2pconnect.mod.util;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Kullanıcı adı ve MQTT broker bilgilerini config/p2pconnect.properties
 * dosyasında saklar. Varsayılan olarak herkese açık HiveMQ test broker'ı
 * (broker.hivemq.com) kullanılır, hiçbir ek kurulum gerekmez - kullanıcı
 * isterse mqttBrokerUrl değerini kendi broker'ına çevirebilir (README.md
 * "Gizlilik / Güvenilirlik Notu" bölümüne bak).
 */
public class ClientConfig {

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("p2pconnect.properties");
    private static final Properties PROPS = new Properties();

    public static String username = "";
    // Varsayılan olarak herkese açık HiveMQ test broker'ı kullanılır - kullanıcı
    // isterse kendi (yine ücretsiz) broker adresini buraya yazabilir.
    public static String mqttBrokerUrl = com.p2pconnect.mod.network.MqttService.DEFAULT_BROKER;

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var in = Files.newInputStream(CONFIG_PATH)) {
                    PROPS.load(in);
                }
                username = PROPS.getProperty("username", "");
                mqttBrokerUrl = PROPS.getProperty("mqttBrokerUrl", com.p2pconnect.mod.network.MqttService.DEFAULT_BROKER);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            PROPS.setProperty("username", username);
            PROPS.setProperty("mqttBrokerUrl", mqttBrokerUrl);
            try (var out = Files.newOutputStream(CONFIG_PATH)) {
                PROPS.store(out, "P2P Connect ayarlari - kullanici adi ve MQTT broker adresi");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasUsername() {
        return username != null && !username.isBlank();
    }
}
