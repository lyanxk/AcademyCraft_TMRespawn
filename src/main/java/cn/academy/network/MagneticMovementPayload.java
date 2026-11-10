package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public record MagneticMovementPayload(int caster, int slot, int contextId, int requestId, int phase, float overloadKeep, Vec3 target)
        implements CustomPacketPayload {
    public static final int LINK = 0, START = 1, UPDATE = 2, END = 3;
    public static final String QUEUE = "academy:magnetic_movement_queue";
    public static final Type<MagneticMovementPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "magnetic_movement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticMovementPayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> { b.writeVarInt(v.caster); b.writeVarInt(v.slot); b.writeInt(v.contextId); b.writeVarInt(v.requestId);
                b.writeVarInt(v.phase); b.writeFloat(v.overloadKeep); b.writeDouble(v.target.x); b.writeDouble(v.target.y); b.writeDouble(v.target.z); },
            b -> new MagneticMovementPayload(b.readVarInt(), b.readVarInt(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readFloat(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var queue = player.getPersistentData().getList(QUEUE,10); var tag = new CompoundTag();
        tag.putInt("Caster",caster); tag.putInt("Slot",slot); tag.putInt("Context",contextId); tag.putInt("Request",requestId);
        tag.putInt("Phase",phase); tag.putFloat("Overload",overloadKeep);
        tag.putDouble("X",target.x); tag.putDouble("Y",target.y); tag.putDouble("Z",target.z);
        queue.add(tag); player.getPersistentData().put(QUEUE,queue);
    }
}
