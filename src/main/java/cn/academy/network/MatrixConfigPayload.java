package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MatrixConfigPayload(BlockPos pos, String ssid, String password,
                                  boolean updatePassword) implements CustomPacketPayload {
    public static final Type<MatrixConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "matrix_config"));
    public static final StreamCodec<ByteBuf, MatrixConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MatrixConfigPayload::pos,
            ByteBufCodecs.STRING_UTF8, MatrixConfigPayload::ssid,
            ByteBufCodecs.STRING_UTF8, MatrixConfigPayload::password,
            ByteBufCodecs.BOOL, MatrixConfigPayload::updatePassword,
            MatrixConfigPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
