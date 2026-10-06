package com.miramusic;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: activa o desactiva Mira Music para ese jugador. */
public record MiraStatePayload(boolean enabled) implements CustomPayload {

    public static final CustomPayload.Id<MiraStatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(MiraMusic.MOD_ID, "state"));

    public static final PacketCodec<RegistryByteBuf, MiraStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, MiraStatePayload::enabled,
            MiraStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
