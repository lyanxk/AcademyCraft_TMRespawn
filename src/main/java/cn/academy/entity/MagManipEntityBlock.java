package cn.academy.entity;

import cn.academy.ability.AbilityContext;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.electromaster.skill.MagneticManipulationRules;
import cn.academy.network.MagneticManipulationPayload;
import cn.academy.registry.ACEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original EntityBlock + MagManipEntityBlock + Rigidbody order, rather than vanilla falling-block physics. */
public final class MagManipEntityBlock extends Entity {
    public static final int NOTHING=0, MOVE_TO=1;
    private static final EntityDataAccessor<BlockState> BLOCK=SynchedEntityData.defineId(MagManipEntityBlock.class,EntityDataSerializers.BLOCK_STATE);
    private Player owner;
    public float damage;
    private int action;
    private Vec3 target=Vec3.ZERO;
    // EntityBlock's true field is used by its collision handler. The subclass's false shadow is never read there.
    private boolean placementEnabled=true;
    private boolean first=true;
    private int syncTicks;
    private final float yawSpeed=1+random.nextFloat()*2, pitchSpeed=1+random.nextFloat()*2;
    public float spinYaw, previousSpinYaw, spinPitch, previousSpinPitch;

    public MagManipEntityBlock(EntityType<? extends MagManipEntityBlock> type, Level level) {
        super(type,level); noCulling=true;
    }
    public static MagManipEntityBlock create(ServerPlayer owner, BlockState state, Vec3 position) {
        var entity=new MagManipEntityBlock(ACEntities.MAG_MANIP_BLOCK.get(),owner.level());
        entity.owner=owner;entity.damage=10;entity.entityData.set(BLOCK,state.getBlock().defaultBlockState());entity.setPos(position);return entity;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(BLOCK,Blocks.AIR.defaultBlockState()); }
    public BlockState blockState(){return entityData.get(BLOCK);}
    public int action(){return action;}
    public Vec3 target(){return target;}
    public Player owner(){return owner;}
    public void setMoveTo(Vec3 position) { action=MOVE_TO; target=MagneticManipulationRules.floatTarget(position); }
    public void follow(Player player) { setMoveTo(player.getEyePosition().subtract(0,.1,0).add(player.getLookAngle().scale(2))); }
    public void release() {
        action=NOTHING; placementEnabled=true;
        if(!level().isClientSide) broadcast(MagneticManipulationPayload.PLACE,20);
    }
    public void applySync(Player player,int action,Vec3 target) { owner=player;this.action=action;this.target=MagneticManipulationRules.floatTarget(target); }
    private void broadcast(int phase,double range) {
        if(!(level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var packet=new MagneticManipulationPayload(owner==null?-1:owner.getId(),-1,0,0,phase,getId(),action,target);
        if(owner instanceof ServerPlayer player && player.level()==level && player.distanceToSqr(this)<=range*range)
            PacketDistributor.sendToPlayer(player,packet);
        for(var player:level.players()) if(player!=owner && player.distanceToSqr(this)<=range*range) PacketDistributor.sendToPlayer(player,packet);
    }
    @Override public void tick() {
        // EntityAdvanced did not call vanilla Entity.onUpdate/baseTick.
        if(!level().isClientSide && ++syncTicks>5) { syncTicks=0;broadcast(MagneticManipulationPayload.ENTITY_SYNC,40); }
        if(first) { first=false;if(!level().isClientSide)broadcast(MagneticManipulationPayload.ENTITY_SYNC,40); }
        Vec3 velocity=getDeltaMovement();
        // Rigidbody stops at the first non-null result; Raytrace.perform returns MISS rather than null.
        // Consequently only the first (-width/2, 0, -width/2) corner is tested, even with accurateCollision=true.
        Vec3 corner=position().add(-getBbWidth()/2,0,-getBbWidth()/2);
        HitResult hit=ElectricAttackTrace.traceSegment(this,corner,corner.add(velocity),entity->entity!=owner);
        if(!level().isClientSide) {
            if(hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK && placementEnabled) place(block);
            else if(hit instanceof EntityHitResult entity && owner instanceof ServerPlayer player)
                AbilityContext.of(player,AbilityRegistry.skill("electromaster.mag_manip")).attack(entity.getEntity(),damage);
        }
        // Rigidbody integrates the old motion first, even after a collision handler marked the entity dead.
        setPos(position().add(velocity));
        previousSpinYaw=spinYaw;previousSpinPitch=spinPitch;spinYaw+=yawSpeed;spinPitch+=pitchSpeed;
        velocity=action==MOVE_TO ? MagneticManipulationRules.followVelocity(position(),target) : velocity.add(0,-.04,0);
        setDeltaMovement(velocity);
        // Original directly changes posX/Y/Z a second time, leaving its bounding box at the first integration.
        Vec3 next=position().add(velocity);setPosRaw(next.x,next.y,next.z);
    }
    private void place(BlockHitResult hit) {
        var pos=MagneticManipulationRules.placement(hit.getBlockPos(),hit.getDirection(),p->level().getBlockState(p).canBeReplaced());
        if(pos!=null) {
            BlockState state=blockState();
            // ItemBlock.placeBlockAt accepted the stored default state and did not do normal placement checks or item payment.
            if(level().setBlock(pos,state,11) && level().getBlockState(pos).is(state.getBlock()))
                state.getBlock().setPlacedBy(level(),pos,state,owner,new ItemStack(state.getBlock(),0));
        } else cn.academy.AcademyCraft.LOGGER.error("EntityBlock Lost: {}",hit);
        discard(); // Original also discards on failure; it never falls back to dropping a block item.
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
