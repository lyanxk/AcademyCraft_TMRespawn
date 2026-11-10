package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.AbilityContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/** LV5's forward blast and the twenty-block upward explosion above its broken blocks. */
public final class DirectedBlastUpgrade {
    private static final double BASE_RADIUS=Math.sqrt(6)+2;

    public static void perform(ServerPlayer player,AbilityContext context,BlockPos origin,Vec3 direction,float experience) {
        var world=player.serverLevel();
        double radius=BASE_RADIUS+(8.45-BASE_RADIUS)*experience;
        double depth=BASE_RADIUS+(20-BASE_RADIUS)*experience;
        int reach=(int)Math.ceil(Math.max(radius,depth));
        var primary=new ArrayList<BlockPos>();
        // At zero proficiency this is the old full-proficiency sphere. Its front stretches along the aim.
        for(int x=-reach;x<=reach;x++)for(int y=-reach;y<=reach;y++)for(int z=-reach;z<=reach;z++) {
            double along=x*direction.x+y*direction.y+z*direction.z;
            double across=x*x+y*y+z*z-along*along;
            double length=along>0?depth:radius;
            if(across/(radius*radius)+along*along/(length*length)>1)continue;
            var pos=origin.offset(x,y,z);var block=world.getBlockState(pos);
            float hardness=block.getDestroySpeed(world,pos);
            if(!block.isAir() && hardness>=0 && hardness<=55 && context.canBreakBlock(world,pos))primary.add(pos);
        }
        var destroyed=new ArrayList<BlockPos>();
        Map<Column,BitSet> upper=new HashMap<>();
        int bottom=origin.getY()-reach;
        for(var pos:primary) {
            if(world.removeBlock(pos,false)) {
                destroyed.add(pos);
                upper.computeIfAbsent(new Column(pos.getX(),pos.getZ()),key->new BitSet())
                        .set(pos.getY()-bottom+1,pos.getY()-bottom+21);
            }
        }
        for(var pos:destroyed)upper.get(new Column(pos.getX(),pos.getZ())).clear(pos.getY()-bottom);
        for(var entry:upper.entrySet()) {
            var column=entry.getKey();var heights=entry.getValue();
            for(int height=heights.nextSetBit(0);height>=0;height=heights.nextSetBit(height+1)) {
                var pos=new BlockPos(column.x(),bottom+height,column.z());
                var block=world.getBlockState(pos);float hardness=block.getDestroySpeed(world,pos);
                if(block.isAir() || hardness<0 || hardness>55 || !context.canBreakBlock(world,pos))continue;
                world.removeBlock(pos,false);
            }
        }
        if(!destroyed.isEmpty()) {
            var center=Vec3.atCenterOf(origin);
            world.playSound(null,center.x,center.y,center.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,4,1);
            for(double forward=0;forward<=depth;forward+=4) {
                var point=center.add(direction.scale(forward));
                for(int height=0;height<=20;height+=4)
                    world.sendParticles(ParticleTypes.EXPLOSION,point.x,point.y+height,point.z,3,radius*.4,1,radius*.4,0);
            }
        }
    }

    private record Column(int x,int z){}

    private DirectedBlastUpgrade(){}
}
