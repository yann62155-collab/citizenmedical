package fr.citizen.medical;

import fr.citizen.medical.net.SelfStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Etat de blessure d'un joueur, stocke dans ses donnees persistantes (survit a la deconnexion, efface a la mort).
 * Les compteurs n'avancent que quand le joueur est connecte.
 */
public final class Wounds {
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "bleeding"));
    private static final ResourceLocation LIMP_ID = ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "limp");
    private static final String TAG = "citizenmedical";

    /** Vue figee de l'etat d'un joueur. */
    public record State(boolean wounded, String cause, boolean bandaged, boolean treated, int ageSec, int bleedClock) {
        public static final State NONE = new State(false, "", false, false, 0, 0);

        public boolean bleeding() { return wounded && !treated; }

        public boolean limping() { return wounded; }

        /** Secondes avant la prochaine perte de vie (-1 si plus de saignement). */
        public int nextBleedSec() {
            if (!bleeding()) return -1;
            return Math.max(0, interval(bandaged) - bleedClock);
        }

        /** Secondes minimum de boiterie restantes (la boiterie dure aussi tant que le medecin n'a pas soigne). */
        public int limpMinLeftSec() {
            if (!wounded) return 0;
            return Math.max(0, MedicalConfig.get().minutesBoiterieMin * 60 - ageSec);
        }
    }

    private Wounds() { }

    static int interval(boolean bandaged) {
        MedicalConfig.Data d = MedicalConfig.get();
        return Math.max(1, (bandaged ? d.minutesAvecBandage : d.minutesSansBandage) * 60);
    }

    // ------------------------------------------------------------------ lecture / ecriture

    public static State get(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) return State.NONE;
        CompoundTag t = root.getCompound(TAG);
        if (!t.getBoolean("wounded")) return State.NONE;
        return new State(true, t.getString("cause"), t.getBoolean("bandaged"), t.getBoolean("treated"),
                t.getInt("age"), t.getInt("bleedClock"));
    }

    private static void put(ServerPlayer p, State s) {
        if (!s.wounded()) {
            p.getPersistentData().remove(TAG);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putBoolean("wounded", true);
        t.putString("cause", s.cause());
        t.putBoolean("bandaged", s.bandaged());
        t.putBoolean("treated", s.treated());
        t.putInt("age", s.ageSec());
        t.putInt("bleedClock", s.bleedClock());
        p.getPersistentData().put(TAG, t);
    }

    // ------------------------------------------------------------------ actions

    /** Nouvelle blessure. Ignoree si le joueur saigne deja (pas de cumul). @return true si appliquee */
    public static boolean wound(ServerPlayer p, String cause) {
        State cur = get(p);
        if (cur.bleeding()) return false;
        put(p, new State(true, cause, false, false, 0, 0));
        applyLimp(p, true);
        MedicalConfig.Data d = MedicalConfig.get();
        p.sendSystemMessage(Component.literal("✚ Vous êtes blessé : " + cause + ". Vous boitez et perdez "
                + hearts(d.perteSansBandage) + " toutes les " + d.minutesSansBandage + " min.").withStyle(ChatFormatting.RED));
        p.sendSystemMessage(Component.literal("   Posez un bandage (clic droit) puis allez voir un médecin de l'hôpital.").withStyle(ChatFormatting.GRAY));
        p.displayClientMessage(Component.literal("✚ Blessé : " + cause).withStyle(ChatFormatting.RED), true);
        sync(p);
        return true;
    }

    /** Pose d'un bandage sur {@code target} par {@code by} (lui-meme ou un autre joueur). */
    public static boolean bandage(ServerPlayer target, ServerPlayer by) {
        State s = get(target);
        boolean self = target == by;
        if (!s.wounded()) {
            by.displayClientMessage(Component.literal(self ? "Vous n'êtes pas blessé." : target.getGameProfile().getName() + " n'est pas blessé.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (s.treated()) {
            by.displayClientMessage(Component.literal("Cette blessure a déjà été soignée par un médecin.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (s.bandaged()) {
            by.displayClientMessage(Component.literal("La blessure est déjà bandée.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        // on garde la progression, sans depasser le nouvel intervalle
        put(target, new State(true, s.cause(), true, false, s.ageSec(), Math.min(s.bleedClock(), interval(true) - 1)));
        MedicalConfig.Data d = MedicalConfig.get();
        target.level().playSound(null, target.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
        target.sendSystemMessage(Component.literal("✚ Blessure bandée" + (self ? "" : " par " + by.getGameProfile().getName())
                + " : vous ne perdez plus que " + hearts(d.perteAvecBandage) + " toutes les " + d.minutesAvecBandage
                + " min. Allez voir un médecin pour être soigné.").withStyle(ChatFormatting.YELLOW));
        if (!self) by.sendSystemMessage(Component.literal("✚ Vous avez bandé la blessure de " + target.getGameProfile().getName() + ".").withStyle(ChatFormatting.YELLOW));
        sync(target);
        return true;
    }

    /** Soin par un medecin : le saignement s'arrete ; la boiterie s'arrete une fois les 10 min passees. */
    public static boolean treat(ServerPlayer target, ServerPlayer doctor) {
        State s = get(target);
        String dn = doctor == null ? "un médecin" : "le médecin " + doctor.getGameProfile().getName();
        if (!s.wounded() || s.treated()) {
            if (doctor != null) doctor.displayClientMessage(Component.literal(target.getGameProfile().getName()
                    + (s.wounded() ? " a déjà été soigné." : " n'a aucune blessure.")).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        State n = new State(true, s.cause(), s.bandaged(), true, s.ageSec(), 0);
        put(target, n);
        target.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        int left = n.limpMinLeftSec();
        if (left <= 0) {
            clear(target);
            target.sendSystemMessage(Component.literal("✚ Vous avez été soigné par " + dn + ". Vous ne boitez plus.").withStyle(ChatFormatting.GREEN));
        } else {
            target.sendSystemMessage(Component.literal("✚ Vous avez été soigné par " + dn + ". Le saignement est arrêté ; vous boiterez encore "
                    + duration(left) + ".").withStyle(ChatFormatting.GREEN));
            sync(target);
        }
        if (doctor != null && doctor != target) {
            String hop = Hospital.hospitalOf(doctor);
            doctor.sendSystemMessage(Component.literal("✚ Vous avez soigné " + target.getGameProfile().getName()
                    + (hop.isEmpty() ? "" : " (" + hop + ")") + "." + (left > 0 ? " Boiterie encore " + duration(left) + "." : "")).withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    /** Efface tout (guerison complete, mort, commande admin). */
    public static void clear(ServerPlayer p) {
        put(p, State.NONE);
        applyLimp(p, false);
        sync(p);
    }

    // ------------------------------------------------------------------ chaque seconde

    public static void tickSecond(ServerPlayer p) {
        State s = get(p);
        if (!s.wounded()) {
            applyLimp(p, false);
            return;
        }
        int age = s.ageSec() + 1;
        int clock = s.bleedClock();
        if (s.bleeding()) {
            clock++;
            if (clock >= interval(s.bandaged())) {
                clock = 0;
                MedicalConfig.Data d = MedicalConfig.get();
                bleed(p, s.bandaged() ? d.perteAvecBandage : d.perteSansBandage);
                if (!p.isAlive()) return; // mort : l'etat est efface au respawn
            }
        }
        State n = new State(true, s.cause(), s.bandaged(), s.treated(), age, clock);
        if (n.treated() && n.limpMinLeftSec() <= 0) {
            clear(p);
            p.sendSystemMessage(Component.literal("✚ Votre blessure est guérie : vous ne boitez plus.").withStyle(ChatFormatting.GREEN));
            return;
        }
        put(p, n);
        applyLimp(p, true);
        if (age % 5 == 0) sync(p);
    }

    private static void bleed(ServerPlayer p, double amount) {
        if (p.isCreative() || p.isSpectator() || amount <= 0) return;
        float dmg = (float) amount;
        if (!MedicalConfig.get().saignementPeutTuer) {
            dmg = Math.min(dmg, p.getHealth() - 1f);
            if (dmg <= 0) return;
        }
        DamageSource src = new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLEEDING));
        p.invulnerableTime = 0;
        p.hurt(src, dmg);
        p.displayClientMessage(Component.literal("✚ Vous saignez… (-" + hearts(amount) + ")").withStyle(ChatFormatting.DARK_RED), true);
    }

    // ------------------------------------------------------------------ boiterie

    public static void applyLimp(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.hasModifier(LIMP_ID);
        if (on && !has) {
            speed.addTransientModifier(new AttributeModifier(LIMP_ID, -Math.max(0, Math.min(0.9, MedicalConfig.get().ralentissementBoiterie)),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && has) {
            speed.removeModifier(LIMP_ID);
        }
        if (on && p.isSprinting()) p.setSprinting(false);
    }

    // ------------------------------------------------------------------ client / textes

    public static void sync(ServerPlayer p) {
        if (!p.connection.hasChannel(SelfStatePayload.TYPE)) return;
        State s = get(p);
        PacketDistributor.sendToPlayer(p, new SelfStatePayload(s.wounded(), s.cause(), s.bandaged(), s.treated(),
                s.ageSec(), s.nextBleedSec(), s.limpMinLeftSec()));
    }

    public static String hearts(double hp) {
        double h = hp / 2.0;
        if (h == 0.5) return "½ ❤";
        if (h == Math.rint(h)) return (int) h + " ❤";
        return String.format(java.util.Locale.ROOT, "%.1f ❤", h);
    }

    public static String duration(int sec) {
        int m = sec / 60, s = sec % 60;
        return m > 0 ? m + " min " + (s < 10 ? "0" : "") + s + " s" : s + " s";
    }
}
