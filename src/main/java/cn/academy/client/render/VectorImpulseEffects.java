package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext;
import cn.academy.client.render.util.*;
import cn.academy.network.*;
import cn.academy.registry.ACSounds;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.VectorImpulsePayload.*;

/** VecAccelContext and BloodRetroContext local listeners, with original trajectory and blood meshes. */
public final class VectorImpulseEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Map<Integer,Impulse> LOCAL=new LinkedHashMap<>();
    private static final List<Splash> SPLASHES=new ArrayList<>();
    private static final List<Spray> SPRAYS=new ArrayList<>();
    private static final Random RANDOM=new Random(),ENTITY_RANDOM=new Random();
    private static ClientLevel world;
    private static Player local;
    private static final class Impulse {
        final int kind,slot,request;final float experience;int id,ticks;
        final boolean enhancedAcceleration;
        boolean released,canPerform=true,previewCanPerform;
        Impulse(int kind,int slot,int request,float experience){this.kind=kind;this.slot=slot;this.request=request;this.experience=experience;released=kind==BLOOD;enhancedAcceleration=kind==ACCEL && AbilityState.load(local).level()>=4;}
        AbilitySkill skill(){return AbilityRegistry.skill(kind==ACCEL?"vecmanip.vec_accel":"vecmanip.blood_retro");}
    }
    private static final class Splash {
        final Vec3 position;final float size;int frame;
        Splash(Vec3 position,float size){this.position=position;this.size=size;}
    }
    private static final class Spray {
        final BlockPos block;final Vec3 position;final Direction face;final int texture;
        final double size,rotation,offsetX,offsetY;final float yaw,pitch;int ticks;
        Spray(BlockHitResult hit) {
            block=hit.getBlockPos();face=hit.getDirection();texture=RANDOM.nextInt(10);
            size=(1.1+RANDOM.nextDouble()*.3)*(face.getAxis()==Direction.Axis.Y?1:.8);rotation=RANDOM.nextDouble()*360;
            offsetX=ENTITY_RANDOM.nextGaussian()*.15;offsetY=ENTITY_RANDOM.nextGaussian()*.15;
            var shape=world.getBlockState(block).getShape(world,block);var bounds=shape.isEmpty()?new AABB(0,0,0,1,1,1):shape.bounds();
            var center=bounds.getCenter();position=Vec3.atLowerCornerOf(block).add(center).add(face.getStepX()*.51*bounds.getXsize(),face.getStepY()*.51*bounds.getYsize(),face.getStepZ()*.51*bounds.getZsize());
            yaw=switch(face){case EAST->90;case WEST->-90;case NORTH->180;default->0;};pitch=face==Direction.DOWN?90:face==Direction.UP?-90:0;
        }
    }
    private static boolean sync(Minecraft mc) {
        if(world!=mc.level || local!=mc.player) {
            LOCAL.clear();SPLASHES.clear();SPRAYS.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;
        }
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !VectorImpulseContext.handles(skill) || !state.canStartContext(skill))return 0;
        endLocal(slot,true);int request=REQUESTS.begin(slot);LOCAL.put(request,new Impulse(VectorImpulseContext.kind(skill),slot,request,state.experience(skill.id())));return request;
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        for(var impulse:List.copyOf(LOCAL.values()))if(slot<0 || impulse.slot==slot) {
            if(abort)cancel(impulse);else release(impulse);
        }
    }
    private static void cancel(Impulse impulse) {
        PacketDistributor.sendToServer(new VectorImpulseInputPayload(impulse.request,-1,true));LOCAL.remove(impulse.request);
        if(REQUESTS.accepts(impulse.slot,impulse.request))REQUESTS.end(impulse.slot);
    }
    private static void release(Impulse impulse) {
        if(impulse.released)return;impulse.released=true;
        if(impulse.kind==ACCEL) {
            var state=AbilityState.load(local);var skill=impulse.skill();float exp=state.experience(skill.id());
            if(!canAccelerate(impulse) || !state.consumeRaw(skill,impulse.enhancedAcceleration?160:240+(160-240)*impulse.experience,
                    impulse.enhancedAcceleration?15:30+(15-30)*exp,local.isCreative())){cancel(impulse);return;}
            local.setDeltaMovement(initialSpeed(impulse,0));
            // getLowestRidingEntity in the original always returns at least the player, so its null-only dismount does not run.
            state.finishContext(skill,state.cooldownFor(skill),0);
            PacketDistributor.sendToServer(new VectorImpulseInputPayload(impulse.request,-1,false));
        } else {
            var trace=ElectricAttackTrace.trace(local,2,false);
            if(trace instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity)
                PacketDistributor.sendToServer(new VectorImpulseInputPayload(impulse.request,hit.getEntity().getId(),false));
            else cancel(impulse); // Avoid the original null trace / nonliving cast crash, while still allowing those entities to occlude.
        }
    }
    public static void receive(VectorImpulsePayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.phase()==END) {
            if(packet.caster()==local.getId())LOCAL.remove(packet.request());
            return;
        }
        if(!(world.getEntity(packet.caster()) instanceof Player owner))return;
        if(packet.phase()==START && owner==local) {
            var impulse=LOCAL.get(packet.request());
            if(packet.request()!=0 && (impulse==null || !REQUESTS.accepts(packet.slot(),packet.request()))) {
                PacketDistributor.sendToServer(new VectorImpulseInputPayload(packet.request(),-1,true));return;
            }
            if(impulse==null){impulse=new Impulse(packet.kind(),packet.slot(),packet.request(),packet.experience());LOCAL.put(packet.request(),impulse);}
            impulse.id=packet.context();
        } else if(packet.phase()==PERFORM) {
            if(packet.kind()==BLOOD && world.getEntity(packet.target()) instanceof LivingEntity target)blood(owner,target);
            world.playLocalSound(owner.getX(),owner.getY(),owner.getZ(),ACSounds.get(packet.kind()==ACCEL?"vecmanip.vec_accel":"vecmanip.blood_retro"),SoundSource.AMBIENT,packet.kind()==ACCEL?.35f:1,1,false);
        }
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        for(var impulse:List.copyOf(LOCAL.values())) {
            impulse.ticks++;
            if(!local.isAlive() || !AbilityState.load(local).active()){cancel(impulse);continue;}
            if(impulse.kind==ACCEL) {
                impulse.canPerform=canAccelerate(impulse);
                impulse.previewCanPerform=impulse.canPerform;
            }
        }
        SPLASHES.removeIf(splash->++splash.frame>=10);
        SPRAYS.removeIf(spray->++spray.ticks>1200 || world.getBlockState(spray.block).isAir());
    }
    private static Vec3 entityLook(float yaw,float pitch) {
        float y=yaw*(float)Math.PI/180,p=pitch*(float)Math.PI/180;
        return new Vec3(-Mth.sin(y)*Mth.cos(p),-Mth.sin(p),Mth.cos(y)*Mth.cos(p));
    }
    private static boolean canAccelerate(Impulse impulse) {
        return impulse.enhancedAcceleration || impulse.experience>.5f || ElectricAttackTrace.traceBlockSegment(local,local.position(),local.position().add(0,-2,0),state->!state.isAir()).getType()==HitResult.Type.BLOCK;
    }
    private static Vec3 initialSpeed(Impulse impulse,float partial) {
        float yaw=local.yRotO+(local.getYRot()-local.yRotO)*partial,pitch=local.xRotO+(local.getXRot()-local.xRotO)*partial-10;
        return entityLook(yaw,pitch).scale(VectorImpulseContext.accelerationSpeed(impulse.enhancedAcceleration,impulse.experience));
    }
    private static void blood(Player owner,LivingEntity target) {
        int count=6+RANDOM.nextInt(4);
        for(int i=0;i<count;i++) {
            RANDOM.nextFloat(); // EntityBloodSplash constructor draws its initial size before BloodRetro overwrites it.
            float size=1.4f+RANDOM.nextFloat()*(1.8f-1.4f);
            Vec3 offset=new Vec3((-1+RANDOM.nextDouble()*2)*target.getBbWidth(),RANDOM.nextDouble()*target.getBbHeight(),(-1+RANDOM.nextDouble()*2)*target.getBbWidth());
            SPLASHES.add(new Splash(target.position().add(offset).add(owner.getLookAngle().scale(.2)),size));
        }
        Vec3 head=target.position().add(0,target.getBbHeight()*.6,0);
        List<Vec3> directions=new ArrayList<>();
        for(int pitch:new int[]{0,30,45,60,80,-30,-45,-60,-80})directions.add(entityLook(owner.getYHeadRot()-20+RANDOM.nextFloat()*40,pitch));
        List<BlockHitResult> hits=new ArrayList<>();
        for(var look:directions) {
            var hit=ElectricAttackTrace.traceBlockSegment(owner,head.subtract(look.scale(.5)),head.add(look.scale(5)),null);
            if(hit.getType()==HitResult.Type.BLOCK)hits.add(hit);
        }
        for(var hit:hits)for(int i=0,countSprays=2+RANDOM.nextInt(1);i<countSprays;i++)SPRAYS.add(new Spray(hit));
    }
    /** FleshRipping uses the same EntityBloodSplash renderer with its default size and cylindrical placement. */
    public static void fleshBlood(Entity target) {
        if(!sync(Minecraft.getInstance()))return;
        for(int i=0,count=4+RANDOM.nextInt(2);i<count;i++) {
            double y=target.getY()+RANDOM.nextDouble()*target.getBbHeight();if(target instanceof Player)y+=1.6;
            double theta=RANDOM.nextDouble()*Math.PI*2,r=.5*(.8*target.getBbWidth()+RANDOM.nextDouble()*(target.getBbWidth()-.8*target.getBbWidth()));
            SPLASHES.add(new Splash(new Vec3(target.getX()+r*Math.sin(theta),y,target.getZ()+r*Math.cos(theta)),.8f+RANDOM.nextFloat()*(1.3f-.8f)));
        }
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(Minecraft.getInstance()))return;
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();var camera=event.getCamera();var cameraPos=camera.getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        if(mc.options.getCameraType().isFirstPerson())for(var impulse:LOCAL.values())if(impulse.kind==ACCEL) {
            Vec3 velocity=initialSpeed(impulse,partial);float yaw=Mth.lerp(partial,local.yRotO,local.getYRot()),pitch=Mth.lerp(partial,local.xRotO,local.getXRot());
            Vec3 look=RailgunGeometry.direction(yaw,pitch),side=new Vec3(look.x,0,look.z).yRot(90).normalize().scale(-.08);
            Vec3 position=new Vec3(side.x,1.56,side.z).subtract(look.scale(.12));
            var base=local.getPosition(partial).subtract(cameraPos);pose.pushPose();pose.translate(base.x,base.y,base.z);
            var out=buffers.getBuffer(ACShaders.VECTOR_TRAJECTORY);
            for(int index=1;index<100;index++) {
                var previous=position;velocity=velocity.scale(.98);position=position.add(velocity.scale(.02));velocity=velocity.add(0,-.02*1.9,0);
                int alpha=(int)(255*Math.clamp(.7f*(1-index*.03f),0,1)),color=impulse.previewCanPerform?255:51;
                out.addVertex(pose.last(),(float)previous.x,(float)previous.y+.02f,(float)previous.z).setUv(0,0).setColor(255,color,color,alpha);
                out.addVertex(pose.last(),(float)previous.x,(float)previous.y-.02f,(float)previous.z).setUv(0,1).setColor(255,color,color,alpha);
                out.addVertex(pose.last(),(float)position.x,(float)position.y-.02f,(float)position.z).setUv(1,1).setColor(255,color,color,alpha);
                out.addVertex(pose.last(),(float)position.x,(float)position.y+.02f,(float)position.z).setUv(1,0).setColor(255,color,color,alpha);
            }
            buffers.endBatch(ACShaders.VECTOR_TRAJECTORY);pose.popPose();
        }
        for(var splash:SPLASHES) {
            pose.pushPose();var point=splash.position.subtract(cameraPos);pose.translate(point.x,point.y,point.z);pose.scale(splash.size,splash.size,splash.size);
            pose.mulPose(Axis.YP.rotationDegrees(180-camera.getYRot()));
            float pitch=mc.options.getCameraType()==net.minecraft.client.CameraType.THIRD_PERSON_FRONT?-camera.getXRot():camera.getXRot();
            pose.mulPose(Axis.XP.rotationDegrees(-pitch));var type=ACShaders.BLOOD_SPLASH[splash.frame];var out=buffers.getBuffer(type);
            out.addVertex(pose.last(),-.5f,-.25f,0).setUv(0,1).setColor(213,29,29,200);
            out.addVertex(pose.last(),.5f,-.25f,0).setUv(1,1).setColor(213,29,29,200);
            out.addVertex(pose.last(),.5f,.75f,0).setUv(1,0).setColor(213,29,29,200);
            out.addVertex(pose.last(),-.5f,.75f,0).setUv(0,0).setColor(213,29,29,200);
            buffers.endBatch(type);pose.popPose();
        }
        for(var spray:SPRAYS) {
            pose.pushPose();var point=spray.position.subtract(cameraPos);pose.translate(point.x,point.y,point.z);
            pose.mulPose(Axis.YP.rotationDegrees(-spray.yaw));pose.mulPose(Axis.XP.rotationDegrees(-spray.pitch));pose.translate(spray.offsetX,spray.offsetY,0);
            pose.scale((float)spray.size,(float)spray.size,(float)spray.size);pose.mulPose(Axis.ZP.rotationDegrees((float)spray.rotation));
            // Original isWall is true for UP/DOWN; keep that reversed texture selection.
            var type=ACShaders.BLOOD_SPRAY[spray.face.getAxis()==Direction.Axis.Y?1:0][spray.texture%3];var out=buffers.getBuffer(type);
            int light=LevelRenderer.getLightColor(world,BlockPos.containing(spray.position));
            out.addVertex(pose.last(),-.5f,-.5f,0).setColor(-1).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);
            out.addVertex(pose.last(),.5f,-.5f,0).setColor(-1).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);
            out.addVertex(pose.last(),.5f,.5f,0).setColor(-1).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);
            out.addVertex(pose.last(),-.5f,.5f,0).setColor(-1).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);
            buffers.endBatch(type);pose.popPose();
        }
    }
    private VectorImpulseEffects(){}
}
