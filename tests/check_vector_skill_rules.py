from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parent.parent
skills = root / 'src/main/java/cn/academy/ability/vanilla/vecmanip/skill'

def method(path, signature):
    source = path.read_text(encoding='utf-8')
    start = source.index(signature)
    body = source.index('{', start)
    depth = 1
    end = body + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join([
    method(skills / 'VectorImpulseContext.java', 'public static void attackBloodRetrograde('),
    method(skills / 'VectorDefenseContext.java', 'public static boolean cancelIncoming('),
    method(skills / 'VectorDefenseContext.java', 'private record PendingCounter(').replace('VectorDefenseContext', 'Defense'),
    method(skills / 'VectorDefenseContext.java', 'public static void queueCounterAttack('),
    method(skills / 'VectorDefenseContext.java', 'public static void processCounterAttacks('),
    method(skills / 'VectorDefenseContext.java', 'private static void performCounter('),
    method(skills / 'VectorDefenseContext.java', 'public static void clearPendingCounters('),
    method(skills / 'VectorDefenseContext.java', 'public static float reduceDamage('),
    method(root / 'src/main/java/cn/academy/event/ACCommonEvents.java',
           'public static void onIncomingDamage(').replace(
               'cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext', 'VectorRulesCheck'),
    method(root / 'src/main/java/cn/academy/event/ACCommonEvents.java',
           'public static void onServerTick(').replace(
               'cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext', 'VectorRulesCheck').replace(
               'cn.academy.ability.vanilla.vecmanip.skill.DirectedShockKnockback', 'DirectedShockKnockback'),
    method(root / 'src/main/java/cn/academy/event/ACCommonEvents.java',
           'public static void onServerStopped(').replace(
               'cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext', 'VectorRulesCheck').replace(
               'cn.academy.ability.vanilla.vecmanip.skill.DirectedShockKnockback', 'DirectedShockKnockback'),
])
# Execute the actual production method bodies against small recording adapters.
# Minecraft event dispatch and live combat still require an in-game check.
harness = '''
import java.util.*;
class VectorRulesCheck {
    static final int PLAY=0,EFFECT=1,SYNC_VELOCITY=6;
    static final List<Defense> ACTIVE=new ArrayList<>();
    static final Map<MinecraftServer,ArrayDeque<PendingCounter>> PENDING_COUNTERS=new IdentityHashMap<>();
    static final Map<MinecraftServer,Set<Entity>> PENDING_VELOCITIES=new IdentityHashMap<>();
    static final MinecraftServer SERVER=new MinecraftServer();
    record VectorDefensePayload(int context,int caster,boolean reflection,int phase,int target,Object point){}
    static class PacketDistributor {
        static int sends;
        static void sendToPlayersTrackingEntityAndSelf(Entity entity,VectorDefensePayload payload){sends++;}
    }
    static class DirectedShockKnockback {
        static void sync(MinecraftServer server){}
        static void clear(MinecraftServer server){}
    }
    static class MinecraftServer {int tick;int getTickCount(){return tick;}}
    static class ServerTickEvent {record Post(MinecraftServer server){MinecraftServer getServer(){return server;}}}
    record ServerStoppedEvent(MinecraftServer server){MinecraftServer getServer(){return server;}}
    static class Tags {static class EntityTypes {static final Object BOSSES=new Object();}}
    static class EntityType {
        boolean boss;
        boolean is(Object tag){return boss && tag==Tags.EntityTypes.BOSSES;}
    }
    static class Entity {
        final EntityType type=new EntityType();
        EntityType getType(){return type;}
        Object world=SERVER;boolean removed;
        Object position(){return this;}
        Object level(){return world;}
        boolean isRemoved(){return removed;}
        int getId(){return System.identityHashCode(this);}
        Object getDeltaMovement(){return this;}
    }
    static class LivingEntity extends Entity {
        float maxHealth=200,health=200;
        boolean alive=true;
        float getMaxHealth(){return maxHealth;}
        boolean isAlive(){return alive;}
    }
    static class DamageSource {
        final Entity direct;
        DamageSource(Entity direct){this.direct=direct;}
        Entity getDirectEntity(){return direct;}
    }
    static class SkillDamageSource extends DamageSource {
        SkillDamageSource(Entity direct){super(direct);}
    }
    static class LivingIncomingDamageEvent {
        final LivingEntity victim;final DamageSource source;final float amount;
        boolean canceled;
        LivingIncomingDamageEvent(LivingEntity victim,DamageSource source,float amount){
            this.victim=victim;this.source=source;this.amount=amount;
        }
        LivingEntity getEntity(){return victim;}
        DamageSource getSource(){return source;}
        float getAmount(){return amount;}
        void setCanceled(boolean value){canceled=value;}
    }
    static class AbilityContext {
        static AbilityContext counter=new AbilityContext();
        static AbilityContext of(ServerPlayer player,Object skill){return counter;}
        float damage; boolean piercing; int hits;
        LivingEntity lastTarget;Runnable hook;boolean applyDamage;int depth,maxDepth;
        void attack(LivingEntity target,float amount){
            damage=amount;piercing=false;hits++;lastTarget=target;depth++;maxDepth=Math.max(maxDepth,depth);
            try{
                if(hook!=null)hook.run(); // Guardian thorns happen BEFORE health loss.
                if(applyDamage){target.health-=amount;target.alive=target.health>0;}
            }finally{depth--;}
        }
        void attackIgnoreArmor(LivingEntity target,float amount){damage=amount;piercing=true;hits++;}
    }
    static class ServerPlayer extends LivingEntity {
        MinecraftServer server=SERVER;boolean disconnected;
        final AbilityState state=new AbilityState();
        boolean hasDisconnected(){return disconnected;}
        boolean isCreative(){return false;}
        Object position(){return this;}
    }
    static class AbilityState {
        float experience,cp=100; int payments,gains,saves;
        static AbilityState load(ServerPlayer player){return player.state;}
        float cp(){return cp;}
        void consumeRaw(Object skill,float amount,float overload,boolean creative){cp-=amount;payments++;}
        void consumeForce(Object skill,float amount,float overload,boolean creative){cp-=amount;payments++;}
        void addExperience(Object skill,float gain){gains++;}
        void save(ServerPlayer player){saves++;}
    }
    static class Defense {
        boolean ended,reflection; ServerPlayer player; Object skill=new Object(); int effects;
        Defense(ServerPlayer player,boolean reflection){this.player=player;this.reflection=reflection;}
        float live(AbilityState state,float low,float high){return low+(high-low)*state.experience;}
        void send(int phase,Object entity,Object point){effects++;}
    }
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static void close(float actual,float expected,String label){check(Math.abs(actual-expected)<.001f,label+": "+actual);}
    static void nextTick(MinecraftServer server){server.tick++;onServerTick(new ServerTickEvent.Post(server));}
    static void nextTick(){nextTick(SERVER);}
    static LivingIncomingDamageEvent hit(ServerPlayer victim,Entity attacker,float damage){
        var event=new LivingIncomingDamageEvent(victim,new DamageSource(attacker),damage);
        onIncomingDamage(event);return event;
    }
    METHODS
    public static void main(String[] args){
        float[] exp={0,.25f,Math.nextDown(.5f),.5f,.75f,Math.nextDown(1f),1};
        float[] damage={40,50,60,60,70,80,280};
        for(int i=0;i<exp.length;i++){
            var context=new AbilityContext();
            attackBloodRetrograde(context,new LivingEntity(),exp[i],false);
            close(context.damage,damage[i],"blood damage at "+exp[i]);
            check(context.piercing==(i>=3),"armor threshold at "+exp[i]);
            check(context.hits==1,"max-health bonus must not cause a second immunity-window hit");
        }
        var boss=new LivingEntity();boss.type.boss=true;
        for(int i=0;i<exp.length;i++){
            var context=new AbilityContext();attackBloodRetrograde(context,boss,exp[i],false);
            close(context.damage,i==exp.length-1?90:damage[i],"boss damage at "+exp[i]);
            check(context.piercing==(i>=3) && context.hits==1,"boss armor threshold and single hit");
        }
        boss.maxHealth=1000;boss.health=1;
        var bossContext=new AbilityContext();attackBloodRetrograde(bossContext,boss,1,false);
        close(bossContext.damage,130,"boss bonus is 5% of MAX health, plus full base damage");
        for(int tier=0;tier<=4;tier++){
            var context=new AbilityContext();attackBloodRetrograde(context,new LivingEntity(),tier/4f,true);
            close(context.damage,280+20*tier,"LV5 blood damage tier "+tier);
            check(context.piercing && context.hits==1,"LV5 blood pierces with one hit");
            var enhancedBoss=new AbilityContext();attackBloodRetrograde(enhancedBoss,boss,tier/4f,true);
            close(enhancedBoss.damage,130+32.5f*tier,"LV5 boss max-health bonus tier "+tier);
        }
        PENDING_VELOCITIES.put(SERVER,new HashSet<>(List.of(new Entity())));
        processCounterAttacks(SERVER);processCounterAttacks(SERVER);
        check(PacketDistributor.sends==1 && PENDING_VELOCITIES.isEmpty(),"velocity sync drains once");
        PENDING_VELOCITIES.put(SERVER,new HashSet<>(List.of(new Entity())));
        clearPendingCounters(SERVER);
        check(PENDING_VELOCITIES.isEmpty(),"server cleanup releases pending velocities");
        var first=new ServerPlayer();var victim=new ServerPlayer();var bystander=new ServerPlayer();
        var unrelated=new Defense(first,false);ACTIVE.add(unrelated);
        close(reduceDamage(victim,10),10,"unrelated player must receive no mitigation");
        check(first.state.payments==0 && first.state.gains==0,"unrelated caster must not pay or gain XP");
        var own=new Defense(victim,false);ACTIVE.add(own);
        close(reduceDamage(victim,10),6,"victim's own low-proficiency reduction");
        victim.state.experience=1;
        close(reduceDamage(victim,10),1,"victim's own full-proficiency reduction");
        check(first.state.payments==0 && unrelated.effects==0,"first global context must remain untouched");
        check(victim.state.payments==2 && victim.state.gains==2 && own.effects==2,"correct caster side effects");
        own.ended=true;
        close(reduceDamage(victim,10),10,"ended deviation must not reduce damage");
        var reflection=new Defense(victim,true);ACTIVE.add(reflection);
        victim.state.experience=0;
        check(cancelIncoming(victim),"reflection at zero proficiency");
        check(!cancelIncoming(bystander),"reflection must not protect another player");
        for(float amount:new float[]{.001f,.5f,1,10,10000,Float.MAX_VALUE})
            close(reduceDamage(victim,amount),0,"reflection must cancel damage "+amount);
        check(victim.state.payments==2,"reflection cancellation must not consume deviation CP");
        reflection.ended=true;
        check(!cancelIncoming(victim),"ended reflection must not cancel damage");
        close(reduceDamage(victim,10),10,"ended reflection must not mitigate damage");
        System.out.println("PASS: blood damage/armor boundaries, boss 5% max-health bonus and single hit; victim isolation; ended contexts; unconditional reflection.");
        ACTIVE.clear();
        var defender=new ServerPlayer();var attacker=new ServerPlayer();
        var shield=new Defense(defender,true);ACTIVE.add(shield);
        var incoming=new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10);
        AbilityContext.counter.hook=()->check(incoming.canceled,"cancel original hit before countering");
        onIncomingDamage(incoming);
        check(incoming.canceled,"counter must preserve immunity");
        check(AbilityContext.counter.hits==0 && defender.state.payments==0,"damage event must only queue");
        onServerTick(new ServerTickEvent.Post(SERVER));
        check(AbilityContext.counter.hits==0,"current tick must not execute the counter");
        nextTick();
        close(AbilityContext.counter.damage,6,"low-proficiency counter damage");
        check(AbilityContext.counter.hits==1 && AbilityContext.counter.lastTarget==attacker,"hit original attacker exactly once");
        close(defender.state.cp,-100,"original forced counter cost");
        check(defender.state.gains==1 && shield.effects==1,"counter rewards and effects");
        AbilityContext.counter.hook=null;
        defender.state.experience=1;
        onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10));
        nextTick();
        close(AbilityContext.counter.damage,12,"full-proficiency counter damage");
        close(defender.state.cp,-250,"full-proficiency counter cost");
        var attackerShield=new Defense(attacker,true);ACTIVE.add(attackerShield);
        int hits=AbilityContext.counter.hits,payments=defender.state.payments,gains=defender.state.gains;
        var mutual=new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10);
        onIncomingDamage(mutual);
        check(mutual.canceled && AbilityContext.counter.hits==hits,"two reflectors must only cancel damage");
        check(defender.state.payments==payments && defender.state.gains==gains,"no counter costs or XP against reflector");
        var reverse=new LivingIncomingDamageEvent(attacker,new DamageSource(defender),10);
        onIncomingDamage(reverse);
        check(reverse.canceled && AbilityContext.counter.hits==hits,"mutual protection works in both directions");
        nextTick();
        check(AbilityContext.counter.hits==hits && PENDING_COUNTERS.isEmpty(),"two reflectors must not queue counters");
        attackerShield.ended=true;
        onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10));
        nextTick();
        check(AbilityContext.counter.hits==hits+1,"ended attacker reflection must not block a counter");
        hits=AbilityContext.counter.hits;
        for(DamageSource source:new DamageSource[]{new DamageSource(null),new DamageSource(new Entity()),
                new SkillDamageSource(attacker),new DamageSource(defender)}){
            var skipped=new LivingIncomingDamageEvent(defender,source,10);
            onIncomingDamage(skipped);
            check(skipped.canceled && AbilityContext.counter.hits==hits,"environment/projectile/skill/self must not counter");
        }
        attacker.alive=false;
        onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10));
        check(AbilityContext.counter.hits==hits,"dead attackers must not counter");
        attacker.alive=true;
        for(float amount:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),amount));
        check(AbilityContext.counter.hits==hits,"invalid damage must not counter");
        nextTick();
        check(AbilityContext.counter.hits==hits,"excluded hits must leave no delayed counter");
        // Each nested passive retaliation is allowed, but only in the following tick.
        int[] passiveHits={3};
        int paymentsBeforeRecursion=defender.state.payments,gainsBeforeRecursion=defender.state.gains;
        AbilityContext.counter.hook=()->{
            if(passiveHits[0]--<=0)return;
            var nested=new LivingIncomingDamageEvent(defender,new DamageSource(attacker),2);
            onIncomingDamage(nested);
            check(nested.canceled,"recursive damage still cancels");
        };
        onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10));
        for(int i=1;i<=4;i++){
            nextTick();
            check(AbilityContext.counter.hits==hits+i,"exactly one chain link per tick");
            onServerTick(new ServerTickEvent.Post(SERVER));
            check(AbilityContext.counter.hits==hits+i,"same-tick processing cannot drain new links");
        }
        check(AbilityContext.counter.maxDepth==1,"chained attacks must not nest the Java stack");
        check(defender.state.payments==paymentsBeforeRecursion+4 && defender.state.gains==gainsBeforeRecursion+4,
                "each nested counter applies its own cost and XP");
        AbilityContext.counter.hook=()->{throw new IllegalStateException("simulated attack callback failure");};
        try{
            onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10));
            nextTick();
            throw new AssertionError("expected simulated failure");
        }catch(IllegalStateException expected){}
        AbilityContext.counter.hook=null;
        hits=AbilityContext.counter.hits;
        onIncomingDamage(new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10));
        nextTick();
        check(AbilityContext.counter.hits==hits+1,"counter still works after callback failure");
        shield.ended=true;
        var inactive=new LivingIncomingDamageEvent(defender,new DamageSource(attacker),10);
        onIncomingDamage(inactive);
        check(!inactive.canceled && AbilityContext.counter.hits==hits+1,"inactive defender has no immunity or counter");
        shield.ended=false;
        hits=AbilityContext.counter.hits;payments=defender.state.payments;
        hit(defender,attacker,10);attackerShield.ended=false;nextTick();
        check(AbilityContext.counter.hits==hits && defender.state.payments==payments,
                "attacker activating reflection before execution must cancel without payment");
        attackerShield.ended=true;
        hit(defender,attacker,10);shield.ended=true;nextTick();shield.ended=false;
        check(AbilityContext.counter.hits==hits,"closing own reflection cancels its queued counters");
        hit(defender,attacker,10);attacker.alive=false;nextTick();attacker.alive=true;
        hit(defender,attacker,10);attacker.removed=true;nextTick();attacker.removed=false;
        hit(defender,attacker,10);attacker.disconnected=true;nextTick();attacker.disconnected=false;
        hit(defender,attacker,10);attacker.world=new Object();nextTick();attacker.world=SERVER;
        hit(defender,attacker,10);defender.disconnected=true;nextTick();defender.disconnected=false;
        hit(defender,attacker,10);defender.alive=false;nextTick();defender.alive=true;
        check(AbilityContext.counter.hits==hits && defender.state.payments==payments,
                "dead, removed, disconnected or cross-dimension targets must be discarded for free");
        // Reproduce Guardian.hurt: thorns first, then health loss, with no artificial chain limit.
        var guardian=new LivingEntity();guardian.health=10;
        AbilityContext.counter.applyDamage=true;
        AbilityContext.counter.hook=()->hit(defender,guardian,2);
        hit(defender,guardian,2);
        for(int i=0;i<10 && !PENDING_COUNTERS.isEmpty();i++)nextTick();
        check(!guardian.isAlive(),"guardian must reach its health deduction and die");
        check(PENDING_COUNTERS.isEmpty() && AbilityContext.counter.maxDepth==1,
                "guardian chain must end without stack recursion");
        // A lethal initial hit can trigger thorns, but the delayed counter is discarded after death.
        guardian.alive=true;guardian.health=1;
        AbilityContext.counter.attack(guardian,40);
        hits=AbilityContext.counter.hits;nextTick();
        check(AbilityContext.counter.hits==hits && PENDING_COUNTERS.isEmpty(),"no counter against a guardian killed by the initial hit");
        AbilityContext.counter.hook=null;AbilityContext.counter.applyDamage=false;
        var secondServer=new MinecraftServer();
        var secondDefender=new ServerPlayer();secondDefender.server=secondServer;secondDefender.world=secondServer;
        var secondAttacker=new LivingEntity();secondAttacker.world=secondServer;
        ACTIVE.add(new Defense(secondDefender,true));
        hit(secondDefender,secondAttacker,10);nextTick();
        check(AbilityContext.counter.hits==hits,"server queues must be isolated");
        nextTick(secondServer);
        check(AbilityContext.counter.hits==hits+1,"other server runs its own next-tick counter");
        hits=AbilityContext.counter.hits;
        hit(defender,attacker,10);onServerStopped(new ServerStoppedEvent(SERVER));nextTick();
        check(AbilityContext.counter.hits==hits && PENDING_COUNTERS.isEmpty(),"server stop clears pending references");
        SERVER.tick=Integer.MAX_VALUE;hit(defender,attacker,10);
        onServerTick(new ServerTickEvent.Post(SERVER));
        check(AbilityContext.counter.hits==hits,"tick wrap must not run early");
        nextTick();check(AbilityContext.counter.hits==hits+1,"tick wrap must still run next tick");
        hits=AbilityContext.counter.hits;
        for(int i=0;i<10000;i++)hit(defender,attacker,1);
        check(AbilityContext.counter.hits==hits,"burst must remain queued");
        nextTick();
        check(AbilityContext.counter.hits==hits+10000 && PENDING_COUNTERS.isEmpty(),"burst drains once and releases empty queues");
        System.out.println("PASS: next-tick timing, chained thorns with health loss, reflector recheck, invalidation, server isolation/cleanup, tick wrap and 10000-hit burst.");
    }
}
'''.replace('    METHODS', methods)
output = root / 'build/VectorRulesCheck.java'
output.write_text(harness, encoding='utf-8')
subprocess.run(['java', '-Dfile.encoding=UTF-8', '-Duser.language=en', str(output)], check=True)
for path in (root / 'src/main/resources/assets/academy/lang').glob('*.json'):
    json.loads(path.read_text(encoding='utf-8'))
print('PASS: all language JSON files parse.')
