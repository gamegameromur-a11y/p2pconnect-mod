package com.p2pconnect.mod.network;

import com.p2pconnect.mod.P2PConnectMod;
import com.p2pconnect.mod.util.ClientConfig;
import com.p2pconnect.mod.util.ModListUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;

/**
 * Opens the current (singleplayer/integrated) world to LAN, tunnels it out
 * with bore to get a publicly reachable address, and publishes that over
 * MQTT.
 *
 * NOTE: the exact signature of `publishServer` can differ slightly depending
 * on your Forge/MCP mapping version. If this fails to compile, check the
 * decompiled vanilla `OpenToLanScreen` class (after ForgeGradle's
 * `genClientSources`/`genIntellijRuns`) and adapt this method to match.
 */
public class HostingUtil {

    private static volatile int activeBorePort = -1;

    public static boolean isHosting() {
        return activeBorePort > 0 && P2PConnectMod.BORE.isRunning();
    }

    public static int getActiveBorePort() {
        return activeBorePort;
    }

    /** Returns e.g. "bore.pub:23456", or null if not currently hosting. */
    public static String getActiveId() {
        return isHosting() ? "bore.pub:" + activeBorePort : null;
    }

    /**
     * @param requesterUsername null for the manual "just show me the ID" flow.
     *                          Non-null right after accepting a join request
     *                          (the requester gets an MQTT response once hosting is ready).
     */
    public static void startHostingCurrentWorld(String requesterUsername, java.util.function.Consumer<String> onIdReady,
                                                  java.util.function.Consumer<String> onError) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() == null) {
            onError.accept("You're not currently in a singleplayer world.");
            return;
        }

        var server = mc.getSingleplayerServer();
        int localPort;
        try {
            if (!server.isPublished()) {
                localPort = HttpUtil.getAvailablePort();
                boolean ok = server.publishServer(GameType.SURVIVAL, false, localPort);
                if (!ok) {
                    onError.accept("Could not open the world to LAN.");
                    return;
                }
            } else {
                localPort = server.getPort();
            }
        } catch (Exception e) {
            onError.accept("Error opening to LAN: " + e.getMessage());
            return;
        }

        mc.player.displayClientMessage(Component.literal("§7[P2P Connect] World opened to LAN, tunnelling out with bore..."), false);

        // NOTE: everything inside this callback runs on bore's own background
        // reader thread, NOT the Minecraft client thread - that's why the
        // UI-facing bits below are wrapped in mc.execute(...).
        P2PConnectMod.BORE.start(localPort, borePort -> {
            activeBorePort = borePort;
            String id = "bore.pub:" + borePort;
            var modList = ModListUtil.getInstalledMods();

            P2PConnectMod.MQTT.publishHosting(ClientConfig.username, "bore.pub", borePort, modList);

            if (ClientConfig.publicListingEnabled) {
                P2PConnectMod.MQTT.publishPublicListing(ClientConfig.username, "bore.pub", borePort, modList,
                        ClientConfig.serverDescription, ClientConfig.hasAdminPassword());
            }

            if (requesterUsername != null) {
                P2PConnectMod.MQTT.respondToRequest(requesterUsername, true, "bore.pub", borePort, modList);
            }

            mc.execute(() -> {
                mc.player.displayClientMessage(Component.literal("§a[P2P Connect] Live! Your connection ID: §f" + id), false);
                onIdReady.accept(id);
            });
        }, error -> mc.execute(() -> onError.accept(error)));
    }

    public static void stopHosting() {
        activeBorePort = -1;
        P2PConnectMod.BORE.stop();
        P2PConnectMod.MQTT.stopHosting(ClientConfig.username);
        P2PConnectMod.MQTT.clearPublicListing(ClientConfig.username);
    }

    /**
     * Re-publishes the current hosting/public-listing metadata (description,
     * public visibility, password-protected flag) without restarting bore or
     * re-opening the world to LAN. Used by the "Manage Server" panel so
     * settings changes apply live. No-op if not currently hosting.
     */
    public static void refreshListingMetadata() {
        if (!isHosting()) return;
        var modList = ModListUtil.getInstalledMods();
        P2PConnectMod.MQTT.publishHosting(ClientConfig.username, "bore.pub", activeBorePort, modList);
        if (ClientConfig.publicListingEnabled) {
            P2PConnectMod.MQTT.publishPublicListing(ClientConfig.username, "bore.pub", activeBorePort, modList,
                    ClientConfig.serverDescription, ClientConfig.hasAdminPassword());
        } else {
            P2PConnectMod.MQTT.clearPublicListing(ClientConfig.username);
        }
    }
}
