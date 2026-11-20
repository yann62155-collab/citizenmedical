package fr.citizen.medical.net;

import fr.citizen.medical.Hospital;
import fr.citizen.medical.MedicalCommands;
import fr.citizen.medical.MedicalConfig;
import fr.citizen.medical.MedicalEvents;
import fr.citizen.medical.Wounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    private Network() { }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, Network::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        // optional() : les joueurs sans le mod (joueurs console via Geyser) peuvent quand meme se connecter.
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToClient(DoctorStatePayload.TYPE, DoctorStatePayload.CODEC,
                (p, ctx) -> fr.citizen.medical.client.ClientHooks.onDoctor(p));
        r.playToClient(SelfStatePayload.TYPE, SelfStatePayload.CODEC,
                (p, ctx) -> fr.citizen.medical.client.ClientHooks.onSelf(p));
        r.playToClient(WoundInfoPayload.TYPE, WoundInfoPayload.CODEC,
                (p, ctx) -> fr.citizen.medical.client.ClientHooks.onInfo(p));
        r.playToServer(RequestWoundsPayload.TYPE, RequestWoundsPayload.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer doc)) return;
            ServerPlayer t = doc.getServer().getPlayerList().getPlayer(p.target());
            if (t == null) return;
            if (!Hospital.isDoctor(doc)) {
                doc.displayClientMessage(Component.literal("Seuls les médecins de l'hôpital peuvent examiner les blessures.").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!doc.hasPermissions(2) && (doc.level() != t.level() || doc.distanceTo(t) > MedicalConfig.get().distanceExamen)) {
                doc.displayClientMessage(Component.literal("Patient trop loin pour être examiné.").withStyle(ChatFormatting.RED), true);
                return;
            }
            sendInfo(doc, t);
        });
        r.playToServer(HealPayload.TYPE, HealPayload.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer doc)) return;
            ServerPlayer t = doc.getServer().getPlayerList().getPlayer(p.target());
            if (t == null) return;
            String err = MedicalCommands.canTreat(doc, t);
            if (err != null) doc.displayClientMessage(Component.literal(err).withStyle(ChatFormatting.RED), true);
            else Wounds.treat(t, doc);
            sendInfo(doc, t);
        });
    }

    public static void sendInfo(ServerPlayer doc, ServerPlayer t) {
        if (!doc.connection.hasChannel(WoundInfoPayload.TYPE)) {
            MedicalEvents.examineInChat(doc, t);
            return;
        }
        Wounds.State s = Wounds.get(t);
        String err = MedicalCommands.canTreat(doc, t);
        PacketDistributor.sendToPlayer(doc, new WoundInfoPayload(t.getUUID(), t.getGameProfile().getName(), t.getHealth(), t.getMaxHealth(),
                s.wounded(), s.treated(), MedicalEvents.describe(s), err == null ? "" : err));
    }
}
