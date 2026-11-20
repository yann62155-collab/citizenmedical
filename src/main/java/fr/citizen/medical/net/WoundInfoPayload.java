package fr.citizen.medical.net;

import fr.citizen.medical.CitizenMedical;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serveur -> client : resultat de l'examen d'un joueur.
 * {@code healError} vide = le bouton "Soigner" est utilisable, sinon raison affichee.
 */
public record WoundInfoPayload(UUID target, String name, float health, float maxHealth, boolean wounded, boolean treated,
                               List<String> lines, String healError) implements CustomPacketPayload {
    public static final Type<WoundInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "wound_info"));
    public static final StreamCodec<FriendlyByteBuf, WoundInfoPayload> CODEC = StreamCodec.ofMember(WoundInfoPayload::write, WoundInfoPayload::read);

    private void write(FriendlyByteBuf b) {
        UUIDUtil.STREAM_CODEC.encode(b, target);
        b.writeUtf(name, 64);
        b.writeFloat(health);
        b.writeFloat(maxHealth);
        b.writeBoolean(wounded);
        b.writeBoolean(treated);
        b.writeVarInt(lines.size());
        for (String l : lines) b.writeUtf(l, 512);
        b.writeUtf(healError, 256);
    }

    private static WoundInfoPayload read(FriendlyByteBuf b) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(b);
        String name = b.readUtf(64);
        float h = b.readFloat(), mh = b.readFloat();
        boolean w = b.readBoolean(), t = b.readBoolean();
        int n = Math.min(b.readVarInt(), 64);
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(b.readUtf(512));
        return new WoundInfoPayload(id, name, h, mh, w, t, lines, b.readUtf(256));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
