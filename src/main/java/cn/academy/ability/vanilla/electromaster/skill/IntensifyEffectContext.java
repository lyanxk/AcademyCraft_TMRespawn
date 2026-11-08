package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.network.IntensifyEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;

/** Original ContextManager's targets are fixed before MADEALIVE/SkillPerform. */
public final class IntensifyEffectContext {
    private final ServerPlayer owner;
    private final int slot;
    private final ArrayList<ServerPlayer> audience = new ArrayList<>();
    public IntensifyEffectContext(ServerPlayer owner, int slot) {
        this.owner = owner; this.slot = slot;
        audience.add(owner);
        for (var viewer : owner.serverLevel().players())
            if (viewer != owner && viewer.distanceToSqr(owner) <= 625) audience.add(viewer);
    }
    public void start() { send(IntensifyEffectPayload.START); }
    public void end(boolean performed) {
        send(performed ? IntensifyEffectPayload.PERFORMED : IntensifyEffectPayload.FAILED);
        send(IntensifyEffectPayload.TERMINATED);
        audience.clear();
    }
    private void send(int phase) {
        var payload = new IntensifyEffectPayload(owner.getId(), slot, phase);
        audience.forEach(viewer -> PacketDistributor.sendToPlayer(viewer, payload));
    }
}
