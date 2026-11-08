package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** ChargingContext's effect_start/effect_end, preserving the constructor's item-mode snapshot. */
public record ChargingEffectPayload(int caster, boolean start, boolean itemMode) implements CustomPacketPayload {
    public static final String QUEUE = "academy:charging_effect_queue";
    public static final Type<ChargingEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "charging_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargingEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.caster); buffer.writeBoolean(value.start); buffer.writeBoolean(value.itemMode); },
            buffer -> new ChargingEffectPayload(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var queue = player.getPersistentData().getList(QUEUE, 10);
        var tag = new CompoundTag(); tag.putInt("Caster", caster); tag.putBoolean("Start", start); tag.putBoolean("Item", itemMode);
        queue.add(tag); player.getPersistentData().put(QUEUE, queue);
    }
}
