package fr.citizen.medical.net;

import fr.citizen.medical.CitizenMedical;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serveur -> client : le joueur est-il medecin (touche Alt active) ? */
public record DoctorStatePayload(boolean doctor) implements CustomPacketPayload {
    public static final Type<DoctorStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "doctor_state"));
    public static final StreamCodec<ByteBuf, DoctorStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DoctorStatePayload::doctor, DoctorStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
