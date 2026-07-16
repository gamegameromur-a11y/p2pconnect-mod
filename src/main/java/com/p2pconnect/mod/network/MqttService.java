package com.p2pconnect.mod.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.p2pconnect.mod.P2PConnectMod;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * public information, not secrets.
 *
 * Topic schema - everything under the "p2pconnect/" prefix:
 *   host/{username}          -> (retained) hosting info JSON: {host, port, modlist, ts}, or empty "" once hosting stops
 *   request/{targetUsername} -> (not retained) request JSON: {from, ts, password?}
 *   response/{fromUsername}  -> (not retained) response JSON: {status: accepted|denied, host, port, modlist}
 *   publicListing/{username} -> (retained) public server browser entry JSON:
 *                                {host, port, modlist, description, passwordProtected, ts}, or empty "" when delisted
 *
 * You can point config/p2pconnect.properties at your own broker if you'd
 * rather not share the default one.
 */
public class MqttService {

    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    private static final String TOPIC_PREFIX = "p2pconnect/";

    private MqttClient client;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<String, IMqttMessageListener> subscriptions = new ConcurrentHashMap<>();

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void connect(String brokerUrl, Runnable onConnected, Consumer<String> onError) {
        try {
            String clientId = "p2pconnect-" + Long.toHexString(System.nanoTime());
            client = new MqttClient(brokerUrl == null || brokerUrl.isBlank() ? DEFAULT_BROKER : brokerUrl,
                    clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);

            client.setCallback(new MqttCallback() {
                @Override public void connectionLost(Throwable cause) {
                    P2PConnectMod.LOGGER.warn("MQTT connection lost: " + cause.getMessage());
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
            onError.accept("Could not connect to the MQTT broker: " + e.getMessage());
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
                JsonObject obj = gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
                onMessage.accept(obj);
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
                JsonObject obj = gson.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
                onMessage.accept(key, obj);
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
        obj.addProperty("ts", System.currentTimeMillis());
        var mods = new com.google.gson.JsonArray();
        modList.forEach(mods::add);
        obj.add("modlist", mods);
        publish("publicListing/" + username, obj, true);
    }

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
}
