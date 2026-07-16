package com.p2pconnect.mod.network;

import com.p2pconnect.mod.util.PendingState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * After accepting a request (or using "Start Broadcast" without a world
 * already open), the player is sent to pick or create a world. Once a world
 * actually finishes loading (mc.level != null, integrated server ready),
 * this class detects it and automatically starts the LAN + bore + MQTT
 * broadcast - responding to the requester over MQTT if there was one.
 */
public class AutoHostTrigger {

    private boolean triggering = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (triggering) return;

        boolean hasRequester = PendingState.pendingAutoHostForRequester != null;
        boolean manualPending = PendingState.pendingManualHost;
        if (!hasRequester && !manualPending) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getSingleplayerServer() != null && mc.player != null) {
            triggering = true;
            String requester = PendingState.pendingAutoHostForRequester;
            PendingState.pendingAutoHostForRequester = null;
            PendingState.pendingManualHost = false;

            HostingUtil.startHostingCurrentWorld(requester,
                    id -> triggering = false,
                    error -> {
                        triggering = false;
                        mc.player.displayClientMessage(Component.literal("§c[P2P Connect] Could not start automatic hosting: " + error), false);
                    });
        }
    }
}
