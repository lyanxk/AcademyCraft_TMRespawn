package cn.academy.entity;

import cn.academy.data.GameplayData;

import cn.academy.data.Balance;

import cn.academy.ability.AbilityState;
import cn.academy.network.VisualEffectPayload;
import cn.academy.registry.ACEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;

/** Modern synced counterpart of EntityMdBall used by Electron Bomb. */
public final class ACElectronBallEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(
            ACElectronBallEntity.class, EntityDataSerializers.INT);

    private float damage;
    private boolean fired;
    private ServerPlayer spawner;
    private LivingEntity clientSpawner;
    private float clientOffsetX, clientOffsetY, clientOffsetZ;
    private int clientLife = 50;

    public ACElectronBallEntity(EntityType<? extends ACElectronBallEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static ACElectronBallEntity create(ServerPlayer owner, int life, float damage) {
        ACElectronBallEntity ball = new ACElectronBallEntity(ACEntities.ELECTRON_BALL.get(), owner.level());
        float pi=(float)Math.PI;
        float theta = -owner.getYRot()/180*pi + (-pi*.45f+owner.getRandom().nextFloat()*(pi*.45f-(-pi*.45f)));
        float range = Balance.f("a_c_electron_ball_entity", "create_range_1") + owner.getRandom().nextFloat() * (Balance.f("a_c_electron_ball_entity", "create_range_2")-Balance.f("a_c_electron_ball_entity", "create_range_3"));
        ball.spawner=owner;
        ball.getEntityData().set(OWNER, owner.getId());
        ball.getEntityData().set(OFFSET_X, net.minecraft.util.Mth.sin(theta) * range);
        ball.getEntityData().set(OFFSET_Z, net.minecraft.util.Mth.cos(theta) * range);
        ball.getEntityData().set(OFFSET_Y, -1.2f + owner.getRandom().nextFloat() * (.2f-(-1.2f)));
        ball.getEntityData().set(LIFE, life);
        ball.damage = damage;
        ball.getEntityData().set(MODE, 0);
        ball.followOwner(owner);
        return ball;
    }

    public static ACElectronBallEntity createHeld(ServerPlayer owner, int mode) {
        ACElectronBallEntity ball = create(owner, 2_333_333, 0);
        ball.getEntityData().set(MODE, mode);
        return ball;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, 0);
        builder.define(OFFSET_X, 0f);
        builder.define(OFFSET_Y, 0f);
        builder.define(OFFSET_Z, 0f);
        builder.define(LIFE, 20);
        builder.define(MODE, 0);
    }

    public LivingEntity owner() {
        if(level().isClientSide)return clientSpawner;
        if(!level().isClientSide && spawner!=null)return spawner;
        Entity value = level().getEntity(getEntityData().get(OWNER));
        return value instanceof LivingEntity living ? living : null;
    }

    public int life() { return getEntityData().get(LIFE); }
    public int renderLife() { return clientLife; }
    public int nextAnimationInt(int bound) { return random.nextInt(bound); }
    public boolean prepareRenderPosition() {
        if(clientSpawner == null || !clientOffsetsReady())return false;
        setPos(clientSpawner.getX()+clientOffsetX,clientSpawner.getY()+clientOffsetY,clientSpawner.getZ()+clientOffsetZ);
        return true;
    }
    private boolean clientOffsetsReady() { return clientOffsetX!=0 || clientOffsetY!=0 || clientOffsetZ!=0; }
    public boolean isHeldScatterBall() { return life() > 1_000 && getEntityData().get(MODE) == 1; }
    public boolean isHeldMissileBall() { return life() > 1_000 && getEntityData().get(MODE) == 2; }
    public boolean belongsTo(Entity entity) { return getEntityData().get(OWNER) == entity.getId(); }

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide) {
            // Original client resolves the owner, then copies synchronized offsets on a later tick.
            // It keeps this reference and follows even after the owner dies.
            if(clientSpawner==null) {
                Entity value=level().getEntity(getEntityData().get(OWNER));
                if(value instanceof Player player)clientSpawner=player;
            } else if(!clientOffsetsReady()) {
                clientOffsetX=getEntityData().get(OFFSET_X);
                clientOffsetY=getEntityData().get(OFFSET_Y);
                clientOffsetZ=getEntityData().get(OFFSET_Z);
                clientLife=life();
            } else prepareRenderPosition();
            return;
        }
        LivingEntity owner = owner();
        if(!level().isClientSide && getEntityData().get(MODE)==0 && owner instanceof ServerPlayer player) {
            // EntityAdvanced runs callbacks before EntityMdBall.updatePosition. The callback
            // uses the previous ball position, but the caster's current look and eye height.
            if(!fired && tickCount>=life()-Balance.i("a_c_electron_ball_entity", "tick_threshold_1")) {
                fired=true;
                cn.academy.ability.vanilla.meltdowner.skill.ElectronBomb.fire(player,this,damage);
            }
            if(tickCount>=life())discard();
            followOwner(owner);
            return;
        }
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        followOwner(owner);
        int life = Math.max(3, life());
        if (!level().isClientSide && !fired && tickCount >= life - Balance.i("a_c_electron_ball_entity", "tick_threshold_2")) {
            fired = true;
            fire(owner);
        }
        if (!level().isClientSide && tickCount >= life) discard();
    }

    private void followOwner(LivingEntity owner) {
        setPos(owner.getX() + getEntityData().get(OFFSET_X), owner.getY() + getEntityData().get(OFFSET_Y),
                owner.getZ() + getEntityData().get(OFFSET_Z));
        setDeltaMovement(Vec3.ZERO);
    }

    private void fire(LivingEntity owner) {
        Vec3 intended = owner.getEyePosition().add(owner.getLookAngle().scale(Balance.i("a_c_electron_ball_entity", "fire_scale_1")));
        fireAt(owner, intended, damage);
    }

    public void fireAt(LivingEntity owner, Vec3 intended, float shotDamage) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        damage = shotDamage;
        Vec3 start = position().add(0, owner.getEyeHeight(), 0);
        HitResult blockHit = server.clip(new ClipContext(start, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = blockHit.getLocation();
        AABB search = new AABB(start, end).inflate(1);
        LivingEntity target = server.getEntitiesOfClass(LivingEntity.class, search,
                        value -> value != owner && value.isAlive() && !(value instanceof Player
                                && !GameplayData.flag("policy", "attack_players")))
                .stream().filter(value -> value.getBoundingBox().inflate(Balance.d("a_c_electron_ball_entity", "fire_at_inflate_1")).clip(start, end).isPresent())
                .min(Comparator.comparingDouble(value -> value.distanceToSqr(start))).orElse(null);
        if (target != null) {
            target.invulnerableTime = 0;
            if (owner instanceof ServerPlayer player) {
                String skillName = switch (getEntityData().get(MODE)) {
                    case 1 -> "scatter_bomb";
                    case 2 -> "electron_missile";
                    default -> "electron_bomb";
                };
                cn.academy.ability.AbilityContext.of(player,
                        cn.academy.ability.AbilityRegistry.skill("meltdowner." + skillName)).attack(target, damage);
                cn.academy.ability.AbilityExecutor.applyMeltdownerMark(player, target);
            } else {
                target.hurt(server.damageSources().mobAttack(owner), damage);
            }
        }
        VisualEffectPayload visual = new VisualEffectPayload("meltdowner", start.x, start.y, start.z,
                end.x, end.y, end.z, .055f, 0xD86EFF87, 10);
        for (ServerPlayer viewer : server.players()) if (viewer.distanceToSqr(this) <= Balance.i("a_c_electron_ball_entity", "fire_at_threshold_1") * Balance.i("a_c_electron_ball_entity", "fire_at_threshold_2"))
            PacketDistributor.sendToPlayer(viewer, visual);
        discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
