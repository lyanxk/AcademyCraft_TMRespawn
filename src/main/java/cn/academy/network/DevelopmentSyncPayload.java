package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative progress and live developer energy for the original multi-stimulation process. */
public record DevelopmentSyncPayload(int state, int action, String skill, float progress,
                                     boolean portable, boolean offhand, double energy, int maxEnergy)
        implements CustomPacketPayload {
    public static final int IDLE = 0;
    public static final int FAILED = 1;
    public static final int DEVELOPING = 2;
    public static final int DONE = 3;

    public static final Type<DevelopmentSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "development_sync"));
    public static final StreamCodec<ByteBuf, DevelopmentSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DevelopmentSyncPayload decode(ByteBuf buffer) {
            return new DevelopmentSyncPayload(ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.STRING_UTF8.decode(buffer),
                    buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readDouble(), ByteBufCodecs.VAR_INT.decode(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, DevelopmentSyncPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.state);
            ByteBufCodecs.VAR_INT.encode(buffer, value.action);
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.skill);
            buffer.writeFloat(value.progress);
            buffer.writeBoolean(value.portable);
            buffer.writeBoolean(value.offhand);
            buffer.writeDouble(value.energy);
            ByteBufCodecs.VAR_INT.encode(buffer, value.maxEnergy);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
