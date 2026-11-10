"""Exercise production release/landing methods with recording world and player adapters."""
from pathlib import Path
import subprocess
from gameplay_defaults import resolve_parameters

root = Path(__file__).resolve().parent.parent
source = (root / 'src/main/java/cn/academy/ability/vanilla/vecmanip/skill/VectorStrikeContext.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return resolve_parameters(source[start:end]).replace('net.minecraft.core.Direction', 'Direction')

methods = '\n'.join(method(s) for s in [
    'public boolean release(', 'private Vec3 findGroundLanding(', 'public static boolean accepts(',
    'private float snapshot(', 'private float live(',
])
harness = '''
import java.util.*;
class GroundShockCheck {
    static final int GROUND=1,SHOCK=0,BLAST=2,ENHANCED_SHOCK=3,PERFORM=2,HIT=3;
    int kind=GROUND,impacts,blasts;float exp;boolean ended,released,performed,levelFiveShock,levelFiveBlast;
    final Player player=new Player();final Skill skill=new Skill();final Context ctx=new Context();
    record Vec3(double x,double y,double z){
        static final Vec3 ZERO=new Vec3(0,0,0);
        Vec3 add(Vec3 v){return new Vec3(x+v.x,y+v.y,z+v.z);}
        Vec3 subtract(Vec3 v){return new Vec3(x-v.x,y-v.y,z-v.z);}
        Vec3 scale(double k){return new Vec3(x*k,y*k,z*k);}
        Vec3 normalize(){return this;}
    }
    static class Entity {
        Vec3 pos=new Vec3(2.25,100,3.75),velocity=new Vec3(.1,-3,.2);boolean hurtMarked;
        Vec3 position(){return pos;}Vec3 getDeltaMovement(){return velocity;}
        void setDeltaMovement(Vec3 value){velocity=value;}int getId(){return 1;}
    }
    static class Player extends Entity {
        boolean grounded,passenger=true,gliding=true;float fallDistance=25,yaw=17,pitch=90;int teleports,abilityUpdates;
        final World world=new World();final Abilities abilities=new Abilities();
        boolean onGround(){return grounded;}void setOnGround(boolean value){grounded=value;}
        boolean isPassenger(){return passenger;}void stopRiding(){passenger=false;}
        boolean isFallFlying(){return gliding;}void stopFallFlying(){gliding=false;}
        boolean isCreative(){return false;}Abilities getAbilities(){return abilities;}
        void resetFallDistance(){fallDistance=0;}void onUpdateAbilities(){abilityUpdates++;}
        void teleportTo(double x,double y,double z){teleports++;pos=new Vec3(x,y,z);}
        World serverLevel(){return world;}Box getBoundingBox(){return new Box(Vec3.ZERO);}
    }
    static class Abilities {boolean flying=true;}
    record Box(Vec3 offset){Box move(Vec3 delta){return new Box(delta);}}
    enum Direction {UP,DOWN}
    static class HitResult {enum Type {BLOCK,MISS} Type getType(){return Type.MISS;}}
    static class BlockHitResult extends HitResult {
        Vec3 point=new Vec3(2.25,64,3.75);Direction face=Direction.UP;Type type=Type.BLOCK;
        Type getType(){return type;}Direction getDirection(){return face;}Vec3 getLocation(){return point;}
    }
    static class EntityHitResult extends HitResult {Entity getEntity(){return new Entity();}}
    record ClipContext(Vec3 from,Vec3 to,Block block,Fluid fluid,Player player){
        enum Block {COLLIDER} enum Fluid {NONE}
    }
    static class World {
        final BlockHitResult hit=new BlockHitResult();boolean clear=true;int traces;ClipContext lastTrace;Box checkedBox;
        int getMinBuildHeight(){return -64;}
        BlockHitResult clip(ClipContext trace){traces++;lastTrace=trace;return hit;}
        boolean noCollision(Player player,Box box){checkedBox=box;return clear;}
    }
    static class Skill {String id(){return "ground_shock";}}
    static class AbilityState {
        boolean canPay=true;int payments,cooldowns;float paidCp,paidOverload;
        boolean consumeRaw(Skill skill,float cp,float overload,boolean creative){
            if(!canPay)return false;payments++;paidCp=cp;paidOverload=overload;return true;
        }
        float experience(String id){return 0;}
        void addExperience(Skill skill,float amount){}
        void finishContext(Skill skill,int cooldown,float experience){cooldowns++;}
    }
    static class Context {void attack(Entity target,float damage){}}
    HitResult trace(double distance){return new HitResult();}
    static void knockback(Player owner,Entity target,int kind,float exp){}
    void send(int phase,Vec3 point,List<Integer> targets,List<Object> blocks){}
    void blast(AbilityState state){blasts++;}
    void ground(AbilityState state){
        check(player.onGround(),"impact must happen after landing");impacts++;
        state.finishContext(skill,80,0);
    }
    METHODS
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        var skill=new GroundShockCheck();var state=new AbilityState();
        check(!skill.release(state,0),"ground strike must fire at zero charge and terminate immediately");
        check(skill.performed && skill.impacts==1 && skill.player.teleports==1,"air release teleports then impacts once");
        check(skill.player.pos.equals(new Vec3(2.25,64,3.75)),"landing preserves horizontal position");
        check(skill.player.velocity.equals(Vec3.ZERO) && skill.player.fallDistance==0,"clear falling momentum/distance");
        check(!skill.player.passenger && !skill.player.gliding && !skill.player.abilities.flying,"end riding/gliding/flight");
        check(skill.player.yaw==17 && skill.player.pitch==90,"preserve view direction");
        check(state.payments==1 && state.cooldowns==1 && state.paidCp==80 && state.paidOverload==15,"single payment/cooldown");
        check(skill.player.world.lastTrace.to().equals(new Vec3(2.25,-65,3.75)),"trace directly down through world height");
        check(skill.player.world.lastTrace.block()==ClipContext.Block.COLLIDER
                && skill.player.world.lastTrace.fluid()==ClipContext.Fluid.NONE,"use solid collision surface");
        check(skill.player.world.checkedBox.offset().equals(new Vec3(0,-36,0)),"check destination player volume");
        skill.release(state,30);
        check(state.payments==1 && skill.impacts==1 && skill.player.teleports==1,"duplicate release does nothing");
        var standing=new GroundShockCheck();standing.player.grounded=true;var standingState=new AbilityState();
        standing.release(standingState,0);
        check(standing.impacts==1 && standing.player.teleports==0 && standing.player.world.traces==0,"standing release stays in place");
        var slab=new GroundShockCheck();slab.player.world.hit.point=new Vec3(2.25,64.5,3.75);
        slab.release(new AbilityState(),0);
        check(slab.player.pos.y()==64.5,"preserve fractional slab landing height");
        for(int scenario=0;scenario<5;scenario++){
            var rejected=new GroundShockCheck();var rejectedState=new AbilityState();
            if(scenario==0)rejected.player.world.hit.type=HitResult.Type.MISS;
            if(scenario==1)rejected.player.world.hit.face=Direction.DOWN;
            if(scenario==2)rejected.player.world.clear=false;
            if(scenario==3)rejectedState.canPay=false;
            rejected.release(rejectedState,scenario==4?-1:0);
            check(!rejected.performed && rejected.impacts==0 && rejected.player.teleports==0
                    && rejectedState.payments==0 && rejectedState.cooldowns==0,"invalid landing/payment/charge rejected: "+scenario);
        }
        for(int kind:new int[]{SHOCK,BLAST})
            check(!accepts(kind,0) && !accepts(kind,6) && accepts(kind,7) && accepts(kind,49) && !accepts(kind,50),
                    "other vector punches retain their release window");
        System.out.println("PASS: zero-charge groundshock, airborne instant landing, one impact/payment, grounded use, fractional surfaces, rejected invalid landing/CP/ticks, unchanged punch timing/view.");
    }
}
'''.replace('    METHODS', methods)
output = root / 'build/GroundShockCheck.java'
output.parent.mkdir(exist_ok=True)
output.write_text(harness, encoding='utf-8')
subprocess.run(['java', '-Dfile.encoding=UTF-8', '-Duser.language=en', str(output)], check=True)
ground = method('private void ground(')
assert not any(token in ground for token in ['setBlock', 'removeBlock', 'breakGround', 'dropResources'])
client = (root / 'src/main/java/cn/academy/client/render/VectorStrikeEffects.java').read_text(encoding='utf-8')
assert 'setXRot' not in client and 'setYRot' not in client and 'SLASH' not in client
assert 'groundParticles(packet.blocks())' in client
assert 'diving' not in source and 'GROUND_DIVE_SPEED' not in source
manager = (root / 'src/main/java/cn/academy/ability/AbilityContextManager.java').read_text(encoding='utf-8')
key_down = manager[manager.index('public static void keyDown(ServerPlayer player, AbilityState state, int slot, int clientRequestId)'):manager.index('public static void vectorStrikeInput(')]
assert 'try {session.vectorStrike.release(state,0);}' in key_down
assert 'finally {terminate(player,state,session,false,false);cleanup(player);}' in key_down
assert 'case "ground_shock" -> 0;' in manager
assert 'strike.released=strike.kind==GROUND;' in client
print('PASS: no block mutation, no camera rotation, ground particles retained, no intermediate dive state.')
