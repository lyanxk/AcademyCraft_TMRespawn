package cn.academy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;
import java.util.UUID;

/** Original TileDeveloper gui/sync notifications, including the current user before opening. */
public record DeveloperUserPayload(BlockPos pos, Optional<UUID> user, boolean open) implements CustomPacketPayload {
    public static final Type<DeveloperUserPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "developer_user"));
    public static final StreamCodec<ByteBuf, DeveloperUserPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DeveloperUserPayload::pos,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), DeveloperUserPayload::user,
            ByteBufCodecs.BOOL, DeveloperUserPayload::open, DeveloperUserPayload::new);
    public void applyTo(Player viewer) {
        if (viewer == null) return;
        if (viewer.level().getBlockEntity(pos) instanceof cn.academy.block.entity.ACMachineBlockEntity machine)
            machine.setDeveloperUser(user.orElse(null));
        if (open && user.filter(viewer.getUUID()::equals).isPresent())
            new OpenClientScreenPayload("developer:" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ())
                    .applyTo(viewer.getPersistentData());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
