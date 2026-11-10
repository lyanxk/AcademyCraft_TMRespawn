package cn.academy.ability.vanilla.teleporter.skill;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.teleporter.util.TPSkillHelper;
import cn.academy.block.ACMachineBlock;
import cn.academy.network.ShiftTeleportPayload;
import cn.academy.registry.ACSounds;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.ShiftTeleportPayload.*;

/** STContext: effect before payment, adjacent-cell placement, then a fresh feet-origin damage trace. */
public final class ShiftTeleportContext {
    private static int next;
    public final int id=++next,slot,request;
    private final ServerPlayer player;private final AbilitySkill skill;private final float experience;
    private final List<ServerPlayer> audience=new ArrayList<>();private boolean performed,ended;
    public ShiftTeleportContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;experience=state.experience(skill.id());
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("shift_teleport_context", "shift_teleport_context_threshold_1"))audience.add(viewer);
    }
    public static boolean validHand(Player player){return ShiftTeleportPlacement.validItem(player.getMainHandItem());}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(skill.name().equals("shift_tp") && request>0)PacketDistributor.sendToPlayer(player,new ShiftTeleportPayload(0,player.getId(),END,slot,request,0,false));
    }
    public static BlockHitResult trace(Player player,float experience) {
        var hit=ElectricAttackTrace.traceBlocks(player,25+(35-25)*experience);
        return hit.getType()==HitResult.Type.MISS
                ? BlockHitResult.miss(hit.getLocation(),net.minecraft.core.Direction.DOWN,BlockPos.containing(hit.getLocation())) : hit;
    }
    public static BlockPos destination(Player player,float experience) {
        var hit=trace(player,experience);
        if(hit.getType()==HitResult.Type.MISS) {
            var end=hit.getLocation();
            return new BlockPos((int)end.x,(int)end.y,(int)end.z);
        }
        return hit.getBlockPos().relative(hit.getDirection());
    }
    public static List<Entity> targets(Player player,float experience) {
        Vec3 start=player.position(),end=Vec3.atCenterOf(destination(player,experience));
        return player.level().getEntities((Entity)null,new AABB(start,end),entity->{
            if(entity==player || !(entity instanceof LivingEntity))return false;
            double half=entity.getBbWidth()/2.0;
            return intersects(new AABB(entity.getX()-half,entity.getY(),entity.getZ()-half,entity.getX()+half,entity.getY()+entity.getBbHeight(),entity.getZ()+half),start,end);
        });
    }
    /** VecUtils.checkLineBox uses strict face interiors and rejects segment endpoints lying on a face. */
    private static boolean intersects(AABB box,Vec3 start,Vec3 end) {
        double[] lo={box.minX,box.minY,box.minZ},hi={box.maxX,box.maxY,box.maxZ},a={start.x,start.y,start.z},b={end.x,end.y,end.z};
        for(int axis=0;axis<3;axis++)if(a[axis]<lo[axis] && b[axis]<lo[axis] || a[axis]>hi[axis] && b[axis]>hi[axis])return false;
        if(start.x>box.minX && start.x<box.maxX && start.y>box.minY && start.y<box.maxY && start.z>box.minZ && start.z<box.maxZ)return true;
        for(int side=0;side<2;side++)for(int axis=0;axis<3;axis++) {
            double plane=side==0?lo[axis]:hi[axis],d0=a[axis]-plane,d1=b[axis]-plane;if(d0*d1>=0 || d0==d1)continue;
            double t=-d0/(d1-d0);boolean inside=true;
            for(int other=0;other<3;other++)if(other!=axis){double hit=a[other]+(b[other]-a[other])*t;if(hit<=lo[other] || hit>=hi[other])inside=false;}
            if(inside)return true;
        }
        return false;
    }
    public void start(){send(START,false);}
    public void release(AbilityState state) {
        if(ended || !validHand(player))return;
        send(EFFECT,true);var stack=player.getMainHandItem();var item=(BlockItem)stack.getItem();var hit=trace(player,experience);
        var point=hit.getType()==HitResult.Type.MISS ? hit.getLocation()
                : hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
        var destination=hit.getBlockPos().relative(hit.getDirection());
        if(!state.consumeRaw(skill,Balance.i("shift_teleport_context", "release_consume_raw_1")+(Balance.i("shift_teleport_context", "release_consume_raw_2")-Balance.i("shift_teleport_context", "release_consume_raw_3"))*experience,Balance.i("shift_teleport_context", "release_consume_raw_4")+(Balance.i("shift_teleport_context", "release_consume_raw_5")-Balance.i("shift_teleport_context", "release_consume_raw_6"))*experience,player.isCreative()))return;
        var world=player.serverLevel();var block=item.getBlock();
        boolean canPlace=ShiftTeleportPlacement.canPlace(block,world,destination);
        if(canPlace && AbilityContext.of(player,skill).canBreakBlock(world,hit.getBlockPos())) {
            // ItemBlockMulti checked surrounding space in onItemUse, which STContext never calls.
            // Keep its original unchecked setPlacedBy path for the Academy multiblocks.
            var placed=ShiftTeleportPlacement.state(block,world,hit.getBlockPos(),destination,hit.getDirection(),(float)point.y,player,stack,
                    () -> block instanceof ACMachineBlock?block.defaultBlockState().setValue(ACMachineBlock.FACING,player.getDirection().getOpposite()):
                    block.getStateForPlacement(new LegacyPlacement(player,stack,hit,point)));
            var previous=world.getBlockState(destination);
            if(placed!=null && world.setBlock(destination,placed,11)) {
                ShiftTeleportPlacement.afterPlacement(block,world,destination,previous);
                var actual=world.getBlockState(destination);
                if(actual.is(placed.getBlock())) {
                    BlockItem.updateCustomBlockEntityTag(world,player,destination,stack);
                    var tile=world.getBlockEntity(destination);if(tile!=null){ShiftTeleportPlacement.applyComponents(tile,stack);tile.setChanged();}
                    ShiftTeleportPlacement.placedBy(world,destination,actual,player,stack);
                    CriteriaTriggers.PLACED_BLOCK.trigger(player,destination,stack);
                }
            }
            // Original ignores placeBlockAt's return value; failure here does not enter the drop branch.
        } else world.addFreshEntity(new ItemEntity(world,point.x,point.y,point.z,stack.copyWithCount(1)));
        if(!player.isCreative()){if(stack.getCount()<=1)player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);else stack.shrink(1);}
        var targets=targets(player,experience);for(var target:targets)TPSkillHelper.attack(AbilityContext.of(player,skill),target,Balance.i("shift_teleport_context", "release_attack_1")+(Balance.i("shift_teleport_context", "release_attack_2")-Balance.i("shift_teleport_context", "release_attack_3"))*experience,false);
        world.playSound(player,player.blockPosition(),ACSounds.get("tp.tp_shift"),SoundSource.AMBIENT,.5f,1);
        state.addExperience(skill,(Balance.i("shift_teleport_context", "release_add_experience_1")+targets.size())*Balance.f("shift_teleport_context", "release_add_experience_2"));state.finishContext(skill,(int)(Balance.i("shift_teleport_context", "release_finish_context_1")+(Balance.i("shift_teleport_context", "release_finish_context_2")-Balance.i("shift_teleport_context", "release_finish_context_3"))*experience),0);performed=true;
    }
    private static final class LegacyPlacement extends BlockPlaceContext {
        LegacyPlacement(Player player,ItemStack stack,BlockHitResult hit,Vec3 point) {
            super(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(new Vec3(
                    hit.getBlockPos().getX()+(double)(float)point.x,hit.getBlockPos().getY()+(double)(float)point.y,hit.getBlockPos().getZ()+(double)(float)point.z),hit.getDirection(),hit.getBlockPos(),false));
            replaceClicked=true;
        }
        @Override public BlockPos getClickedPos(){return getHitResult().getBlockPos();}
    }
    public boolean performed(){return performed;}
    public void end(){if(ended)return;ended=true;send(END,false);audience.clear();}
    private void send(int phase,boolean effect){var packet=new ShiftTeleportPayload(id,player.getId(),phase,slot,request,experience,effect);for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);}
}
