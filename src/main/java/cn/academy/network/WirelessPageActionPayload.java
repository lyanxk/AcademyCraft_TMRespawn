package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** An absent target means disconnect; otherwise connect to an issued candidate. */
public record WirelessPageActionPayload(int containerId, int requestId, BlockPos pos,
                                        Optional<BlockPos> target, String password) implements CustomPacketPayload {
    public static final Type<WirelessPageActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "wireless_page_action"));
    public static final StreamCodec<ByteBuf, WirelessPageActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WirelessPageActionPayload::containerId,
            ByteBufCodecs.VAR_INT, WirelessPageActionPayload::requestId,
            BlockPos.STREAM_CODEC, WirelessPageActionPayload::pos,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), WirelessPageActionPayload::target,
            ByteBufCodecs.stringUtf8(64), WirelessPageActionPayload::password, WirelessPageActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
