package cn.academy.network;

import cn.academy.AcademyCraft;
import cn.academy.menu.ACMachineMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.Optional;

/** Server-selected names/coordinates/encryption flags; no password or hash is sent. */
public record WirelessPageSnapshotPayload(int containerId, int requestId, BlockPos pos,
                                          Optional<Target> linked, List<Target> available) implements CustomPacketPayload {
    public WirelessPageSnapshotPayload { available = List.copyOf(available); }
    public record Target(BlockPos pos, String name, boolean encrypted) {
        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Target::pos,
                ByteBufCodecs.STRING_UTF8, Target::name,
                ByteBufCodecs.BOOL, Target::encrypted, Target::new);
    }
    public static final Type<WirelessPageSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "wireless_page_snapshot"));
    public static final StreamCodec<ByteBuf, WirelessPageSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WirelessPageSnapshotPayload::containerId,
            ByteBufCodecs.VAR_INT, WirelessPageSnapshotPayload::requestId,
            BlockPos.STREAM_CODEC, WirelessPageSnapshotPayload::pos,
            ByteBufCodecs.optional(Target.STREAM_CODEC), WirelessPageSnapshotPayload::linked,
            Target.STREAM_CODEC.apply(ByteBufCodecs.list(100)), WirelessPageSnapshotPayload::available,
            WirelessPageSnapshotPayload::new);
    private static final java.util.Map<Player, WirelessPageSnapshotPayload> DEVELOPER_SNAPSHOTS = new java.util.WeakHashMap<>();
    public static WirelessPageSnapshotPayload developerSnapshot(Player player) { return DEVELOPER_SNAPSHOTS.get(player); }
    public void applyTo(Player player) {
        if (player == null) return;
        if (containerId < 0) DEVELOPER_SNAPSHOTS.put(player, this);
        else if (player.containerMenu instanceof ACMachineMenu menu) menu.acceptWirelessSnapshot(this);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
