package fr.citizen.medical;

import fr.citizen.medical.net.DownedListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Joueur "a terre" : au lieu de mourir, il reste allonge pendant un compte a rebours.
 * Un autre joueur peut le porter (accroupi + clic droit main vide) jusqu'a un lit d'hopital ;
 * sinon, a la fin du compte a rebours, il est transporte sur un lit d'hopital. Dans les deux cas
 * il se releve avec 100 % de vie mais boite (blessure grave) jusqu'au soin d'un medecin.
 */
public final class Downed {
    private static final String TAG = "citizenmedical_downed";
    private static final ResourceLocation STILL_ID = ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "downed");
    private static final ResourceLocation NOJUMP_ID = ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "downed_jump");
    /** porteur -> ticks passes accroupi (poser le blesse apres 1 s) */
    private static final Map<UUID, Integer> SNEAK = new ConcurrentHashMap<>();

    private Downed() { }

    public static boolean isDowned(Entity e) {
        return e instanceof ServerPlayer p && p.getPersistentData().contains(TAG);
    }

    public static int secondsLeft(ServerPlayer p) { return p.getPersistentData().getInt(TAG); }

    // ------------------------------------------------------------------ mise a terre

    /** Appele a la mort. @return true si la mort est remplacee par "a terre" */
    public static boolean tryDown(ServerPlayer p, DamageSource src) {
        MedicalConfig.Data d = MedicalConfig.get();
        if (!d.aTerreActif || p.isCreative() || p.isSpectator()) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // /kill, vide : vraie mort
        if (isDowned(p)) return false;
        p.setHealth(1f);
        p.getPersistentData().putInt(TAG, Math.max(5, d.secondesATerre));
        p.stopRiding();
        p.ejectPassengers();
        p.clearFire();
        p.setSprinting(false);
        applyStill(p, true);
        p.setForcedPose(Pose.SWIMMING);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        p.sendSystemMessage(Component.literal("✚ Vous êtes à terre ! Un joueur peut vous porter jusqu'à un lit d'hôpital, sinon vous y serez transporté dans "
                + Wounds.duration(secondsLeft(p)) + ".").withStyle(ChatFormatting.RED));
        String name = p.getGameProfile().getName();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || o.level() != p.level() || o.distanceTo(p) > 32) continue;
            o.sendSystemMessage(Component.literal("✚ " + name + " est à terre ! Accroupi + clic droit (main vide) pour le porter jusqu'à un lit d'hôpital.")
                    .withStyle(ChatFormatting.GOLD));
        }
        broadcast(p.server);
        return true;
    }

    /** Fin de l'etat a terre (sans teleportation). */
    public static void end(ServerPlayer p) {
        boolean was = p.getPersistentData().contains(TAG);
        p.getPersistentData().remove(TAG);
        applyStill(p, false);
        if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
        if (was) broadcast(p.server);
    }

    // ------------------------------------------------------------------ ticks

    /** Chaque tick : position allongee, immobilite, porteurs qui posent leur blesse. */
    public static void tickFast(ServerPlayer p) {
        if (isDowned(p)) {
            boolean carried = p.isPassenger();
            Pose want = carried ? null : Pose.SWIMMING;
            if (p.getForcedPose() != want) p.setForcedPose(want);
            applyStill(p, true);
            if (p.isSprinting()) p.setSprinting(false);
        }
        ServerPlayer carried = carriedBy(p);
        if (carried != null) {
            if (p.isShiftKeyDown()) {
                int n = SNEAK.merge(p.getUUID(), 1, Integer::sum);
                if (n == 20) drop(p, true);
            } else {
                SNEAK.remove(p.getUUID());
            }
        } else {
            SNEAK.remove(p.getUUID());
        }
    }

    /** Chaque seconde : compte a rebours (en pause pendant le portage). */
    public static void tickSecond(ServerPlayer p) {
        if (!isDowned(p)) return;
        boolean carried = p.getVehicle() instanceof ServerPlayer;
        int left = secondsLeft(p);
        if (!carried) left--;
        if (left <= 0) {
            sendToHospital(p, "Vous avez été transporté à l'hôpital.");
            return;
        }
        p.getPersistentData().putInt(TAG, left);
        p.displayClientMessage(Component.literal(carried
                ? "✚ À terre — vous êtes porté (compte à rebours en pause : " + Wounds.duration(left) + ")"
                : "✚ À terre — transport à l'hôpital dans " + Wounds.duration(left)).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ portage

    /** Le blesse a terre porte par {@code carrier}, ou null. */
    public static ServerPlayer carriedBy(ServerPlayer carrier) {
        for (Entity e : carrier.getPassengers()) if (e instanceof ServerPlayer sp && isDowned(sp)) return sp;
        return null;
    }

    public static boolean carry(ServerPlayer carrier, ServerPlayer target) {
        if (isDowned(carrier) || !isDowned(target) || target.isPassenger() || carrier.isPassenger() || !carrier.getPassengers().isEmpty()) return false;
        target.setForcedPose(null);
        if (!target.startRiding(carrier, true)) return false;
        carrier.sendSystemMessage(Component.literal("✚ Vous portez " + target.getGameProfile().getName()
                + ". Clic droit sur un lit d'hôpital pour l'y déposer · restez accroupi 1 s pour le poser au sol.").withStyle(ChatFormatting.AQUA));
        target.sendSystemMessage(Component.literal("✚ " + carrier.getGameProfile().getName() + " vous porte.").withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static void drop(ServerPlayer carrier, boolean tell) {
        ServerPlayer t = carriedBy(carrier);
        SNEAK.remove(carrier.getUUID());
        if (t == null) return;
        t.stopRiding();
        t.teleportTo(carrier.getX(), carrier.getY(), carrier.getZ());
        if (tell) carrier.displayClientMessage(Component.literal("Vous avez posé " + t.getGameProfile().getName() + " au sol.").withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ reveil

    /** Le blesse est depose sur un lit d'hopital : il se releve, 100 % de vie, boite jusqu'au soin. */
    public static void reviveOnBed(ServerPlayer p, ServerLevel level, BlockPos bed, String msg) {
        p.stopRiding();
        end(p);
        p.teleportTo(level, bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(Math.max(p.getFoodData().getFoodLevel(), 6));
        level.playSound(null, bed, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 0.9f);
        p.sendSystemMessage(Component.literal("✚ " + msg + " Votre santé est restaurée, mais vous boitez : faites-vous soigner par un médecin.")
                .withStyle(ChatFormatting.GREEN));
        Wounds.severe(p);
    }

    /** Fin du compte a rebours (ou reconnexion a terre) : lit d'hopital le plus proche, sinon point d'hopital, sinon spawn. */
    public static void sendToHospital(ServerPlayer p, String msg) {
        MinecraftServer server = p.server;
        BedRegistry.Spot spot = BedRegistry.get(server).nearest(server, p);
        if (spot != null) {
            reviveOnBed(p, spot.level(), spot.pos(), msg);
            return;
        }
        MedicalConfig.Data d = MedicalConfig.get();
        ServerLevel level = null;
        BlockPos pos = null;
        if (d.hopitalDefini) {
            ResourceLocation dim = ResourceLocation.tryParse(d.hopitalDimension);
            if (dim != null) level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level != null) pos = BlockPos.containing(d.hopitalX, d.hopitalY, d.hopitalZ);
        }
        if (level == null) {
            level = server.overworld();
            pos = level.getSharedSpawnPos();
        }
        p.stopRiding();
        end(p);
        p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.setHealth(p.getMaxHealth());
        p.sendSystemMessage(Component.literal("✚ " + msg + " Votre santé est restaurée, mais vous boitez : faites-vous soigner par un médecin.")
                .withStyle(ChatFormatting.GREEN));
        Wounds.severe(p);
        if (spot == null) CitizenMedical.LOGGER.info("Citizen Medical : aucun lit d'hôpital posé, {} envoyé au point d'hôpital / spawn", p.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ outils

    private static void applyStill(ServerPlayer p, boolean on) {
        mod(p.getAttribute(Attributes.MOVEMENT_SPEED), STILL_ID, on);
        mod(p.getAttribute(Attributes.JUMP_STRENGTH), NOJUMP_ID, on);
    }

    private static void mod(AttributeInstance inst, ResourceLocation id, boolean on) {
        if (inst == null) return;
        boolean has = inst.hasModifier(id);
        if (on && !has) inst.addTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!on && has) inst.removeModifier(id);
    }

    /** Liste des joueurs a terre envoyee a tous les clients (position allongee). */
    public static void broadcast(MinecraftServer server) {
        if (server == null) return;
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (isDowned(o)) ids.add(o.getUUID());
        DownedListPayload pl = new DownedListPayload(ids);
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o.connection.hasChannel(DownedListPayload.TYPE)) PacketDistributor.sendToPlayer(o, pl);
        }
    }

    public static boolean inLevel(Level a, Level b) { return a == b; }
}
