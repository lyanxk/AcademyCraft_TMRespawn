package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Starts or stops a looping, entity-following legacy context sound on nearby clients. */
public record ContextSoundPayload(UUID entity, String sound, boolean start, float volume)
        implements CustomPacketPayload {
    public static final Type<ContextSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "context_sound"));
    public static final StreamCodec<ByteBuf, ContextSoundPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.core.UUIDUtil.STREAM_CODEC, ContextSoundPayload::entity,
            ByteBufCodecs.STRING_UTF8, ContextSoundPayload::sound,
            ByteBufCodecs.BOOL, ContextSoundPayload::start,
            ByteBufCodecs.FLOAT, ContextSoundPayload::volume,
            ContextSoundPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
