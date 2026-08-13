package com.lexoravisauls.client.party;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lexora Party System — хранит состояние пати на клиенте.
 * Не делает запросов сам — только хранит данные.
 */
public final class LexoraPartyManager {

    // ---- Текущее состояние пати ----
    public static volatile String partyCode   = null;  // null если не в пати
    public static volatile boolean isOwner    = false;
    public static volatile String ownerUuid   = null;

    // ---- Участники (обновляются через heartbeat) ----
    public static final List<PartyMember> members = new CopyOnWriteArrayList<>();

    // ---- Входящие запросы (для владельца) ----
    public static final List<PendingRequest> pendingRequests = new CopyOnWriteArrayList<>();

    // ---- Ожидание ответа (для заявителя) ----
    public static volatile boolean waitingForResponse = false;
    public static volatile String waitingCode = null;

    // ---- Dynamic Island уведомление ----
    public static volatile PartyInviteNotif activeInviteNotif = null;

    // ---- Флаг — открыть вкладку пати в GUI ----
    public static volatile boolean shouldOpenPartyTab = false;

    private LexoraPartyManager() {}

    // =============================================

    public static boolean inParty() {
        return partyCode != null;
    }

    public static void clear() {
        partyCode = null;
        isOwner   = false;
        ownerUuid = null;
        members.clear();
        pendingRequests.clear();
        waitingForResponse = false;
        waitingCode        = null;
        activeInviteNotif  = null;
    }

    // =============================================
    // Вложенные data-классы
    // =============================================

    public static class PartyMember {
        public final String uuid;
        public final String name;
        public volatile String server;
        public volatile boolean online;

        public PartyMember(String uuid, String name, String server, boolean online) {
            this.uuid   = uuid;
            this.name   = name;
            this.server = server;
            this.online = online;
        }
    }

    public static class PendingRequest {
        public final String uuid;
        public final String name;
        public final long   receivedAt;

        public PendingRequest(String uuid, String name) {
            this.uuid       = uuid;
            this.name       = name;
            this.receivedAt = System.currentTimeMillis();
        }
    }

    /** Данные для уведомления в Dynamic Island */
    public static class PartyInviteNotif {
        public final String requesterName;
        public final String requesterUuid;
        public final String partyCode;
        public final long   createdAt;

        // Колбэки — вызываются из GUI при нажатии кнопок
        public Runnable onAccept;
        public Runnable onDecline;

        public PartyInviteNotif(String name, String uuid, String code,
                                Runnable onAccept, Runnable onDecline) {
            this.requesterName = name;
            this.requesterUuid = uuid;
            this.partyCode     = code;
            this.createdAt     = System.currentTimeMillis();
            this.onAccept      = onAccept;
            this.onDecline     = onDecline;
        }

        /** Сколько секунд осталось до авто-отклонения (показывается в таймере) */
        public float secondsLeft() {
            return Math.max(0, 30_000 - (System.currentTimeMillis() - createdAt)) / 1000f;
        }

        public boolean expired() {
            return System.currentTimeMillis() - createdAt > 30_000;
        }
    }
}