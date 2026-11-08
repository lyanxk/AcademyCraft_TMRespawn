package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.ability.AbilityContext;
import cn.academy.ability.AbilityState;
import cn.academy.network.RadiationMarkPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original MDDamageHelper, including its caster-duration lookup and caster-only mark synchronization. */
public final class MDDamageHelper {
    public static final String MARK="md_marktick",RATE="md_markrate",PASSIVE="meltdowner.rad_intensify";

    public static void attack(AbilityContext context,Entity target,float damage) {
        context.attack(target,damage);
        mark(context.player,target);
    }
    public static void mark(net.minecraft.server.level.ServerPlayer player,Entity target) {
        var state=AbilityState.load(player);
        if(!state.learned().contains(PASSIVE))return;
        int ticks=Math.max(60,ticks(player));
        setTicks(target,ticks);
        target.getPersistentData().putFloat(RATE,1.4f+state.experience(PASSIVE)*(1.8f-1.4f));
        // The original writes target, but serializes player. Preserve that observable behavior.
        var message=new RadiationMarkPayload(player.getId(),ticks);
        for(var viewer:player.serverLevel().players())if(viewer.distanceToSqr(player)<400)
            PacketDistributor.sendToPlayer(viewer,message);
    }
    public static int ticks(Entity entity) {
        migrate(entity);
        return entity.getPersistentData().getInt(MARK);
    }
    public static void setTicks(Entity entity,int ticks) {
        migrate(entity);entity.getPersistentData().putInt(MARK,ticks);
    }
    /** Convert saves made by earlier port builds once; unloaded time stops elapsing after conversion. */
    private static void migrate(Entity entity) {
        var data=entity.getPersistentData();
        if(!data.contains("academy:md_mark_until"))return;
        if(!data.contains(MARK)) {
            long remaining=data.getLong("academy:md_mark_until")-entity.level().getGameTime();
            data.putInt(MARK,(int)Math.clamp(remaining,0,Integer.MAX_VALUE));
            data.putFloat(RATE,data.getFloat("academy:md_mark_rate"));
        }
        data.remove("academy:md_mark_until");data.remove("academy:md_mark_rate");
    }
    /** Applied to post-mitigation damage by LivingDamageEvent.Pre. */
    public static float amplifyDamage(LivingEntity target,float damage) {
        return ticks(target)>0 ? damage*target.getPersistentData().getFloat(RATE) : damage;
    }
    public static void livingTick(LivingEntity entity) {
        int ticks=ticks(entity);
        if(ticks>0)setTicks(entity,ticks-1);
        if(entity.level().isClientSide && ticks(entity)>0)NeoForge.EVENT_BUS.post(new ClientTick(entity));
    }
    public static final class ClientTick extends Event {
        public final LivingEntity entity;
        public ClientTick(LivingEntity entity){this.entity=entity;}
    }
    private MDDamageHelper(){}
}
