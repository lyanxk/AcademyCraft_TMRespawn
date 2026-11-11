package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import java.util.HashSet;

/** The two manually invoked 1.12.2 Explosion phases used by PlasmaCannon (no fire, smoking).
 * Keeps the original integer damage, float sampling, block traversal and per-item drops.
 * Modern block/entity hooks and loot tables provide the corresponding registered content.
 */
final class PlasmaExplosion extends Explosion {
    private final ServerLevel world;
    private final ServerPlayer caster;
    private final Vec3 point;
    private final float power;
    private final boolean breakBlocks;
    PlasmaExplosion(ServerPlayer caster,Vec3 point,float power,boolean breakBlocks) {
        super(caster.serverLevel(),caster,point.x,point.y,point.z,power,false,BlockInteraction.DESTROY_WITH_DECAY);
        this.world=caster.serverLevel();this.caster=caster;this.point=point;this.power=power;
        this.breakBlocks=breakBlocks;
    }
    @Override public void explode() {
        var affected=new HashSet<BlockPos>();
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
            if(x!=0 && x!=15 && y!=0 && y!=15 && z!=0 && z!=15)continue;
            double dx=x/15f*2-1,dy=y/15f*2-1,dz=z/15f*2-1;
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=length;dy/=length;dz/=length;
            float energy=power*(.7f+world.random.nextFloat()*.6f);
            double px=point.x,py=point.y,pz=point.z;
            for(;energy>0;energy-=.22500001f) {
                var pos=BlockPos.containing(px,py,pz);var state=world.getBlockState(pos);
                if(!state.isAir()) {
                    var fluid=state.getFluidState();
                    float resistance=Math.max(state.getExplosionResistance(world,pos,this),fluid.getExplosionResistance(world,pos,this));
                    resistance=caster.getBlockExplosionResistance(this,world,pos,state,fluid,resistance);
                    energy-=(resistance+.3f)*.3f;
                }
                if(energy>0 && caster.shouldBlockExplode(this,world,pos,state,energy))affected.add(pos);
                px+=dx*.3f;py+=dy*.3f;pz+=dz*.3f;
            }
        }
        if(breakBlocks)getToBlow().addAll(affected);
        float diameter=power*2;
        var entities=world.getEntities(caster,new AABB(Mth.floor(point.x-diameter-1),Mth.floor(point.y-diameter-1),Mth.floor(point.z-diameter-1),
                Mth.floor(point.x+diameter+1),Mth.floor(point.y+diameter+1),Mth.floor(point.z+diameter+1)));
        // Forge 1.12's sole phase-A event; listeners can adjust entities and the affected block list.
        EventHooks.onExplosionDetonate(world,this,entities,diameter);
        if(!breakBlocks)getToBlow().clear();
        float cap = cn.academy.data.Balance.f("plasma_cannon_context","blast_fixed_cap");
        float reference = cn.academy.data.Balance.f("plasma_cannon_context","blast_reference_health");
        for(var entity:entities) {
            if(entity.ignoreExplosion(this))continue;
            double distance=(double)(float)Math.sqrt(entity.distanceToSqr(point))/(double)diameter;if(distance>1)continue;
            double dx=entity.getX()-point.x,dy=entity.getEyeY()-point.y,dz=entity.getZ()-point.z;
            double length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length==0)continue;
            dx/=length;dy/=length;dz/=length;
            double impact=(1-distance)*exposure(entity);
            float rawDamage = (int)((impact*impact+impact)/2*7*diameter+1);
            float damage = rawDamage;
            if (rawDamage > cap) {
                var hit = new cn.academy.ability.SkillHitDamage(cap, (rawDamage-cap)/reference);
                damage = hit.against(entity);
            }
            entity.hurt(world.damageSources().explosion(caster,caster),damage);
            double knockback=impact;
            if(entity instanceof LivingEntity living) {
                var protection=world.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.BLAST_PROTECTION);
                int level=EnchantmentHelper.getEnchantmentLevel(protection,living);
                if(level>0)knockback-=Mth.floor(knockback*(level*.15f));
            }
            entity.setDeltaMovement(entity.getDeltaMovement().add(dx*knockback,dy*knockback,dz*knockback));
            if(entity instanceof Player player && !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying))
                getHitPlayers().put(player,new Vec3(dx*impact,dy*impact,dz*impact));
        }
    }
    private float exposure(Entity entity) {
        var box=entity.getBoundingBox();
        double sx=1/((box.maxX-box.minX)*2+1),sy=1/((box.maxY-box.minY)*2+1),sz=1/((box.maxZ-box.minZ)*2+1);
        if(sx<0 || sy<0 || sz<0)return 0;
        double ox=(1-Math.floor(1/sx)*sx)/2,oz=(1-Math.floor(1/sz)*sz)/2;
        int visible=0,total=0;
        // World.getBlockDensity uses FLOAT loop variables, unlike the modern double loops.
        for(float x=0;x<=1;x=(float)(x+sx))for(float y=0;y<=1;y=(float)(y+sy))for(float z=0;z<=1;z=(float)(z+sz)) {
            var sample=new Vec3(box.minX+(box.maxX-box.minX)*x+ox,box.minY+(box.maxY-box.minY)*y,box.minZ+(box.maxZ-box.minZ)*z+oz);
            // World.rayTraceBlocks considers selection shapes even for non-solid plants; liquids do not collide.
            if(ElectricAttackTrace.traceBlockSegment(entity,sample,point,state->!state.isAir() && !state.liquid()).getType()==HitResult.Type.MISS)visible++;
            total++;
        }
        return (float)visible/total;
    }
    @Override public void finalizeExplosion(boolean particles) {
        world.playSound(null,point.x,point.y,point.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,4,
                (1+(world.random.nextFloat()-world.random.nextFloat())*.2f)*.7f);
        world.addParticle(power>=2?ParticleTypes.EXPLOSION_EMITTER:ParticleTypes.EXPLOSION,point.x,point.y,point.z,1,0,0);
        if(!breakBlocks)return;
        // No shuffle, no combined drop list. The original processes each block before the next one.
        for(var pos:getToBlow()) {
            var state=world.getBlockState(pos);
            if(particles)particlePair(pos);
            if(state.isAir())continue;
            if(state.canDropFromExplosion(world,pos,this)) {
                var builder=new LootParams.Builder(world).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos))
                        .withParameter(LootContextParams.TOOL,ItemStack.EMPTY)
                        .withOptionalParameter(LootContextParams.THIS_ENTITY,caster)
                        .withOptionalParameter(LootContextParams.BLOCK_ENTITY,state.hasBlockEntity()?world.getBlockEntity(pos):null);
                // No EXPLOSION_RADIUS in loot: the old per-drop roll is applied below, once per item.
                for(var stack:state.getDrops(builder))for(int i=0;i<stack.getCount();i++)if(world.random.nextFloat()<=1f/power) {
                    if(!world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))continue;
                    double x=pos.getX()+(double)(world.random.nextFloat()*.5f)+.25;
                    double y=pos.getY()+(double)(world.random.nextFloat()*.5f)+.25;
                    double z=pos.getZ()+(double)(world.random.nextFloat()*.5f)+.25;
                    var item=new ItemEntity(world,x,y,z,stack.copyWithCount(1));item.setDefaultPickUpDelay();world.addFreshEntity(item);
                }
            }
            state.onBlockExploded(world,pos,this);
        }
    }
    private void particlePair(BlockPos pos) {
        // ServerLevel.addParticle is intentionally local/no-op, but the original still consumes these five random draws.
        double x=(float)pos.getX()+world.random.nextFloat(),y=(float)pos.getY()+world.random.nextFloat(),z=(float)pos.getZ()+world.random.nextFloat();
        double dx=x-point.x,dy=y-point.y,dz=z-point.z,length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        dx/=length;dy/=length;dz/=length;
        double speed=.5/(length/power+.1)*(world.random.nextFloat()*world.random.nextFloat()+.3f);
        dx*=speed;dy*=speed;dz*=speed;
        world.addParticle(ParticleTypes.POOF,(x+point.x)/2,(y+point.y)/2,(z+point.z)/2,dx,dy,dz);
        world.addParticle(ParticleTypes.SMOKE,x,y,z,dx,dy,dz);
    }
}
