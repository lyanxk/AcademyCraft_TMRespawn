package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeveloperActionPayload(int action, BlockPos pos, boolean portable, String skill) implements CustomPacketPayload {
    public static final int LEARN_SKILL = 0;
    public static final int LEVEL_UP = 1;
    public static final int RESET_CATEGORY = 2;
    public static final int ABORT = 3;
    public static final int CLOSE = 4;

    public static final Type<DeveloperActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "developer_action"));
    public static final StreamCodec<ByteBuf, DeveloperActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DeveloperActionPayload::action,
            BlockPos.STREAM_CODEC, DeveloperActionPayload::pos,
            ByteBufCodecs.BOOL, DeveloperActionPayload::portable,
            ByteBufCodecs.STRING_UTF8, DeveloperActionPayload::skill,
            DeveloperActionPayload::new);

    public static DeveloperActionPayload learn(BlockPos pos, boolean portable, String skill) {
        return new DeveloperActionPayload(LEARN_SKILL, pos, portable, skill);
    }
    public static DeveloperActionPayload level(BlockPos pos, boolean portable) {
        return new DeveloperActionPayload(LEVEL_UP, pos, portable, "");
    }
    public static DeveloperActionPayload reset(BlockPos pos) {
        return new DeveloperActionPayload(RESET_CATEGORY, pos, false, "");
    }
    public static DeveloperActionPayload abort(BlockPos pos, boolean portable) {
        return new DeveloperActionPayload(ABORT, pos, portable, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
