package com.p2pconnect.mod.util;

/**
 * When a request is accepted, or when the "Start Broadcast" button is used
 * without an existing world open, the player is sent to the vanilla world
 * list/creation flow. Once a world actually finishes loading (detected by
 * AutoHostTrigger), hosting needs to start automatically. These simple
 * static fields carry that intent across screens.
 */
public class PendingState {
    /** Username whose accepted request we owe a hosting response to. Null = no pending auto-accept host. */
    public static volatile String pendingAutoHostForRequester = null;

    /** True if the manual "Start Broadcast" flow sent the player to pick/create a world and is waiting for it to load. */
    public static volatile boolean pendingManualHost = false;
}
