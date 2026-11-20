package fr.citizen.medical.net;

import fr.citizen.medical.CitizenMedical;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serveur -> client : etat de sa propre blessure (petit HUD). */
public record SelfStatePayload(boolean wounded, String cause, boolean bandaged, boolean treated,
                               int ageSec, int nextBleedSec, int limpMinLeftSec) implements CustomPacketPayload {
    public static final Type<SelfStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "self_state"));
    public static final StreamCodec<FriendlyByteBuf, SelfStatePayload> CODEC = StreamCodec.ofMember(SelfStatePayload::write, SelfStatePayload::read);

    private void write(FriendlyByteBuf b) {
        b.writeBoolean(wounded);
        b.writeUtf(cause, 256);
        b.writeBoolean(bandaged);
        b.writeBoolean(treated);
        b.writeVarInt(ageSec);
        b.writeInt(nextBleedSec);
        b.writeVarInt(limpMinLeftSec);
    }

    private static SelfStatePayload read(FriendlyByteBuf b) {
        return new SelfStatePayload(b.readBoolean(), b.readUtf(256), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readInt(), b.readVarInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
