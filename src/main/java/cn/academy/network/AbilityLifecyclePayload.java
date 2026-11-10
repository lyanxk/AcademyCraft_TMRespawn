package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Carries the state associated with a both-sides event, so client listeners cannot observe an older sync. */
public record AbilityLifecyclePayload(int event, String skill, CompoundTag snapshot) implements CustomPacketPayload {
    public static final int CATEGORY_CHANGED = 0;
    public static final int SKILL_LEARNED = 1;
    public static final int ACTIVATED = 2;
    public static final int DEACTIVATED = 3;
    public static final int PRESET_UPDATED = 4;
    public static final Type<AbilityLifecyclePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "ability_lifecycle"));
    public static final StreamCodec<ByteBuf, AbilityLifecyclePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityLifecyclePayload::event,
            ByteBufCodecs.STRING_UTF8, AbilityLifecyclePayload::skill,
            ByteBufCodecs.COMPOUND_TAG, AbilityLifecyclePayload::snapshot,
            AbilityLifecyclePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
