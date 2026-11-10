package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Client-only weather visualization; never spawns a modern server lightning entity. */
public record ThunderClapLightningPayload(Vec3 position) implements CustomPacketPayload {
    public static final String QUEUE = "academy:thunder_clap_lightning";
    public static final Type<ThunderClapLightningPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "thunder_clap_lightning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThunderClapLightningPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeDouble(v.position.x); b.writeDouble(v.position.y); b.writeDouble(v.position.z); },
            b -> new ThunderClapLightningPayload(new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var list = player.getPersistentData().getList(QUEUE, 10);
        var tag = new CompoundTag(); tag.putDouble("X", position.x); tag.putDouble("Y", position.y); tag.putDouble("Z", position.z);
        list.add(tag); player.getPersistentData().put(QUEUE, list);
    }
}
