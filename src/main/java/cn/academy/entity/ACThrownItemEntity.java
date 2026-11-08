package cn.academy.entity;

import cn.academy.registry.ACEntities;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACSounds;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import cn.academy.ability.AbilityState;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class ACThrownItemEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_HOOKED = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HOOK_FACE = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_COIN = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> DATA_HOOK_ANCHOR = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_SILBARN_HIT = SynchedEntityData.defineId(
            ACThrownItemEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean hookPending, hookStill, silbarnHit;
    private int silbarnDiscardAt = Integer.MAX_VALUE;
    private Item dimensionItem;
    private final Vec3 silbarnAxis = new Vec3(random.nextInt(), random.nextInt(), random.nextInt());
    private static final java.util.Random VISUAL_RANDOM = new java.util.Random();
    public Vec3 silbarnAxis() { return silbarnAxis; }
    public boolean isSilbarnHit() { return silbarnHit || getEntityData().get(DATA_SILBARN_HIT); }
    private double coinStartY;
    private double coinMaxY;
    private boolean localCoin;
    private final net.minecraft.world.phys.Vec3 coinAxis = new net.minecraft.world.phys.Vec3(
            .1 + random.nextDouble(), random.nextDouble(), random.nextDouble());
    public void setLocalCoin() { localCoin = true; }
    public boolean isLocalCoin() { return localCoin; }
    public net.minecraft.world.phys.Vec3 coinAxis() { return coinAxis; }

    public boolean isHooked() { return getEntityData().get(DATA_HOOKED); }

    private void setHooked(boolean hooked) { getEntityData().set(DATA_HOOKED, hooked); }
    public net.minecraft.core.Direction hookFace() {
        return net.minecraft.core.Direction.from3DDataValue(getEntityData().get(DATA_HOOK_FACE));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HOOKED, false);
        builder.define(DATA_HOOK_FACE, net.minecraft.core.Direction.DOWN.get3DDataValue());
        builder.define(DATA_COIN, false);
        builder.define(DATA_HOOK_ANCHOR, BlockPos.ZERO);
        builder.define(DATA_SILBARN_HIT, false);
    }

    public ACThrownItemEntity(EntityType<? extends ACThrownItemEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public ACThrownItemEntity(LivingEntity owner, Level level, ItemStack item) {
        super(ACEntities.THROWN_ITEM.get(), owner, level);
        noPhysics = true;
        setItem(item.copyWithCount(1));
        if (item.is(ACItems.MAG_HOOK.get()) || item.is(ACItems.SILBARN.get())) {
            setPos(owner.getX(), owner.getEyeY(), owner.getZ());
            setDeltaMovement(owner.getLookAngle().scale(item.is(ACItems.MAG_HOOK.get()) ? 2 : 1));
            setYRot(item.is(ACItems.SILBARN.get()) ? owner.getYHeadRot() : owner.getYRot());
            setXRot(item.is(ACItems.MAG_HOOK.get()) ? owner.getXRot() : 0);
        }
        refreshDimensions();
        if (item.is(ACItems.COIN.get())) {
            getEntityData().set(DATA_COIN, true);
            coinStartY = (float) owner.getY();
            coinMaxY = 0;
            setPos(owner.getX(), owner.getY(), owner.getZ());
            setDeltaMovement(0, owner.getDeltaMovement().y + .92, 0);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return ACItems.COIN.get();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        float size = getItem().is(ACItems.MAG_HOOK.get()) ? (hookStill ? 1 : .5f)
                : getItem().is(ACItems.SILBARN.get()) ? .4f : .25f;
        return net.minecraft.world.entity.EntityDimensions.scalable(size, size);
    }

    @Override
    public void tick() {
        if (getItem().is(ACItems.COIN.get())) {
            // Original local entities are independent; synchronized copies are discarded on the client.
            if (level().isClientSide && !localCoin && getEntityData().get(DATA_COIN)) { discard(); return; }
            // Original Rigidbody subtracts .06 before movement, has no drag, and posts
            // collision notifications with no coin handler: touching a block does not return it.
            baseTick();
            var velocity = getDeltaMovement().add(0, -.06, 0);
            setDeltaMovement(velocity);
            setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
            if (getOwner() instanceof Player owner) {
                setPos(owner.getX(), getY(), owner.getZ());
                if (tickCount > 120 || getDeltaMovement().y < 0 && getY() < owner.getY())
                    returnCoin(owner);
            } else if (tickCount > 120) discard();
            coinMaxY = Math.max(coinMaxY, getY());
            return;
        }
        if (dimensionItem != getItem().getItem()) {
            dimensionItem = getItem().getItem();
            refreshDimensions();
            noCulling = level().isClientSide && getItem().is(ACItems.MAG_HOOK.get());
        }
        if (getItem().is(ACItems.MAG_HOOK.get())) tickHook();
        else if (getItem().is(ACItems.SILBARN.get())) tickSilbarn();
        else super.tick();
    }

    private void tickHook() {
        if (hookPending) {
            hookPending = false;
            hookStill = true;
            setDeltaMovement(Vec3.ZERO);
            refreshDimensions();
            // Original requests maghook_land, but ships neither that sound event nor its audio.
        }
        if (hookStill) {
            prepareHookRender();
            if (!level().isClientSide && level().isEmptyBlock(getEntityData().get(DATA_HOOK_ANCHOR)))
                returnItemAndDiscard();
        } else {
            var from = position();
            var hit = ElectricAttackTrace.traceSegment(this, from, from.add(getDeltaMovement()),
                    entity -> entity != this && entity != getOwner());
            if (!level().isClientSide) {
                if (hit instanceof EntityHitResult entityHit) onHitEntity(entityHit);
                else if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) onHitBlock(blockHit);
            }
            // Rigidbody notifies before applying gravity; even a collision completes this movement.
            setDeltaMovement(getDeltaMovement().add(0, -.05, 0));
            setPos(position().add(getDeltaMovement()));
        }
        if (isHooked() && !hookStill) hookPending = true;
    }

    private void tickSilbarn() {
        var from = position();
        var hit = ElectricAttackTrace.traceSegment(this, from, from.add(getDeltaMovement()), entity -> false);
        if (!silbarnHit && hit.getType() != HitResult.Type.MISS) {
            if (level().isClientSide) {
                silbarnFragments();
                discard();
                silbarnHit = true;
            } else hitSilbarn(false);
        }
        // EntityX's tick-50 callback changes gravity AFTER that tick's Rigidbody update.
        setDeltaMovement(getDeltaMovement().add(0, tickCount > 50 ? -.12 : 0, 0).scale(.8));
        setPos(position().add(getDeltaMovement()));
        if (level().isClientSide) {
            boolean synced = getEntityData().get(DATA_SILBARN_HIT);
            if (!silbarnHit && synced) silbarnFragments();
            silbarnHit = synced;
        } else {
            if (tickCount >= silbarnDiscardAt) discard();
            getEntityData().set(DATA_SILBARN_HIT, silbarnHit);
        }
    }

    public void prepareHookRender() {
        if (!isHooked()) return;
        var face = hookFace();
        switch (face) {
            case DOWN -> setXRot(-90);
            case UP -> setXRot(90);
            case NORTH -> { setYRot(0); setXRot(0); }
            case SOUTH -> { setYRot(180); setXRot(0); }
            case WEST -> { setYRot(-90); setXRot(0); }
            case EAST -> { setYRot(90); setXRot(0); }
        }
        var anchor = getEntityData().get(DATA_HOOK_ANCHOR);
        setPos(anchor.getX() + .5 + face.getStepX() * .51,
                anchor.getY() + .5 + face.getStepY() * .51,
                anchor.getZ() + .5 + face.getStepZ() * .51);
    }

    public double coinProgress() {
        if (!getItem().is(ACItems.COIN.get())) return 0;
        double velocity = getDeltaMovement().y;
        if (velocity > 0) return (.92 - velocity) / .92 * .5;
        return Math.min(1, .5 + (coinMaxY - getY()) / (coinMaxY - coinStartY) * .5);
    }

    public void consumeForRailgun() { discard(); }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide || !getItem().is(ACItems.MAG_HOOK.get())) return;
        boolean otherHook = hit.getEntity() instanceof ACThrownItemEntity other
                && other.getItem().is(ACItems.MAG_HOOK.get());
        if (!otherHook || ((ACThrownItemEntity) hit.getEntity()).isHooked()) {
            if (!otherHook && getOwner() instanceof Player owner)
                hit.getEntity().hurt(level().damageSources().playerAttack(owner), 4);
            returnItemAndDiscard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide || !getItem().is(ACItems.MAG_HOOK.get())) return;
        getEntityData().set(DATA_HOOK_ANCHOR, hit.getBlockPos().immutable());
        getEntityData().set(DATA_HOOK_FACE, hit.getDirection().get3DDataValue());
        setHooked(true);
        hookPending = true;
    }

    @Override
    public boolean isPickable() {
        return getItem().is(ACItems.SILBARN.get()) || getItem().is(ACItems.MAG_HOOK.get()) && isHooked();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getItem().is(ACItems.MAG_HOOK.get())) {
            if (isHooked() && !level().isClientSide && source.getEntity() instanceof Player) returnItemAndDiscard();
            return true;
        }
        return super.hurt(source, amount);
    }

    private void returnCoin(Player player) {
        if (isRemoved()) return;
        if (level().isClientSide) {
            if (cn.academy.config.ACHudConfig.HEADS_OR_TAILS.get()) player.displayClientMessage(
                    Component.translatable("ac.headsOrTails." + player.getRandom().nextInt(2)), false);
            discard(); return;
        }
        ItemStack returned = getItem().copyWithCount(1);
        if (!player.isCreative()) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, returned);
            else if (held.is(ACItems.COIN.get()) && held.getCount() < held.getMaxStackSize()) held.grow(1);
            else if (!player.getInventory().add(returned))
                level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(),
                        player.getX(), player.getY() + .6, player.getZ(), returned));
        }
        discard();
    }

    public void breakSilbarn() {
        if (!level().isClientSide && getItem().is(ACItems.SILBARN.get()) && !isRemoved()) hitSilbarn(true);
    }

    private void hitSilbarn(boolean heavy) {
        if (silbarnHit) return;
        silbarnHit = true;
        playSound(ACSounds.get(heavy ? "entity.silbarn_heavy" : "entity.silbarn_light"), .5f, 1);
        silbarnDiscardAt = tickCount + 10;
    }

    private void silbarnFragments() {
        var velocities = new java.util.ArrayList<Vec3>();
        int count = 18 + VISUAL_RANDOM.nextInt(9);
        for (int i = 0; i < count; i++) {
            double speed = .08 + VISUAL_RANDOM.nextDouble() * .10;
            double vx = random.nextDouble() * speed;
            double vy = random.nextDouble() * Math.sqrt(speed * speed - vx * vx);
            double vz = Math.sqrt(Math.max(0, speed * speed - vx * vx - vy * vy));
            vx *= random.nextBoolean() ? 1 : -1;
            vy *= random.nextBoolean() ? 1 : -1;
            vz *= random.nextBoolean() ? 1 : -1;
            velocities.add(new Vec3(vx, vy + .2, vz));
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new SilbarnFragments(this, velocities));
    }

    /** Client-side entity collision and HIT_SYNC both use this local, once-only visual event. */
    public static final class SilbarnFragments extends net.neoforged.bus.api.Event {
        public final ACThrownItemEntity entity;
        public final java.util.List<Vec3> velocities;
        private SilbarnFragments(ACThrownItemEntity entity, java.util.List<Vec3> velocities) {
            this.entity = entity;
            this.velocities = java.util.List.copyOf(velocities);
        }
    }

    private void returnItemAndDiscard() {
        if (isRemoved()) return;
        if (!level().isClientSide && getItem().is(ACItems.MAG_HOOK.get()))
            level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY(), getZ(),
                    new ItemStack(ACItems.MAG_HOOK.get())));
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Hooked", isHooked());
        tag.putDouble("CoinStartY", coinStartY);
        tag.putDouble("CoinMaxY", coinMaxY);
        tag.putLong("HookAnchor", getEntityData().get(DATA_HOOK_ANCHOR).asLong());
        tag.putInt("HookFace", getEntityData().get(DATA_HOOK_FACE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHooked(tag.getBoolean("Hooked"));
        hookPending = isHooked();
        hookStill = false;
        coinStartY = tag.getDouble("CoinStartY");
        coinMaxY = tag.getDouble("CoinMaxY");
        getEntityData().set(DATA_HOOK_ANCHOR, BlockPos.of(tag.getLong("HookAnchor")));
        getEntityData().set(DATA_HOOK_FACE, tag.getInt("HookFace"));
        refreshDimensions();
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (getItem().is(ACItems.SILBARN.get())) { discard(); return; }
        if (getItem().is(ACItems.COIN.get())) {
            discard();
            level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY(), getZ(),
                    new ItemStack(ACItems.COIN.get())));
        }
    }
}
