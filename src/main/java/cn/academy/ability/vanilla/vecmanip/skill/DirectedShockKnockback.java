package cn.academy.ability.vanilla.vecmanip.skill;

import net.minecraft.server.MinecraftServer;
import cn.academy.network.VectorStrikePayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Precomputed initial velocities for 1/6/11/16/21 blocks of displacement over one second. */
public final class DirectedShockKnockback {
    private static final double[] GROUND_SPEED={.454003,2.724015,4.994028,7.264040,9.534053};
    private static final double[] WATER_SPEED={.202333,1.213996,2.225660,3.237324,4.248987};
    private static final double[] AIR_SPEED={.106088,.636526,1.166964,1.697402,2.227841};
    private static final Map<MinecraftServer,Set<Entity>> PENDING_SYNC=new IdentityHashMap<>();

    public static void start(Entity target,Vec3 direction,int tier) {
        double[] speeds=target.isInWater()?WATER_SPEED:target.onGround()?GROUND_SPEED:AIR_SPEED;
        var velocity=direction.scale(speeds[tier]);
        target.setDeltaMovement(velocity);
        target.hasImpulse=true;target.hurtMarked=true;
        PENDING_SYNC.computeIfAbsent(target.getServer(),ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(target);
    }

    public static void sync(MinecraftServer server) {
        var targets=PENDING_SYNC.remove(server);if(targets==null)return;
        // Send once after vanilla's motion packets, whose components are capped at 3.9.
        for(var target:targets) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,new VectorStrikePayload(
                    0,target.getId(),VectorStrikePayload.ENHANCED_SHOCK,VectorStrikePayload.KNOCKBACK_VELOCITY,
                    -1,0,0,target.getDeltaMovement(),List.of(target.getId()),List.of()));
        }
    }

    public static void clear(MinecraftServer server){PENDING_SYNC.remove(server);}
    private DirectedShockKnockback(){}
}
