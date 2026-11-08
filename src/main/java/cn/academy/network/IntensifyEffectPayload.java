package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Original MADEALIVE, EFFECT_END and TERMINATED remain separate lifecycle messages. */
public record IntensifyEffectPayload(int caster, int slot, int phase) implements CustomPacketPayload {
    public static final int START = 0, FAILED = 1, PERFORMED = 2, TERMINATED = 3;
    public static final String QUEUE = "academy:intensify_effect_queue";
    public static final Type<IntensifyEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "intensify_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IntensifyEffectPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.caster); b.writeVarInt(v.slot); b.writeVarInt(v.phase); },
            b -> new IntensifyEffectPayload(b.readVarInt(), b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var list = player.getPersistentData().getList(QUEUE, 10);
        var tag = new CompoundTag(); tag.putInt("Caster", caster); tag.putInt("Slot", slot); tag.putInt("Phase", phase);
        list.add(tag); player.getPersistentData().put(QUEUE, list);
    }
}
