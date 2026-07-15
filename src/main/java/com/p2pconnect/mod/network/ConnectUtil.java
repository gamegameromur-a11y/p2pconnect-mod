package com.p2pconnect.mod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * NOT: ConnectScreen.startConnecting metodunun imzası Minecraft/Forge sürümüne
 * göre değişebilir (1.20.1'de aşağıdaki 5 parametreli hali geçerlidir; daha
 * yeni sürümlerde "quick play"/cookie parametreleri eklendi). Derleme hatası
 * alırsan IDE'de ConnectScreen sınıfına gidip doğru overload'u kullan.
 */
public class ConnectUtil {

    public static void connect(Screen currentScreen, String hostAndPort, String displayName) {
        Minecraft mc = Minecraft.getInstance();
        ServerAddress address = ServerAddress.parseString(hostAndPort);
        ServerData serverData = new ServerData(displayName == null ? hostAndPort : displayName, hostAndPort, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(currentScreen, mc, address, serverData, false);
    }
}
