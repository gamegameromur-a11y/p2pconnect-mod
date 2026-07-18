package com.p2pconnect.mod.network;

import com.p2pconnect.mod.util.PendingState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Two jobs, both driven off the client tick:
 *
 * 1. After accepting a request (or using "Start Broadcast" without a world
 *    already open), the player is sent to pick or create a world. Once a
 *    world actually finishes loading (mc.level != null, integrated server
 *    ready), this detects it and automatically starts the LAN + bore + MQTT
 *    broadcast - responding to the requester over MQTT if there was one.
 *
 * 2. If the player leaves the world they were hosting (Save & Quit, crash to
 *    menu, etc.) without using "Stop Broadcast" first, this notices the
 *    world is gone and cleans up - marking the public listing offline
 *    (rather than removing it) instead of leaving it stuck showing a "live"
 *    server that no longer exists. (HostingUtil's own crash-recovery handles
 *    bore dying while the world is still open; this handles the world
 *    itself going away.)
 */
public class AutoHostTrigger {

    private boolean triggering = false;
    private boolean wasHostingLastTick = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null && mc.getSingleplayerServer() != null;

        if (wasHostingLastTick && HostingUtil.isHosting() && !inWorld) {
            HostingUtil.handleWorldClosedWhileHosting();
        }
        wasHostingLastTick = HostingUtil.isHosting();

        if (triggering) return;

        boolean hasRequester = PendingState.pendingAutoHostForRequester != null;
        boolean manualPending = PendingState.pendingManualHost;
        if (!hasRequester && !manualPending) return;

        if (inWorld && mc.player != null) {
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
