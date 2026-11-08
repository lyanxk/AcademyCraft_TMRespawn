package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public record MineDetectEffectPayload(float range, boolean advanced) implements CustomPacketPayload {
    public static final String QUEUE = "academy:mine_detect_effect_queue";
    public static final Type<MineDetectEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "mine_detect_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MineDetectEffectPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeFloat(v.range); b.writeBoolean(v.advanced); }, b -> new MineDetectEffectPayload(b.readFloat(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var queue = player.getPersistentData().getList(QUEUE, 10);
        var tag = new CompoundTag(); tag.putFloat("Range", range); tag.putBoolean("Advanced", advanced);
        queue.add(tag); player.getPersistentData().put(QUEUE, queue);
    }
}
