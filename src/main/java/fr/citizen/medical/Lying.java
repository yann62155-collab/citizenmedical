package fr.citizen.medical;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allonge sur un lit d'hopital SANS passer par le sommeil vanilla : pas de point de reapparition enregistre,
 * pas de saut de nuit, pas de "lit trop loin / monstres a proximite". Simple pose allongee forcee.
 * Accroupi (Maj) = se lever.
 */
public final class Lying {
    private static final ResourceLocation STILL_ID = ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "lying");
    private record Spot(ServerLevel level, BlockPos bed, int age) { }
    private static final Map<UUID, Spot> LYING = new ConcurrentHashMap<>();

    private Lying() { }

    public static boolean isLying(ServerPlayer p) { return LYING.containsKey(p.getUUID()); }

    public static java.util.Set<UUID> ids() { return LYING.keySet(); }

    public static void start(ServerPlayer p, ServerLevel level, BlockPos bed) {
        p.stopRiding();
        p.setSprinting(false);
        p.teleportTo(level, bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5, p.getYRot(), p.getXRot());
        LYING.put(p.getUUID(), new Spot(level, bed.immutable(), 0));
        p.setForcedPose(Pose.SLEEPING);
        still(p, true);
        p.displayClientMessage(Component.literal("✚ Allongé sur le lit d'hôpital · accroupissez-vous (Maj) pour vous lever.").withStyle(ChatFormatting.AQUA), true);
        Downed.broadcast(p.server);
    }

    public static void stop(ServerPlayer p) {
        Spot s = LYING.remove(p.getUUID());
        if (s == null) return;
        if (p.getForcedPose() == Pose.SLEEPING) p.setForcedPose(null);
        still(p, false);
        if (p.isAlive() && p.level() == s.level()) p.teleportTo(s.bed().getX() + 0.5, s.bed().getY() + 0.6, s.bed().getZ() + 0.5);
        Downed.broadcast(p.server);
    }

    /** Chaque tick : se lever en s'accroupissant, ou si le lit a disparu / le joueur a ete deplace. */
    public static void tick(ServerPlayer p) {
        Spot s = LYING.get(p.getUUID());
        if (s == null) return;
        int age = s.age() + 1;
        LYING.put(p.getUUID(), new Spot(s.level(), s.bed(), age));
        boolean gone = p.level() != s.level() || !s.level().getBlockState(s.bed()).is(CitizenMedical.HOSPITAL_BED.get())
                || p.blockPosition().distSqr(s.bed()) > 4 || !p.isAlive() || Downed.isDowned(p);
        if (gone || (age > 10 && p.isShiftKeyDown())) {
            stop(p);
            return;
        }
        if (p.getForcedPose() != Pose.SLEEPING) p.setForcedPose(Pose.SLEEPING);
    }

    public static void forget(UUID id) { LYING.remove(id); }

    private static void still(ServerPlayer p, boolean on) {
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        boolean has = inst.hasModifier(STILL_ID);
        if (on && !has) inst.addTransientModifier(new AttributeModifier(STILL_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!on && has) inst.removeModifier(STILL_ID);
    }
}
