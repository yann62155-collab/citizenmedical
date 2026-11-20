package fr.citizen.medical;

import fr.citizen.medical.net.DoctorStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Previent le client quand le joueur devient / n'est plus medecin (active la touche Alt). */
public final class DoctorSync {
    private static final Map<UUID, Boolean> SENT = new ConcurrentHashMap<>();

    private DoctorSync() { }

    public static void send(ServerPlayer p) {
        boolean doc = Hospital.isDoctor(p);
        Boolean prev = SENT.put(p.getUUID(), doc);
        if (prev != null && prev == doc) return;
        if (prev != null || doc) {
            if (doc) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "✚ Vous êtes médecin de l'hôpital : touche Alt puis clic droit sur un joueur pour l'examiner et le soigner (ou accroupi + clic droit main vide).")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            else if (prev != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("✚ Vous n'êtes plus médecin de l'hôpital.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (p.connection.hasChannel(DoctorStatePayload.TYPE)) PacketDistributor.sendToPlayer(p, new DoctorStatePayload(doc));
    }

    public static void forget(UUID id) { SENT.remove(id); }

    public static void clearAll() { SENT.clear(); }
}
