package fr.citizen.medical.client;

import fr.citizen.medical.net.DoctorStatePayload;
import fr.citizen.medical.net.SelfStatePayload;
import fr.citizen.medical.net.WoundInfoPayload;
import net.minecraft.client.Minecraft;

/** Recepteurs cote client (appeles seulement sur le client). */
public final class ClientHooks {
    private ClientHooks() { }

    public static void onDoctor(DoctorStatePayload p) { ClientState.doctor = p.doctor(); }

    public static void onDowned(fr.citizen.medical.net.DownedListPayload p) {
        ClientState.downed.clear();
        ClientState.downed.addAll(p.players());
        ClientState.lying.clear();
        ClientState.lying.addAll(p.lying());
    }

    public static void onSelf(SelfStatePayload p) {
        ClientState.self = p.wounded() ? p : null;
        ClientState.selfAt = System.currentTimeMillis();
    }

    public static void onInfo(WoundInfoPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WoundScreen s && s.target().equals(p.target())) s.update(p);
        else mc.setScreen(new WoundScreen(p));
    }
}
