package dashketch.mods.custom_music_discs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record MusicUploadPayload(String fileName, byte[] data, boolean isFirstChunk, boolean isLastChunk) implements CustomPacketPayload {
    public static final Type<MusicUploadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("custom_music_discs", "music_upload"));

    public static final StreamCodec<FriendlyByteBuf, MusicUploadPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.fileName);
                buf.writeByteArray(payload.data);
                buf.writeBoolean(payload.isFirstChunk);
                buf.writeBoolean(payload.isLastChunk);
            },
            buf -> new MusicUploadPayload(buf.readUtf(), buf.readByteArray(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}