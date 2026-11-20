package fr.citizen.medical.client;

import fr.citizen.medical.net.SelfStatePayload;

/** Etat cote client. */
public final class ClientState {
    public static boolean doctor;
    public static SelfStatePayload self;
    /** Heure locale (ms) de reception de {@link #self}, pour le compte a rebours. */
    public static long selfAt;

    public static void reset() { doctor = false; self = null; selfAt = 0; }

    public static boolean limping() { return self != null && self.wounded(); }

    private ClientState() { }
}
