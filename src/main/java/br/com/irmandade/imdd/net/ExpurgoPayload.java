package br.com.irmandade.imdd.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Servidor -> cliente: tempo restante do Dia do Expurgo em ms (0 = inativo). */
public record ExpurgoPayload(long remainingMillis) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExpurgoPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("imdd", "expurgo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExpurgoPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, ExpurgoPayload::remainingMillis, ExpurgoPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
