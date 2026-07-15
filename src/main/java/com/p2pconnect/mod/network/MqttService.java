package com.p2pconnect.mod.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.p2pconnect.mod.P2PConnectMod;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Kimseye ait olmayan, herkese açık bir MQTT broker'ı (varsayılan: HiveMQ'nun
 * ücretsiz halka açık test broker'ı) üzerinden çalışan basit bir eşleştirme
 * (matchmaking) istemcisi. Yayıncının (bu modu dağıtan kişinin) hiçbir sunucu
 * çalıştırmasına gerek yoktur.
 *
 * Konu (topic) şeması - tamamı "p2pconnect/" öneki ile:
 *   host/{kullaniciAdi}      -> (retained) hosting bilgisi JSON: {host, port, modlist, ts} ya da bos "" (hosting durdurulunca)
 *   request/{hedefKullanici} -> (retained degil) istek JSON: {from, ts}
 *   response/{gonderenKul.}  -> (retained degil) cevap JSON: {status: accepted|denied, host, port, modlist}
 *
 * Kullanıcı isterse config/p2pconnect.properties içinden kendi broker adresini
 * girip varsayılanı değiştirebilir.
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
                    P2PConnectMod.LOGGER.warn("MQTT bağlantısı koptu: " + cause.getMessage());
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
            onError.accept("MQTT broker'a bağlanılamadı: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
        } catch (Exception ignored) {}
    }

    private void publish(String topic, JsonObject payload, boolean retained) {
        try {
            MqttMessage msg = new MqttMessage(payload == null ? new byte[0] : gson.toJson(payload).getBytes());
            msg.setQos(1);
            msg.setRetained(retained);
            client.publish(TOPIC_PREFIX + topic, msg);
        } catch (Exception e) {
            P2PConnectMod.LOGGER.warn("MQTT publish hatası: " + e.getMessage());
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
                JsonObject obj = gson.fromJson(new String(payload), JsonObject.class);
                onMessage.accept(obj);
            };
            subscriptions.put(TOPIC_PREFIX + topic, listener);
            client.subscribe(TOPIC_PREFIX + topic, 1, listener);
        } catch (Exception e) {
            P2PConnectMod.LOGGER.warn("MQTT subscribe hatası: " + e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            client.unsubscribe(TOPIC_PREFIX + topic);
            subscriptions.remove(TOPIC_PREFIX + topic);
        } catch (Exception ignored) {}
    }

    // ---- Üst düzey yardımcı fonksiyonlar ----

    /** Host olarak yayınlanır; retained mesaj olduğu için sonradan bağlanan biri de anında görür. */
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
        publish("host/" + username, null, true); // retained boş mesaj = önceki retained'i temizler
    }

    /** targetUsername'in "request/{myUsername}" konusuna abone olup gelen istekleri dinler. */
    public void listenForRequests(String myUsername, Consumer<JsonObject> onRequest) {
        subscribe("request/" + myUsername, onRequest);
    }

    public void sendJoinRequest(String targetUsername, String fromUsername) {
        JsonObject obj = new JsonObject();
        obj.addProperty("from", fromUsername);
        obj.addProperty("ts", System.currentTimeMillis());
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
}
