package fr.citizen.medical.net;

import fr.citizen.medical.CitizenMedical;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client -> serveur : le medecin soigne un joueur. */
public record HealPayload(UUID target) implements CustomPacketPayload {
    public static final Type<HealPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "heal"));
    public static final StreamCodec<ByteBuf, HealPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HealPayload::target, HealPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
