package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Original 30-block coin-throw charge-hook notification. */
public record RailgunChargePayload(int caster) implements CustomPacketPayload {
    public static final String QUEUE = "academy:railgun_charge_queue";
    public static final Type<RailgunChargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "railgun_charge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunChargePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.caster), buffer -> new RailgunChargePayload(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var queue = player.getPersistentData().getList(QUEUE, 10);
        var tag = new CompoundTag(); tag.putInt("Caster", caster); queue.add(tag);
        player.getPersistentData().put(QUEUE, queue);
    }
}
