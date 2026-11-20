package fr.citizen.medical.net;

import fr.citizen.medical.CitizenMedical;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client -> serveur : le medecin examine un joueur. */
public record RequestWoundsPayload(UUID target) implements CustomPacketPayload {
    public static final Type<RequestWoundsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "request_wounds"));
    public static final StreamCodec<ByteBuf, RequestWoundsPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RequestWoundsPayload::target, RequestWoundsPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
