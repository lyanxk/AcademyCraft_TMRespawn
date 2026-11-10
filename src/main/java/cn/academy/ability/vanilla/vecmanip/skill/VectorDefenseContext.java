package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.event.ability.ReflectEvent;
import cn.academy.network.VectorDefensePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.VectorDefensePayload.*;

/** Server portions of VecDeviationContext and VecReflectionContext, preserving separate listener costs. */
public final class VectorDefenseContext {
    private static final List<VectorDefenseContext> ACTIVE=new ArrayList<>();
    private static final Map<MinecraftServer,ArrayDeque<PendingCounter>> PENDING_COUNTERS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Set<Entity>> PENDING_VELOCITIES=new IdentityHashMap<>();
    private record PendingCounter(VectorDefenseContext context,LivingEntity attacker,int tick,float damage) {}
    private static int next;
    public final int id=++next;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final boolean reflection;
    private final boolean enhancedReflection;
    private final float initialExperience;
    private final Set<Entity> visited=Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<ServerPlayer> audience=new ArrayList<>();
    private boolean ended;
    public static boolean handles(AbilitySkill skill){return skill.name().equals("vec_deviation") || skill.name().equals("vec_reflection");}
    public VectorDefenseContext(ServerPlayer player,AbilityState state,AbilitySkill skill) {
        this.player=player;this.skill=skill;reflection=skill.name().equals("vec_reflection");initialExperience=state.experience(skill.id());
        enhancedReflection=reflection && state.level()>=5;
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public void start(AbilityState state) {
        if(!reflection)state.consumeRaw(skill,0,snapshot(80,50),player.isCreative());
        ACTIVE.add(this);send(START,null,Vec3.ZERO);
    }
    public boolean tick(AbilityState state) {
        if(ended)return false;
        boolean paid=reflection || state.consumeRaw(skill,snapshot(13,5),0,player.isCreative());
        int range=reflection?4:5;
        var entities=player.level().getEntities(player,player.getBoundingBox().inflate(range),e->e.distanceToSqr(player)<=range*range && !visited.contains(e));
        for(var entity:entities) {
            if(EntityAffection.marked(entity))continue;
            float difficulty=EntityAffection.difficulty(entity);if(Float.isNaN(difficulty))continue;
            if(reflection) {
                if(!state.consumeRaw(skill,difficulty*live(state,300,160),0,player.isCreative()))continue;
                reflect(entity,player,enhancedReflection && entity instanceof Projectile?1.5+2*state.experience(skill.id()):0);
                if(enhancedReflection && entity instanceof Projectile)
                    PENDING_VELOCITIES.computeIfAbsent(player.server,server->Collections.newSetFromMap(new IdentityHashMap<>())).add(entity);
                EntityAffection.mark(entity);
                state.addExperience(skill,difficulty*.0008f);send(REFLECT_ENTITY,entity,entity.getDeltaMovement());
            } else {
                state.consumeForce(skill,snapshot(15,12),0,player.isCreative());
                if(entity instanceof LargeFireball fireball) {
                    int power=explosionPower(fireball);fireball.discard();
                    player.level().explode(null,fireball.getX(),fireball.getY(),fireball.getZ(),power,true,
                            player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)?Level.ExplosionInteraction.MOB:Level.ExplosionInteraction.NONE);
                } else if(entity instanceof SmallFireball)entity.discard();
                else {if(entity instanceof AbstractArrow arrow)arrow.setBaseDamage(0);entity.setDeltaMovement(Vec3.ZERO);entity.hurtMarked=true;}
                state.addExperience(skill,.001f*difficulty);send(STOP_ENTITY,entity,Vec3.ZERO);
                if(!(entity instanceof LargeFireball) && !(entity instanceof SmallFireball))EntityAffection.mark(entity);
            }
        }
        visited.addAll(entities); // Includes excluded entities and failures, just as the original visited set.
        if(reflection)return state.consumeRaw(skill,live(state,15,11),live(state,100,50)/20,player.isCreative());
        state.consumeRaw(skill,live(state,5,2.5f),live(state,.5f,.2f),player.isCreative());
        return paid;
    }
    public static void reflect(Entity entity,Entity caster,double extraSpeed) {
        Vec3 target=ElectricAttackTrace.trace(caster,20,false).getLocation();
        entity.setDeltaMovement(target.subtract(entity.getEyePosition()).normalize().scale(entity.getDeltaMovement().length()+extraSpeed));
        entity.hurtMarked=true;
    }
    private static int explosionPower(LargeFireball fireball){var tag=new CompoundTag();fireball.addAdditionalSaveData(tag);return tag.getByte("ExplosionPower");}
    public static boolean cancelIncoming(ServerPlayer victim) {
        for(var context:ACTIVE)if(!context.ended && context.reflection && context.player==victim)
            return true;
        return false;
    }
    public static void queueCounterAttack(ServerPlayer victim,DamageSource source,float damage) {
        // Projectiles and reflectable abilities have their own reflection paths.
        if(!(damage>0) || !Float.isFinite(damage) || source instanceof SkillDamageSource
                || !(source.getDirectEntity() instanceof LivingEntity attacker)
                || attacker==victim || !attacker.isAlive())return;
        // Check before payment, experience gain or attacking: two reflectors only cancel damage.
        if(attacker instanceof ServerPlayer other && cancelIncoming(other))return;
        for(var context:ACTIVE)if(!context.ended && context.reflection && context.player==victim) {
            PENDING_COUNTERS.computeIfAbsent(victim.server,server->new ArrayDeque<>())
                    .addLast(new PendingCounter(context,attacker,victim.server.getTickCount()+1,damage));
            return;
        }
    }
    public static void processCounterAttacks(MinecraftServer server) {
        var velocities=PENDING_VELOCITIES.remove(server);
        // Apply the full velocity after vanilla's motion packets, which cap each component at 3.9.
        if(velocities!=null)for(var entity:velocities)
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new VectorDefensePayload(
                    0,entity.getId(),true,SYNC_VELOCITY,entity.getId(),entity.getDeltaMovement()));
        var queue=PENDING_COUNTERS.get(server);
        if(queue==null)return;
        int tick=server.getTickCount();
        // Nested thorns enqueue for tick+1, so they cannot run during this drain.
        while(!queue.isEmpty() && tick-queue.peekFirst().tick()>=0)performCounter(queue.removeFirst());
        if(queue.isEmpty())PENDING_COUNTERS.remove(server);
    }
    private static void performCounter(PendingCounter pending) {
        var context=pending.context();var victim=context.player;var attacker=pending.attacker();
        if(context.ended || !victim.isAlive() || victim.isRemoved() || victim.hasDisconnected()
                || !attacker.isAlive() || attacker.isRemoved() || victim.level()!=attacker.level())return;
        // The attacker may have activated reflection since this counter was queued.
        if(attacker instanceof ServerPlayer other && (other.hasDisconnected() || cancelIncoming(other)))return;
        var state=AbilityState.load(victim);
        try {
            float damage=pending.damage(),reflectedDamage=context.live(state,.6f,1.2f)*damage;
            state.consumeForce(context.skill,context.live(state,20,15)*damage,0,victim.isCreative());
            state.addExperience(context.skill,damage*.0004f);
            AbilityContext.of(victim,context.skill).attack(attacker,reflectedDamage);
            context.send(EFFECT,null,attacker.position());
        } finally {
            state.save(victim);
        }
    }
    public static void clearPendingCounters(MinecraftServer server) {PENDING_COUNTERS.remove(server);PENDING_VELOCITIES.remove(server);}
    public static float reduceDamage(ServerPlayer victim,float damage) {
        // Also protect the later damage phase if another integration invokes it directly.
        if(cancelIncoming(victim))return 0;
        if(damage>9999)return damage;
        for(var context:ACTIVE)if(!context.ended && !context.reflection && context.player==victim) {
            var state=AbilityState.load(context.player);
            state.consumeRaw(context.skill,Math.min(state.cp(),context.live(state,15,12)),0,context.player.isCreative());
            state.addExperience(context.skill,damage*.0006f);context.send(PLAY,null,context.player.position());
            damage*=1-context.live(state,.4f,.9f);state.save(context.player);break;
        }
        return damage;
    }
    public static void onReflect(ReflectEvent event) {
        for(var context:ACTIVE)if(!context.ended && context.reflection && event.target==context.player) {
            event.setCanceled(true);
            Vec3 offset=event.player.getEyePosition().subtract(context.player.getEyePosition()).normalize().scale(.5);
            context.send(EFFECT,null,context.player.position().add(0,.4+context.player.getRandom().nextDouble()*.9,0).add(offset));
        }
    }
    public void end(){if(ended)return;ended=true;ACTIVE.remove(this);send(END,null,Vec3.ZERO);visited.clear();audience.clear();}
    private float snapshot(float a,float b){return enhancedReflection?b:a+(b-a)*initialExperience;}
    private float live(AbilityState state,float a,float b){return enhancedReflection?b:a+(b-a)*state.experience(skill.id());}
    private void send(int phase,Entity entity,Vec3 point){var packet=new VectorDefensePayload(id,player.getId(),reflection,phase,entity==null?-1:entity.getId(),point);audience.forEach(p->PacketDistributor.sendToPlayer(p,packet));}
}
