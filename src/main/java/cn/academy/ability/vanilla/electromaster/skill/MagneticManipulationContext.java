package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.ability.*;
import cn.academy.entity.MagManipEntityBlock;
import cn.academy.network.MagneticManipulationPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class MagneticManipulationContext {
    private static final AtomicInteger NEXT_ID=new AtomicInteger();
    private final int id=NEXT_ID.incrementAndGet();
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float cp,overload,speed,damage;
    private final int cooldown,slot,request;
    private final ArrayList<ServerPlayer> audience=new ArrayList<>();
    private MagManipEntityBlock entity;
    private boolean performed,ended;
    public MagneticManipulationContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;
        float exp=state.experience(skill.id());boolean enhanced=state.level()>=4;
        cp=enhanced?270+230*exp:140+(270-140)*exp;overload=enhanced?20:35+(20-35)*exp;
        speed=enhanced?1+2*exp:.5f+(1-.5f)*exp;cooldown=enhanced?40:(int)(60+(40-60)*exp);
        damage=enhanced?10+90*exp:10;
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public boolean start() {
        var stack=player.getMainHandItem();
        if(stack.getItem() instanceof BlockItem item && MagneticManipulationRules.accepts(item.getBlock().defaultBlockState())) {
            if(!player.isCreative())stack.shrink(1);
            entity=MagManipEntityBlock.create(player,item.getBlock().defaultBlockState(),player.getEyePosition());
        } else {
            var hit=ElectricAttackTrace.traceBlocksFiltered(player,10,MagneticManipulationRules::accepts);
            if(hit.getType()!=HitResult.Type.BLOCK)return false;
            var state=player.level().getBlockState(hit.getBlockPos());
            player.level().removeBlock(hit.getBlockPos(),false);
            entity=MagManipEntityBlock.create(player,state,MagneticManipulationRules.grabbedPosition(hit.getBlockPos()));
        }
        entity.damage=damage;
        player.serverLevel().addFreshEntity(entity);tick();send(MagneticManipulationPayload.START,Vec3.ZERO);return true;
    }
    public void tick(){if(entity!=null)entity.follow(player);}
    public boolean perform(AbilityState state) {
        performed=true;
        if(entity==null)return false;
        entity.release();
        if(player.distanceToSqr(entity)>=25 || !state.consumeRaw(skill,cp,overload,player.isCreative()))return false;
        HitResult hit=ElectricAttackTrace.trace(player,20,false);
        Vec3 point=hit.getLocation();
        if(hit instanceof EntityHitResult entityHit)point=point.add(0,entityHit.getEntity().getEyeHeight()*.6,0);
        Vec3 velocity=point.subtract(entity.position()).normalize().scale(speed);
        entity.setDeltaMovement(velocity);
        state.finishContext(skill,cooldown,.005f);
        send(MagneticManipulationPayload.RELEASE,velocity);return true;
    }
    public void end() {
        if(ended)return;ended=true;
        if(!performed && entity!=null)entity.release();
        send(MagneticManipulationPayload.END,Vec3.ZERO);audience.clear();
    }
    private void send(int phase,Vec3 value) {
        var packet=new MagneticManipulationPayload(player.getId(),slot,id,request,phase,entity==null?-1:entity.getId(),0,value);
        audience.forEach(viewer->PacketDistributor.sendToPlayer(viewer,packet));
    }
}
