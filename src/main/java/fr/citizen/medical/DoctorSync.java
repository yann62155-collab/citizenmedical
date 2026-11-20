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
        // En mode /cadmin ON, la touche Alt sert au curseur admin : pas de mode medecin.
        boolean doc = Hospital.isDoctor(p) && !adminOnDuty(p);
        Boolean prev = SENT.put(p.getUUID(), doc);
        if (prev != null && prev == doc) return;
        if (prev != null || doc) {
            if (doc && Hospital.isDoctor(p)) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "✚ Vous êtes médecin de l'hôpital : touche Alt puis clic droit sur un joueur pour l'examiner et le soigner (ou accroupi + clic droit main vide).")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            else if (prev != null && adminOnDuty(p)) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("✚ Mode admin : la touche Alt sert au curseur admin (mode médecin en pause).")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            else if (prev != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("✚ Vous n'êtes plus médecin de l'hôpital.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (p.connection.hasChannel(DoctorStatePayload.TYPE)) PacketDistributor.sendToPlayer(p, new DoctorStatePayload(doc));
    }

    /** Citizen Admin : admin EN SERVICE (/cadmin on) ? Lecture par reflexion, false si le mod est absent. */
    public static boolean adminOnDuty(ServerPlayer p) {
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("citizenadmin")) return false;
            Class<?> c = Class.forName("fr.citizen.admin.AdminService");
            return Boolean.TRUE.equals(c.getMethod("isActiveAdmin", ServerPlayer.class).invoke(null, p));
        } catch (Throwable t) {
            return false;
        }
    }

    public static void forget(UUID id) { SENT.remove(id); }

    public static void clearAll() { SENT.clear(); }
}
