"""Run production level-transition and strike methods against recording adapters."""
from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parent.parent
base = root / 'src/main/java/cn/academy'
strike = (base / 'ability/vanilla/vecmanip/skill/VectorStrikeContext.java').read_text(encoding='utf-8')
state = (base / 'ability/AbilityState.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

strike_methods = '\n'.join(method(strike, signature) for signature in (
    'public static int kind(AbilitySkill skill)',
    'public static int kind(AbilitySkill skill,int level)',
    'public static void knockback(', 'private float snapshot(', 'private static boolean withinBlast(', 'private void blast(',
))
level_method = method(state, 'public void setLevel(')
for full, short in (
    ('net.neoforged.neoforge.common.NeoForge.EVENT_BUS', 'BUS'),
    ('cn.academy.event.ability.SkillExpChangedEvent', 'SkillExpChangedEvent'),
    ('cn.academy.event.ability.LevelChangeEvent', 'LevelChangeEvent'),
):
    level_method = level_method.replace(full, short)

harness = r'''
import java.util.*;
import java.util.function.Predicate;
class DirectedShockUpgradeCheck {
    static final int SHOCK=0,GROUND=1,BLAST=2,ENHANCED_SHOCK=3,PERFORM=2,HIT=3;
    int kind=ENHANCED_SHOCK;float exp;double traceLength;Vec3 performedAt;
    boolean levelFiveShock,levelFiveBlast;
    final Player player=new Player();final Context ctx=new Context();
    final AbilitySkill skill=new AbilitySkill("dir_shock");
    record AbilitySkill(String name){String id(){return "vecmanip."+name;}}
    static class AbilityRegistry {static AbilitySkill skill(String id){return new AbilitySkill(id.substring(id.indexOf('.')+1));}}
    static class Bus {final List<Object> events=new ArrayList<>();void post(Object event){events.add(event);}}
    record SkillExpChangedEvent(Object owner,AbilitySkill skill){}
    record LevelChangeEvent(Object owner){}
    static class AbilityState {
        String category="vecmanip";int level=2,cooldown,capacityRefreshes;
        float expAddedThisLevel=1,bonusCp=100,bonusOverload=20,overload=10;
        boolean overloadLocked=true;Object owner=new Object();final Bus BUS=new Bus();
        final Map<String,Float> experience=new HashMap<>();
        boolean hasCategory(){return !category.isEmpty();}boolean serverOwned(){return true;}
        void recalculateCapacities(boolean refill){capacityRefreshes++;}
        float experience(String id){return experience.getOrDefault(id,0f);}
        void finishContext(AbilitySkill skill,int ticks,float gain){cooldown=ticks;}
        void addExperience(AbilitySkill skill,float amount){experience.merge(skill.id(),amount,Float::sum);}
        LEVEL_METHOD
    }
    record Vec3(double x,double y,double z) {
        static final Vec3 ZERO=new Vec3(0,0,0);
        Vec3 add(Vec3 v){return new Vec3(x+v.x,y+v.y,z+v.z);}
        Vec3 subtract(Vec3 v){return new Vec3(x-v.x,y-v.y,z-v.z);}
        Vec3 scale(double n){return new Vec3(x*n,y*n,z*n);}
        double length(){return Math.sqrt(x*x+y*y+z*z);}
        Vec3 normalize(){return length()<1e-9?ZERO:scale(1/length());}
        static Vec3 atLowerCornerOf(BlockPos p){return new Vec3(p.x,p.y,p.z);}
    }
    static class Entity {
        Vec3 pos,velocity=Vec3.ZERO;boolean hurtMarked,piercing;float damage;int positionChanges,knockbackTier=-1;
        Entity(Vec3 pos){this.pos=pos;}Vec3 position(){return pos;}Vec3 getEyePosition(){return pos;}
        int getId(){return System.identityHashCode(this);}void setDeltaMovement(Vec3 v){velocity=v;}
        void setDeltaMovement(double x,double y,double z){velocity=new Vec3(x,y,z);}
        double getX(){return pos.x;}double getY(){return pos.y;}double getZ(){return pos.z;}
        void setPos(double x,double y,double z){positionChanges++;pos=new Vec3(x,y,z);}
        double distanceToSqr(Vec3 v){Vec3 d=pos.subtract(v);return d.x*d.x+d.y*d.y+d.z*d.z;}
        Vec3 getLookAngle(){return new Vec3(1,0,0);}
        AABB getBoundingBox(){return new AABB(pos,pos);}
    }
    static class Player extends Entity {
        final World world=new World();Vec3 look=new Vec3(1,0,0);Player(){super(Vec3.ZERO);}
        World serverLevel(){return world;}Vec3 getLookAngle(){return look;}
        Vec3 getEyePosition(){return pos.add(new Vec3(0,1.6,0));}
    }
    static class AABB {
        final double minX,minY,minZ,maxX,maxY,maxZ;
        AABB(Vec3 min,Vec3 max){minX=min.x;minY=min.y;minZ=min.z;maxX=max.x;maxY=max.y;maxZ=max.z;}
        AABB inflate(double radius){return this;}
    }
    record BlockPos(int x,int y,int z){}
    static class World {
        final List<Entity> entities=new ArrayList<>();final Random random=new Random(1);int blockReads,blockRemovals;
        List<Entity> getEntities(Player player,AABB box,Predicate<Entity> predicate){return entities.stream().filter(predicate).toList();}
        BlockState getBlockState(BlockPos pos){blockReads++;return new BlockState();}
        void removeBlock(BlockPos pos,boolean update){blockRemovals++;}
        void addFreshEntity(ItemEntity entity){}Object getBlockEntity(BlockPos pos){return null;}
    }
    static class BlockState {float getDestroySpeed(World world,BlockPos pos){return 1;}Object getBlock(){return this;}}
    static class Block {static void dropResources(BlockState b,World w,BlockPos p,Object be,Player owner,ItemStack stack){}}
    static class ItemStack {static final ItemStack EMPTY=new ItemStack(null);ItemStack(Object block){}}
    static class ItemEntity {ItemEntity(World world,int x,int y,int z,ItemStack stack){}}
    static class HitResult {
        enum Type{MISS,BLOCK}Type getType(){return Type.MISS;}
        Vec3 location=Vec3.ZERO;Vec3 getLocation(){return location;}
    }
    static class EntityHitResult extends HitResult {Entity getEntity(){return null;}}
    static class BlockHitResult extends HitResult {BlockPos getBlockPos(){return new BlockPos(4,0,0);}}
    static class Context {
        void attack(Entity entity,float damage){entity.damage+=damage;}
        void attackIgnoreArmor(Entity entity,float damage){entity.piercing=true;attack(entity,damage);}
        boolean canBreakBlock(World world,BlockPos pos){return true;}
    }
    static class DirectedShockKnockback {
        static void start(Entity target,Vec3 direction,int tier){target.knockbackTier=tier;target.setDeltaMovement(direction);}
    }
    static class DirectedBlastUpgrade {
        static int calls;
        static void perform(Player player,Context context,BlockPos origin,Vec3 direction,float experience){calls++;}
    }
    static class ElectricAttackTrace {
        static HitResult traceBlocks(Player player,double distance){return new HitResult();}
    }
    HitResult trace(double distance){traceLength=distance;var hit=new HitResult();hit.location=player.getEyePosition().add(player.getLookAngle().scale(distance));return hit;}
    void send(int phase,Vec3 point,List<Integer> entities,List<BlockPos> blocks){if(phase==PERFORM)performedAt=point;}
    STRIKE_METHODS
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void close(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-5,message+": "+actual+" != "+expected);}
    public static void main(String[] args){
        AbilityState state=new AbilityState();
        state.experience.put("vecmanip.dir_shock",1f);state.experience.put("vecmanip.ground_shock",.7f);
        state.setLevel(3);
        close(state.experience("vecmanip.dir_shock"),0,"LV3 resets only directed shock");
        close(state.experience("vecmanip.ground_shock"),.7,"other skill proficiency preserved");
        check(state.BUS.events.stream().anyMatch(e->e instanceof SkillExpChangedEvent),"proficiency change event");
        state.experience.put("vecmanip.dir_shock",.6f);state.experience.put("vecmanip.vec_accel",.8f);
        state.setLevel(3);state.setLevel(4);
        close(state.experience("vecmanip.dir_shock"),.6,"same level and LV4 preserve directed shock retraining");
        close(state.experience("vecmanip.vec_accel"),0,"LV4 acceleration upgrade resets proficiency");
        for(String name:new String[]{"dir_blast","vec_reflection","blood_retro"})state.experience.put("vecmanip."+name,.8f);
        state.setLevel(5);
        for(String name:new String[]{"dir_shock","dir_blast","vec_reflection","blood_retro"})
            close(state.experience("vecmanip."+name),0,"LV5 upgrade resets "+name);
        close(state.experience("vecmanip.ground_shock"),.7,"unupgraded groundshock proficiency preserved");
        state.experience.put("vecmanip.dir_shock",.4f);state.setLevel(5);
        close(state.experience("vecmanip.dir_shock"),.4,"same LV5 preserves retraining");
        AbilityState skipped=new AbilityState();skipped.setLevel(5);check(skipped.level==5,"level skip accepted");
        AbilityState other=new AbilityState();other.category="electromaster";other.experience.put("vecmanip.dir_shock",.9f);other.setLevel(3);
        close(other.experience("vecmanip.dir_shock"),.9,"other category untouched");
        check(kind(new AbilitySkill("dir_shock"),2)==SHOCK,"LV2 keeps single hit");
        check(kind(new AbilitySkill("dir_shock"),3)==ENHANCED_SHOCK,"LV3 selects upgraded hit");
        check(kind(new AbilitySkill("dir_shock"),5)==ENHANCED_SHOCK,"LV5 keeps upgraded hit");
        check(kind(new AbilitySkill("dir_blast"),5)==BLAST,"separate blast remains blast");
        check(kind(new AbilitySkill("ground_shock"),5)==GROUND,"groundshock remains groundshock");
        float[] values={0,.2499f,.25f,.4999f,.5f,.7499f,.75f,.9999f,1};
        for(int i=0;i<values.length;i++){
            DirectedShockUpgradeCheck attack=new DirectedShockUpgradeCheck();attack.exp=values[i];
            AbilityState costs=new AbilityState();costs.experience.put(attack.skill.id(),values[i]);
            Entity center=new Entity(new Vec3(4,1.6,0)),edge=new Entity(new Vec3(7,1.6,0)),outside=new Entity(new Vec3(7.01,1.6,0));
            attack.player.world.entities.addAll(List.of(center,edge,outside));attack.blast(costs);
            close(center.damage,10+15*values[i],"blast damage curve");close(edge.damage,center.damage,"radius boundary hit");close(outside.damage,0,"outside radius excluded");
            check(center.knockbackTier==(int)(values[i]*4),"proficiency selects knockback tier");
            check(center.velocity.equals(attack.player.look),"knockback follows aim");
            check(center.positionChanges==0,"knockback does not teleport target");
            check(attack.performedAt.equals(new Vec3(4,1.6,0)),"air cast uses eye ray endpoint");
            check(attack.player.world.blockReads==0 && attack.player.world.blockRemovals==0,"no block destruction path");
            check(costs.cooldown==(int)(60-40*values[i]),"directed shock cooldown curve");close(attack.traceLength,4,"blast aiming distance");
        }
        DirectedShockUpgradeCheck air=new DirectedShockUpgradeCheck();AbilityState airCosts=new AbilityState();
        air.player.look=new Vec3(0,1,0);air.blast(airCosts);
        check(air.performedAt.equals(new Vec3(0,5.6,0)),"empty sky cast performs at eye endpoint");
        check(airCosts.cooldown==60,"empty sky cast enters cooldown");
        close(airCosts.experience(air.skill.id()),.001,"empty sky cast grants miss experience");
        for(float proficiency:new float[]{0,.25f,.5f,.75f,1}){
            var upgraded=new DirectedShockUpgradeCheck();upgraded.levelFiveShock=true;upgraded.exp=proficiency;
            var target=new Entity(new Vec3(4,1.6,0));upgraded.player.world.entities.add(target);
            var upgradedCosts=new AbilityState();upgraded.blast(upgradedCosts);
            close(target.damage,25+75*proficiency,"LV5 directed shock damage");
            check(target.piercing==(proficiency==1),"LV5 full proficiency pierces armor");
            check(target.knockbackTier==4 && upgradedCosts.cooldown==20,"LV5 fixed full knockback and cooldown");
            check(upgraded.player.world.blockRemovals==0,"LV5 directed shock preserves blocks");
        }
        DirectedShockUpgradeCheck blast=new DirectedShockUpgradeCheck();blast.kind=BLAST;
        Entity target=new Entity(new Vec3(4,0,0));blast.player.world.entities.add(target);AbilityState costs=new AbilityState();blast.blast(costs);
        check(target.damage==0 && target.knockbackTier==-1 && blast.player.world.blockRemovals>0,"separate blast destroys blocks without attacking entities");
        check(costs.cooldown==80,"blast initial cooldown");
        var fullBlast=new DirectedShockUpgradeCheck();fullBlast.kind=BLAST;fullBlast.exp=1;fullBlast.blast(new AbilityState());
        check(fullBlast.player.world.blockRemovals>blast.player.world.blockRemovals,"blast destruction radius grows with proficiency");
        var upgradedBlast=new DirectedShockUpgradeCheck();upgradedBlast.kind=BLAST;upgradedBlast.levelFiveBlast=true;
        var upgradedCosts=new AbilityState();upgradedBlast.blast(upgradedCosts);
        check(DirectedBlastUpgrade.calls==1 && upgradedCosts.cooldown==50,"LV5 blast delegates expanded destruction and fixes cooldown");
        System.out.println("PASS: level transitions, skill routing, aimed knockback tiers, eye-height casts, damage/range, LV5 damage/armor/cooldowns, block-only blast and growing destruction radius.");
    }
}
'''.replace('LEVEL_METHOD', level_method).replace('STRIKE_METHODS', strike_methods)
output = root / 'build/DirectedShockUpgradeCheck.java'
output.parent.mkdir(exist_ok=True)
output.write_text(harness, encoding='utf-8')
subprocess.run(['java', '--source', '21', '-Dfile.encoding=UTF-8', '-Duser.language=en', str(output)], check=True)

client = (base / 'client/render/VectorStrikeEffects.java').read_text(encoding='utf-8')
assert 'packet.kind()!=ENHANCED_SHOCK)VectorStrikeContext.knockback(owner,entity,packet.kind(),exp)' in client
assert 'target.setDeltaMovement(packet.point())' in client
assert 'VectorStrikeContext.kind(skill,state.level())' in client
assert 'selected.description(state)' in (base / 'client/screen/DeveloperScreen.java').read_text(encoding='utf-8')
for path in (root / 'src/main/resources/assets/academy/lang').glob('*.json'):
    data = json.loads(path.read_bytes())
    if 'ac.ability.vecmanip.dir_blast.desc' in data:
        assert data['ac.ability.vecmanip.dir_shock.lv3.desc']
print('PASS: client knockback, level-dependent presentation and all language JSON files.')
