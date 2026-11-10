package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Typed EntityArc spawn data; target IDs retain client-side entity positions and camera association. */
public record ArcEffectPayload(int caster, int kind, Vec3 start, Vec3 end, int target, int duration,
                               boolean sound) implements CustomPacketPayload {
    public static final int WEAK = 0, STRONG = 1, AOE = 2;
    public static final String QUEUE = "academy:legacy_arc_queue";
    public static final Type<ArcEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "arc_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArcEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.caster); buffer.writeByte(value.kind);
                buffer.writeVec3(value.start); buffer.writeVec3(value.end);
                buffer.writeVarInt(value.target); buffer.writeVarInt(value.duration); buffer.writeBoolean(value.sound);
            }, buffer -> new ArcEffectPayload(buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readVec3(), buffer.readVec3(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null || kind < WEAK || kind > AOE) return;
        var queue = player.getPersistentData().getList(QUEUE, 10);
        queue.add(toTag()); player.getPersistentData().put(QUEUE, queue);
    }
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Caster", caster); tag.putInt("Kind", kind); tag.putInt("Target", target);
        tag.putInt("Duration", duration); tag.putBoolean("Sound", sound);
        tag.putDouble("SX", start.x); tag.putDouble("SY", start.y); tag.putDouble("SZ", start.z);
        tag.putDouble("EX", end.x); tag.putDouble("EY", end.y); tag.putDouble("EZ", end.z);
        return tag;
    }
    public static ArcEffectPayload fromTag(CompoundTag tag) {
        return new ArcEffectPayload(tag.getInt("Caster"), tag.getInt("Kind"),
                new Vec3(tag.getDouble("SX"), tag.getDouble("SY"), tag.getDouble("SZ")),
                new Vec3(tag.getDouble("EX"), tag.getDouble("EY"), tag.getDouble("EZ")),
                tag.getInt("Target"), tag.getInt("Duration"), tag.getBoolean("Sound"));
    }
}
