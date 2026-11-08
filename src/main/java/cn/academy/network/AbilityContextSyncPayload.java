package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client HUD state for a server-authoritative 1.12.2-style key context. */
public record AbilityContextSyncPayload(int slot, String skill, int ticks, int targetTicks, int state, float consumptionHint)
        implements CustomPacketPayload {
    public static final int ENDED = 0;
    public static final int CHARGING = 1;
    public static final int ACTIVE = 2;
    /** Flashing.MainContext belongs to its custom key group, not the preset slot that started it. */
    public static final int FLASHING_CONTEXT = -1;

    public AbilityContextSyncPayload(int slot, String skill, int ticks, int targetTicks, int state) {
        this(slot, skill, ticks, targetTicks, state, 0);
    }

    public static final Type<AbilityContextSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "ability_context_sync"));
    public static final StreamCodec<ByteBuf, AbilityContextSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityContextSyncPayload::slot,
            ByteBufCodecs.STRING_UTF8, AbilityContextSyncPayload::skill,
            ByteBufCodecs.VAR_INT, AbilityContextSyncPayload::ticks,
            ByteBufCodecs.VAR_INT, AbilityContextSyncPayload::targetTicks,
            ByteBufCodecs.VAR_INT, AbilityContextSyncPayload::state,
            ByteBufCodecs.FLOAT, AbilityContextSyncPayload::consumptionHint,
            AbilityContextSyncPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
