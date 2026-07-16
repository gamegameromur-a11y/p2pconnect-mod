package com.p2pconnect.mod.util;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Stores the username, MQTT broker address, and hosting preferences in
 * config/p2pconnect.properties. A public, unowned test broker
 * (broker.hivemq.com) is used by default, so no extra setup is required -
 * you can point mqttBrokerUrl at your own (also free) broker if you want.
 */
public class ClientConfig {

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("p2pconnect.properties");
    private static final Properties PROPS = new Properties();

    public static String username = "";
    // Public HiveMQ test broker by default - point this at your own (still free)
    // broker if you'd rather not share the default one with every other user.
    public static String mqttBrokerUrl = com.p2pconnect.mod.network.MqttService.DEFAULT_BROKER;

    // --- Hosting / public server list preferences ---
    // SHA-256 hash only - the plaintext password is never written to disk.
    public static String adminPasswordHash = "";
    public static String serverDescription = "";
    public static boolean publicListingEnabled = false;

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var in = Files.newInputStream(CONFIG_PATH)) {
                    PROPS.load(in);
                }
                username = PROPS.getProperty("username", "");
                mqttBrokerUrl = PROPS.getProperty("mqttBrokerUrl", com.p2pconnect.mod.network.MqttService.DEFAULT_BROKER);
                adminPasswordHash = PROPS.getProperty("adminPasswordHash", "");
                serverDescription = PROPS.getProperty("serverDescription", "");
                publicListingEnabled = Boolean.parseBoolean(PROPS.getProperty("publicListingEnabled", "false"));
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
            PROPS.setProperty("adminPasswordHash", adminPasswordHash == null ? "" : adminPasswordHash);
            PROPS.setProperty("serverDescription", serverDescription == null ? "" : serverDescription);
            PROPS.setProperty("publicListingEnabled", String.valueOf(publicListingEnabled));
            try (var out = Files.newOutputStream(CONFIG_PATH)) {
                PROPS.store(out, "P2P Connect settings - username, MQTT broker address, hosting preferences");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasUsername() {
        return username != null && !username.isBlank();
    }

    public static boolean hasAdminPassword() {
        return adminPasswordHash != null && !adminPasswordHash.isEmpty();
    }

    /** Hashes and stores a new admin password. Pass an empty string to remove password protection. */
    public static void setAdminPassword(String plain) {
        adminPasswordHash = (plain == null || plain.isEmpty()) ? "" : AdminAuth.sha256Hex(plain);
    }
}
