package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenClientScreenPayload(String screen) implements CustomPacketPayload {
    public static final Type<OpenClientScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "open_screen"));
    public static final StreamCodec<ByteBuf, OpenClientScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenClientScreenPayload::screen, OpenClientScreenPayload::new);

    /** Notices replace the previous notice, but must never replace/open a screen or abort skill input. */
    public void applyTo(net.minecraft.nbt.CompoundTag clientData) {
        clientData.putString(screen.startsWith("notify:") ? "academy:notification" : "academy:open_screen", screen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
