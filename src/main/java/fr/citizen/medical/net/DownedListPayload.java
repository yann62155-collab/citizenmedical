package fr.citizen.medical.net;

import fr.citizen.medical.CitizenMedical;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Serveur -> clients : joueurs a terre et joueurs allonges sur un lit d'hopital. */
public record DownedListPayload(List<UUID> players, List<UUID> lying) implements CustomPacketPayload {
    public static final Type<DownedListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "downed_list"));
    public static final StreamCodec<ByteBuf, DownedListPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), DownedListPayload::players,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), DownedListPayload::lying,
            DownedListPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
