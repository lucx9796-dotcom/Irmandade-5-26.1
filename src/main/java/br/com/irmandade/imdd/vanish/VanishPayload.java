package br.com.irmandade.imdd.vanish;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Servidor -> cliente (só para quem NÃO é op): jogadores desaparecidos, cada item no formato "uuid|nome". */
public record VanishPayload(List<String> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VanishPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("imdd", "vanish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VanishPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), VanishPayload::entries, VanishPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
