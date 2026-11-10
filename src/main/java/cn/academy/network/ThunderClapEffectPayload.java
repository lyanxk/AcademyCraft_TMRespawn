package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Context identity keeps a delayed termination from cancelling a later activation. */
public record ThunderClapEffectPayload(int caster, int slot, int contextId, int requestId, boolean start) implements CustomPacketPayload {
    public static final String QUEUE = "academy:thunder_clap_effect_queue";
    public static final Type<ThunderClapEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "thunder_clap_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThunderClapEffectPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.caster); b.writeVarInt(v.slot); b.writeInt(v.contextId); b.writeVarInt(v.requestId); b.writeBoolean(v.start); },
            b -> new ThunderClapEffectPayload(b.readVarInt(), b.readVarInt(), b.readInt(), b.readVarInt(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        if (player == null) return;
        var list = player.getPersistentData().getList(QUEUE, 10);
        var tag = new CompoundTag(); tag.putInt("Caster", caster); tag.putInt("Slot", slot);
        tag.putInt("Context", contextId); tag.putInt("Request", requestId); tag.putBoolean("Start", start);
        list.add(tag); player.getPersistentData().put(QUEUE, list);
    }
}
