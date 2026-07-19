package com.p2pconnect.mod.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.p2pconnect.mod.P2PConnectMod;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A simple matchmaking client running over a public, unowned MQTT broker
 * (default: HiveMQ's free public test broker). The publisher (whoever
 * distributes this mod) never has to run any server of their own.
 *
 * Honest security note: broker.hivemq.com is a shared, unauthenticated,
 * unencrypted public broker. Anyone can technically listen to any topic on
 * it. Treat usernames, descriptions, and "passwords" sent through it as
 * public information, not secrets. The username registry below is a
 * best-effort courtesy check, not a real accounts system - see its comment.
 *
 * Topic schema - everything under the "p2pconnect/" prefix:
 *   host/{username}          -> (retained) hosting info JSON: {host, port, modlist, ts}, or empty "" once hosting stops
 *   request/{targetUsername} -> (not retained) request JSON: {from, ts, password?}
 *   response/{fromUsername}  -> (not retained) response JSON: {status: accepted|denied, host, port, modlist}
 *   publicListing/{username} -> (retained) public server browser entry JSON:
 *                                {host, port, modlist, description, passwordProtected, online, ts}, or empty "" when fully delisted
 *   registry/{username}      -> (retained) username claim JSON: {passwordHash, registeredAt}
 *
 * You can point config/p2pconnect.properties at your own broker if you'd
 * rather not share the default one.
 *
 * Reliability note: MqttConnectOptions.setAutomaticReconnect(true) makes the
 * client reconnect on its own after a dropped connection, but with
 * setCleanSession(true) (used below) the broker forgets all subscriptions on
 * every reconnect - Paho does NOT resubscribe for you (see
 * https://github.com/eclipse-paho/paho.mqtt.java/issues/493). Without
 * handling that, a host whose connection blips would silently stop
 * receiving join requests with no visible error. MqttCallbackExtended's
 * connectComplete(reconnect, ...) is used below specifically to replay every
 * tracked subscription after a reconnect.
 *
 * "no NetworkModule installed for scheme tcp" history: this used to happen
 * on every connection attempt. The actual root cause was that Paho was being
 * relocated (org.eclipse.paho -> com.p2pconnect.shaded.paho) in build.gradle
 * to avoid clashing with other mods, but Paho's NetworkModuleService looks
 * up its TCP/SSL support via
 * ServiceLoader.load(NetworkModuleFactory.class, NetworkModuleService.class.getClassLoader())
 * - a META-INF/services file whose name AND contents both encode the
 * relocated package name, and that mapping didn't survive Shadow's
 * relocation + Forge's reobf step intact. The fix is in build.gradle: Paho
 * is no longer relocated. The context-classloader override still present in
 * connect() below turned out not to be the actual fix (Paho 1.2.5 doesn't
 * use the thread's context classloader at all - it uses its own class's
 * classloader) but is left in as a harmless defensive measure.
 */
public class MqttService {

    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    private static final String TOPIC_PREFIX = "p2pconnect/";

    private MqttClient client;
    private final Gson gson = new Gson();
    private final Map<String, IMqttMessageListener> subscriptions = new ConcurrentHashMap<>();
    private volatile Runnable onReconnected;

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /** Optional hook fired (on the MQTT client's own thread) whenever the client automatically reconnects after a drop. */
    public void setOnReconnected(Runnable callback) {
        this.onReconnected = callback;
    }

    public void connect(String brokerUrl, Runnable onConnected, Consumer<String> onError) {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(MqttService.class.getClassLoader());

            String clientId = "p2pconnect-" + Long.toHexString(System.nanoTime());
            client = new MqttClient(brokerUrl == null || brokerUrl.isBlank() ? DEFAULT_BROKER : brokerUrl,
                    clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);

            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    if (reconnect) {
                        P2PConnectMod.LOGGER.info("MQTT reconnected to " + serverURI + " - resubscribing to " + subscriptions.size() + " topic(s)");
                        for (var entry : subscriptions.entrySet()) {
                            try {
                                client.subscribe(entry.getKey(), 1, entry.getValue());
                            } catch (Exception e) {
                                P2PConnectMod.LOGGER.warn("Could not resubscribe to " + entry.getKey() + ": " + e.getMessage());
                            }
                        }
                        Runnable hook = onReconnected;
                        if (hook != null) hook.run();
                    }
                }
                @Override public void connectionLost(Throwable cause) {
                    P2PConnectMod.LOGGER.warn("MQTT connection lost, will attempt to reconnect automatically: " + cause.getMessage());
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    var listener = subscriptions.get(topic);
                    if (listener != null) {
                        try { listener.messageArrived(topic, message); } catch (Exception ignored) {}
                    }
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            client.connect(options);
            onConnected.run();
        } catch (Exception e) {
            // Log the full exception (with stack trace) to latest.log, not just the short message
            // shown in the UI - a truncated on-screen string isn't enough to diagnose a real bug.
            P2PConnectMod.LOGGER.error("MQTT connect() failed", e);
            onError.accept("Could not connect to the MQTT broker: " + e.getMessage());
        } finally {
            // Don't leave the calling thread (the Minecraft main thread) with a classloader it
            // didn't have before - only this connection setup needed the override.
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
        } catch (Exception ignored) {}
    }

    private void publish(String topic, JsonObject payload, boolean retained) {
        try {
            MqttMessage msg = new MqttMessage(payload == null ? new byte[0] : gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            msg.setRetained(retained);
            client.publish(TOPIC_PREFIX + topic, msg);
        } catch (Exception e) {
            P2PConnectMod.LOGGER.warn("MQTT publish error: " + e.getMessage());
        }
    }

    private void subscribe(String topic, Consumer<JsonObject> onMessage) {
        try {
            IMqttMessageListener listener = (t, message) -> {
                byte[] payload = message.getPayload();
                if (payload.length == 0) {
                    onMessage.accept(null);
                    return;
                }
                try {
                    JsonObject obj = gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
                    onMessage.accept(obj);
                } catch (Exception parseError) {
                    // A malformed message on a shared public broker shouldn't break this listener for
                    // every message after it - just log and move on.
                    P2PConnectMod.LOGGER.warn("Ignoring malformed MQTT message on " + t + ": " + parseError.getMessage());
                }
            };
            subscriptions.put(TOPIC_PREFIX + topic, listener);
            client.subscribe(TOPIC_PREFIX + topic, 1, listener);
        } catch (Exception e) {
            P2PConnectMod.LOGGER.warn("MQTT subscribe error: " + e.getMessage());
        }
    }

    /**
     * Same as {@link #subscribe}, but for single-level wildcard subscriptions
     * (e.g. "publicListing/+") where the caller needs to know which concrete
     * topic segment a message came from. The last path segment (after the
     * final "/") is passed as the first callback argument.
     */
    private void subscribeWildcard(String topicPattern, BiConsumer<String, JsonObject> onMessage) {
        try {
            IMqttMessageListener listener = (t, message) -> {
                String key = t.substring(t.lastIndexOf('/') + 1);
                byte[] payload = message.getPayload();
                if (payload.length == 0) {
                    onMessage.accept(key, null);
                    return;
                }
                try {
                    JsonObject obj = gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
                    onMessage.accept(key, obj);
                } catch (Exception parseError) {
                    P2PConnectMod.LOGGER.warn("Ignoring malformed MQTT message on " + t + ": " + parseError.getMessage());
                }
            };
            subscriptions.put(TOPIC_PREFIX + topicPattern, listener);
            client.subscribe(TOPIC_PREFIX + topicPattern, 1, listener);
        } catch (Exception e) {
            P2PConnectMod.LOGGER.warn("MQTT subscribe (wildcard) error: " + e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            client.unsubscribe(TOPIC_PREFIX + topic);
            subscriptions.remove(TOPIC_PREFIX + topic);
        } catch (Exception ignored) {}
    }

    // ---- High-level helper functions ----

    /** Publishes as a host; retained, so anyone who subscribes later sees it immediately. */
    public void publishHosting(String username, String boreHost, int borePort, List<String> modList) {
        JsonObject obj = new JsonObject();
        obj.addProperty("host", boreHost);
        obj.addProperty("port", borePort);
        obj.addProperty("ts", System.currentTimeMillis());
        var mods = new com.google.gson.JsonArray();
        modList.forEach(mods::add);
        obj.add("modlist", mods);
        publish("host/" + username, obj, true);
    }

    public void stopHosting(String username) {
        publish("host/" + username, null, true); // empty retained message clears the previous one
    }

    /** Subscribes to "request/{myUsername}" and listens for incoming join requests. */
    public void listenForRequests(String myUsername, Consumer<JsonObject> onRequest) {
        subscribe("request/" + myUsername, onRequest);
    }

    public void sendJoinRequest(String targetUsername, String fromUsername) {
        sendJoinRequest(targetUsername, fromUsername, null);
    }

    /** @param password optional - included only when non-empty, e.g. when joining a password-protected public listing. */
    public void sendJoinRequest(String targetUsername, String fromUsername, String password) {
        JsonObject obj = new JsonObject();
        obj.addProperty("from", fromUsername);
        obj.addProperty("ts", System.currentTimeMillis());
        if (password != null && !password.isEmpty()) {
            obj.addProperty("password", password);
        }
        publish("request/" + targetUsername, obj, false);
    }

    public void listenForResponse(String myUsername, Consumer<JsonObject> onResponse) {
        subscribe("response/" + myUsername, onResponse);
    }

    public void respondToRequest(String fromUsername, boolean accept, String boreHost, int borePort, List<String> modList) {
        JsonObject obj = new JsonObject();
        obj.addProperty("status", accept ? "accepted" : "denied");
        if (accept) {
            obj.addProperty("host", boreHost);
            obj.addProperty("port", borePort);
            var mods = new com.google.gson.JsonArray();
            modList.forEach(mods::add);
            obj.add("modlist", mods);
        }
        publish("response/" + fromUsername, obj, false);
    }

    // ---- Public server browser ----

    public void publishPublicListing(String username, String boreHost, int borePort, List<String> modList,
                                      String description, boolean passwordProtected) {
        JsonObject obj = new JsonObject();
        obj.addProperty("host", boreHost);
        obj.addProperty("port", borePort);
        obj.addProperty("description", description == null ? "" : description);
        obj.addProperty("passwordProtected", passwordProtected);
        obj.addProperty("online", true);
        obj.addProperty("ts", System.currentTimeMillis());
        var mods = new com.google.gson.JsonArray();
        modList.forEach(mods::add);
        obj.add("modlist", mods);
        publish("publicListing/" + username, obj, true);
    }

    /**
     * Re-publishes the same listing but flagged offline instead of removing
     * it, for cases where the host went away without explicitly stopping
     * (closed the game, crashed, etc.) - keeps it visible in the browser
     * ("saved" servers), since re-hosting the same world is quick. Does
     * nothing if there was no previous listing to begin with (last-known
     * fields aren't known here, so the caller must have a listing to mark).
     */
    public void markListingOffline(String username, String boreHost, int borePort, List<String> modList,
                                    String description, boolean passwordProtected) {
        JsonObject obj = new JsonObject();
        obj.addProperty("host", boreHost);
        obj.addProperty("port", borePort);
        obj.addProperty("description", description == null ? "" : description);
        obj.addProperty("passwordProtected", passwordProtected);
        obj.addProperty("online", false);
        obj.addProperty("ts", System.currentTimeMillis());
        var mods = new com.google.gson.JsonArray();
        modList.forEach(mods::add);
        obj.add("modlist", mods);
        publish("publicListing/" + username, obj, true);
    }

    /** Fully removes the listing - used when the host explicitly stops broadcasting. */
    public void clearPublicListing(String username) {
        publish("publicListing/" + username, null, true);
    }

    /** Listens for every public listing (subscribes to "publicListing/+"). Callback receives (username, payload-or-null). */
    public void listenPublicListings(BiConsumer<String, JsonObject> onListing) {
        subscribeWildcard("publicListing/+", onListing);
    }

    public void unsubscribePublicListings() {
        unsubscribe("publicListing/+");
    }

    // ---- Username registry ----
    // Honest scope note: there's no central server here, just a shared public
    // broker with retained messages - this is a best-effort claim check, not
    // a real accounts system. Two people claiming the exact same free
    // username in the exact same instant could theoretically both succeed
    // (no atomic compare-and-set is possible over plain MQTT). In normal use
    // this is not something you'll run into.

    /**
     * Looks up "registry/{username}" and reports what's there.
     * @param onResult called with the retained registration JSON ({passwordHash, registeredAt}),
     *                  or null if the username is unclaimed. Always called eventually (after a
     *                  short timeout if nothing is retained for that topic).
     */
    public void checkUsernameRegistration(String username, Consumer<JsonObject> onResult) {
        String topic = "registry/" + username;
        Object lock = new Object();
        boolean[] responded = {false};

        subscribe(topic, payload -> {
            synchronized (lock) {
                if (responded[0]) return;
                responded[0] = true;
            }
            unsubscribe(topic);
            onResult.accept(payload);
        });

        Thread timeoutThread = new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            synchronized (lock) {
                if (responded[0]) return;
                responded[0] = true;
            }
            unsubscribe(topic);
            onResult.accept(null);
        }, "username-check-timeout");
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    /** Claims (or re-confirms) a username with a password hash. Retained, so it acts as the registry entry. */
    public void registerUsername(String username, String passwordHash) {
        JsonObject obj = new JsonObject();
        obj.addProperty("passwordHash", passwordHash);
        obj.addProperty("registeredAt", System.currentTimeMillis());
        publish("registry/" + username, obj, true);
    }
}
