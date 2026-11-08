package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Independent node name/password property update from the 1.12.2 information panel. */
public record NodeConfigPayload(BlockPos pos, String name, String password,
                                boolean updatePassword) implements CustomPacketPayload {
    public static final Type<NodeConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "node_config"));
    public static final StreamCodec<ByteBuf, NodeConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodeConfigPayload::pos,
            ByteBufCodecs.STRING_UTF8, NodeConfigPayload::name,
            ByteBufCodecs.STRING_UTF8, NodeConfigPayload::password,
            ByteBufCodecs.BOOL, NodeConfigPayload::updatePassword,
            NodeConfigPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
