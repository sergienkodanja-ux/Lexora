package com.lexoravisauls.client.utils;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;

public final class DiscordRPCManager {
    private static final String APPLICATION_ID = "1488844440285216769";
    private static final long UPDATE_COOLDOWN_MS = 5000L;

    private static DiscordRPC lib;
    private static Thread callbackThread;

    private static boolean running = false;
    private static boolean available = false;
    private static long startTimestamp = 0L;

    private static String lastDetails = "";
    private static String lastState = "";
    private static long lastUpdateTime = 0L;

    private DiscordRPCManager() {}

    public static synchronized void init() {
        start();
    }

    public static synchronized void start() {
        if (running) return;

        try {
            lib = DiscordRPC.INSTANCE;

            DiscordEventHandlers handlers = new DiscordEventHandlers();

            handlers.ready = user -> {
                available = true;
                System.out.println("[Lexora RPC] Ready: " + user.username);
            };

            handlers.disconnected = (code, message) -> {
                available = false;
                System.out.println("[Lexora RPC] Disconnected: " + code + " / " + message);
            };

            handlers.errored = (code, message) -> {
                available = false;
                System.out.println("[Lexora RPC] Error: " + code + " / " + message);
            };

            lib.Discord_Initialize(APPLICATION_ID, handlers, false, "");
            running = true;
            available = true;
            startTimestamp = System.currentTimeMillis() / 1000L;

            callbackThread = new Thread(() -> {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (lib != null) {
                            lib.Discord_RunCallbacks();
                        }
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable t) {
                        System.out.println("[Lexora RPC] Callback thread crashed");
                        t.printStackTrace();
                        break;
                    }
                }
            }, "Lexora-Discord-RPC");

            callbackThread.setDaemon(true);
            callbackThread.start();

            forceUpdate("Главное меню", "Lexora Visuals 1.21.4");
            System.out.println("[Lexora RPC] Init called");
        } catch (Throwable t) {
            running = false;
            available = false;
            lib = null;
            System.out.println("[Lexora RPC] Failed to initialize");
            t.printStackTrace();
        }
    }

    public static synchronized void update(String details, String state) {
        updatePresence(details, state, false);
    }

    public static synchronized void forceUpdate(String details, String state) {
        updatePresence(details, state, true);
    }

    private static void updatePresence(String details, String state, boolean force) {
        if (!running || lib == null) return;

        details = details == null ? "" : details;
        state = state == null ? "" : state;

        long now = System.currentTimeMillis();
        boolean sameText = details.equals(lastDetails) && state.equals(lastState);
        boolean cooldownPassed = (now - lastUpdateTime) >= UPDATE_COOLDOWN_MS;

        if (sameText) return;
        if (!force && !cooldownPassed) return;

        try {
            DiscordRichPresence presence = new DiscordRichPresence();
            presence.startTimestamp = startTimestamp;
            presence.details = details;
            presence.state = state;
            presence.largeImageKey = "lexora_logo";
            presence.largeImageText = "Lexora Client";

            lib.Discord_UpdatePresence(presence);

            lastDetails = details;
            lastState = state;
            lastUpdateTime = now;
        } catch (Throwable t) {
            System.out.println("[Lexora RPC] Update failed");
            t.printStackTrace();
        }
    }

    public static synchronized void clearPresence() {
        if (!running || lib == null) return;

        try {
            lib.Discord_ClearPresence();
            lastDetails = "";
            lastState = "";
            lastUpdateTime = 0L;
        } catch (Throwable t) {
            System.out.println("[Lexora RPC] Clear presence failed");
            t.printStackTrace();
        }
    }

    public static synchronized void stop() {
        running = false;
        available = false;

        if (callbackThread != null) {
            callbackThread.interrupt();
            callbackThread = null;
        }

        if (lib != null) {
            try {
                lib.Discord_ClearPresence();
                lib.Discord_Shutdown();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        lib = null;
        lastDetails = "";
        lastState = "";
        lastUpdateTime = 0L;
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isAvailable() {
        return available;
    }
}