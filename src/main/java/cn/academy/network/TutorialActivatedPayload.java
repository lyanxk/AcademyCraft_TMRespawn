package cn.academy.network;

import cn.academy.event.TutorialActivatedEvent;
import cn.academy.tutorial.TutorialRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/** TutorialData.MSG_ACTIVATE: each activation reaches the client event bus, even within one tick. */
public record TutorialActivatedPayload(String tutorial) implements CustomPacketPayload {
    public static final Type<TutorialActivatedPayload> TYPE = new Type<>(ResourceLocation.parse("academy:tutorial_activated"));
    public static final StreamCodec<ByteBuf, TutorialActivatedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TutorialActivatedPayload::tutorial, TutorialActivatedPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void applyTo(Player player) {
        var entry = TutorialRegistry.find(tutorial);
        if (player != null && entry != null) NeoForge.EVENT_BUS.post(new TutorialActivatedEvent(player, entry));
    }
}
