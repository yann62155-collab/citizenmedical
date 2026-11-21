package fr.citizen.medical.client;

import fr.citizen.medical.net.SelfStatePayload;

/** Etat cote client. */
public final class ClientState {
    public static boolean doctor;
    public static SelfStatePayload self;
    /** Heure locale (ms) de reception de {@link #self}, pour le compte a rebours. */
    public static long selfAt;

    /** joueurs a terre (UUID) */
    public static final java.util.Set<java.util.UUID> downed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void reset() { doctor = false; self = null; selfAt = 0; downed.clear(); }

    public static boolean limping() { return self != null && self.wounded(); }

    public static boolean selfDowned() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && downed.contains(mc.player.getUUID());
    }

    private ClientState() { }
}
