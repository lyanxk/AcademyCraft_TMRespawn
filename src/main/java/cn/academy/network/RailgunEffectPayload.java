package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Original main-shot (player,length) and reflected-shot (player,reflector) messages. */
public record RailgunEffectPayload(int caster, int reflector, double length, double radiusScale) implements CustomPacketPayload {
    public RailgunEffectPayload(int caster, int reflector, double length) { this(caster, reflector, length, 1); }
    public static final String QUEUE = "academy:railgun_queue";
    public static final Type<RailgunEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "railgun_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.caster); buffer.writeVarInt(value.reflector); buffer.writeDouble(value.length); buffer.writeDouble(value.radiusScale); },
            buffer -> new RailgunEffectPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var queue = player.getPersistentData().getList(QUEUE, 10);
        queue.add(toTag()); player.getPersistentData().put(QUEUE, queue);
    }
    public CompoundTag toTag() {
        var tag = new CompoundTag(); tag.putInt("Caster", caster); tag.putInt("Reflector", reflector); tag.putDouble("Length", length);
        tag.putDouble("RadiusScale", radiusScale);
        return tag;
    }
    public static RailgunEffectPayload fromTag(CompoundTag tag) {
        return new RailgunEffectPayload(tag.getInt("Caster"), tag.getInt("Reflector"), tag.getDouble("Length"), tag.getDouble("RadiusScale"));
    }
}
