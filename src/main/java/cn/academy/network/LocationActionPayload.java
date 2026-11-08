package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative actions for Location Teleport's saved-location interface. */
public record LocationActionPayload(int action, int index, String name) implements CustomPacketPayload {
    public static final int ADD = 0;
    public static final int REMOVE = 1;
    public static final int TELEPORT = 2;

    public static final Type<LocationActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "location_action"));
    public static final StreamCodec<ByteBuf, LocationActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LocationActionPayload::action,
            ByteBufCodecs.VAR_INT, LocationActionPayload::index,
            ByteBufCodecs.STRING_UTF8, LocationActionPayload::name,
            LocationActionPayload::new);

    public static LocationActionPayload add(String name) { return new LocationActionPayload(ADD, 0, name); }
    public static LocationActionPayload remove(int index) { return new LocationActionPayload(REMOVE, index, ""); }
    public static LocationActionPayload teleport(int index) { return new LocationActionPayload(TELEPORT, index, ""); }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
