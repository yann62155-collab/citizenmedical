package fr.citizen.medical;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class MedicalEvents {
    private static int ticks;

    private MedicalEvents() { }

    public static void register(IEventBus bus) {
        bus.addListener(RegisterCommandsEvent.class, e -> MedicalCommands.register(e.getDispatcher()));
        bus.addListener(LivingDamageEvent.Post.class, MedicalEvents::onDamage);
        bus.addListener(ServerTickEvent.Post.class, MedicalEvents::onTick);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Wounds.applyLimp(p, Wounds.get(p).wounded());
                Wounds.sync(p);
                Hospital.refresh(p.getUUID()).thenRun(() -> p.getServer().execute(() -> DoctorSync.send(p)));
                DoctorSync.send(p);
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            Hospital.forget(e.getEntity().getUUID());
            DoctorSync.forget(e.getEntity().getUUID());
        });
        // A la mort, la blessure disparait.
        bus.addListener(PlayerEvent.Clone.class, e -> {
            if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer p) Wounds.clear(p);
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) { Wounds.applyLimp(p, Wounds.get(p).wounded()); Wounds.sync(p); }
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) Wounds.applyLimp(p, Wounds.get(p).wounded());
        });
        bus.addListener(PlayerInteractEvent.EntityInteract.class, MedicalEvents::onInteract);
        // /cadmin on|off : on remet a jour la touche Alt tout de suite (apres execution de la commande)
        bus.addListener(net.neoforged.neoforge.event.CommandEvent.class, e -> {
            String cmd = e.getParseResults().getReader().getString();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.toLowerCase(java.util.Locale.ROOT).startsWith("cadmin")) return;
            ServerPlayer p = e.getParseResults().getContext().getSource().getPlayer();
            if (p != null && p.getServer() != null) p.getServer().execute(() -> DoctorSync.send(p));
        });
        bus.addListener(ServerStoppingEvent.class, e -> { Hospital.clearAll(); DoctorSync.clearAll(); });
    }

    // ------------------------------------------------------------------ apparition des blessures

    private static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        DamageSource src = e.getSource();
        if (src.is(Wounds.BLEEDING)) return;
        float dmg = e.getNewDamage();
        MedicalConfig.Data d = MedicalConfig.get();
        String cause = causeOf(src, dmg, d);
        if (cause == null) return;
        if (p.getRandom().nextDouble() >= d.chanceBlessure) return;
        Wounds.wound(p, cause);
    }

    /** Cause lisible si les degats peuvent blesser, sinon null. */
    private static String causeOf(DamageSource src, float dmg, MedicalConfig.Data d) {
        if (src.is(DamageTypeTags.IS_FALL)) return dmg >= d.degatsMinChute ? "Entorse (chute)" : null;
        if (dmg < d.degatsMinCoup) return null;
        if (src.is(DamageTypeTags.IS_EXPLOSION)) return "Éclats et brûlures (explosion)";
        if (src.is(DamageTypeTags.IS_PROJECTILE)) {
            Entity shooter = src.getEntity();
            return shooter instanceof Player sp ? "Plaie par flèche (tir de " + sp.getGameProfile().getName() + ")" : "Plaie par projectile";
        }
        Entity attacker = src.getEntity();
        if (attacker instanceof Player sp) return "Plaie (coup de " + sp.getGameProfile().getName() + ")";
        if (attacker != null) return "Morsure / griffure (" + attacker.getName().getString() + ")";
        if (src.is(DamageTypes.CACTUS) || src.is(DamageTypes.SWEET_BERRY_BUSH)) return "Coupure";
        return null;
    }

    // ------------------------------------------------------------------ tick

    private static void onTick(ServerTickEvent.Post e) {
        if (++ticks % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            Wounds.tickSecond(p);
        }
        if (ticks % 200 == 0) { // toutes les 10 s : droits medecin a jour cote client
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) DoctorSync.send(p);
        }
    }

    // ------------------------------------------------------------------ examen sans mod (joueurs console) : accroupi + clic droit main vide

    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer doc) || !(e.getTarget() instanceof ServerPlayer target)) return;
        if (!doc.isShiftKeyDown() || !doc.getMainHandItem().isEmpty()) return;
        if (!Hospital.isDoctor(doc)) return;
        examineInChat(doc, target);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Fiche d'examen dans le chat (pour tous, y compris les joueurs console) avec bouton [Soigner]. */
    public static void examineInChat(ServerPlayer doc, ServerPlayer target) {
        Wounds.State s = Wounds.get(target);
        String name = target.getGameProfile().getName();
        doc.sendSystemMessage(Component.literal("✚ Examen médical — " + name + "  (" + String.format(java.util.Locale.ROOT, "%.1f", target.getHealth() / 2f)
                + " / " + (int) (target.getMaxHealth() / 2f) + " ❤)").withStyle(ChatFormatting.GOLD));
        if (!s.wounded()) {
            doc.sendSystemMessage(Component.literal("   Aucune blessure.").withStyle(ChatFormatting.GREEN));
            return;
        }
        for (String line : describe(s)) doc.sendSystemMessage(Component.literal("   " + line).withStyle(ChatFormatting.GRAY));
        if (!s.treated()) {
            MutableComponent btn = Component.literal("   [ Soigner " + name + " ]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/medical soigner " + name))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Arrête le saignement"))));
            doc.sendSystemMessage(btn);
        }
    }

    public static java.util.List<String> describe(Wounds.State s) {
        MedicalConfig.Data d = MedicalConfig.get();
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!s.wounded()) { out.add("Aucune blessure."); return out; }
        out.add("Blessure : " + s.cause());
        out.add("Depuis : " + Wounds.duration(s.ageSec()));
        out.add("Bandage : " + (s.bandaged() ? "oui" : "non"));
        if (s.treated()) out.add("Saignement : arrêté (soigné par un médecin)");
        else out.add("Saignement : -" + Wounds.hearts(s.bandaged() ? d.perteAvecBandage : d.perteSansBandage) + " toutes les "
                + (s.bandaged() ? d.minutesAvecBandage : d.minutesSansBandage) + " min (prochaine perte dans " + Wounds.duration(s.nextBleedSec()) + ")");
        int left = s.limpMinLeftSec();
        if (s.treated()) out.add("Boiterie : encore " + Wounds.duration(left));
        else out.add("Boiterie : oui, jusqu'au soin d'un médecin" + (left > 0 ? " (et au moins " + Wounds.duration(left) + ")" : ""));
        return out;
    }
}
