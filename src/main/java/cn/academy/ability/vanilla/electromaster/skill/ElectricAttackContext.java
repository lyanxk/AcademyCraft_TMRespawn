package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.ability.AbilityContext;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.advancement.ACAdvancements;
import cn.academy.network.ArcEffectPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Constructor snapshots and server perform handlers from 1.12.2 ArcGen / ThunderBolt. */
public final class ElectricAttackContext {
    private final ServerPlayer player;
    private final AbilityState state;
    private final AbilitySkill skill;
    private final float experience;
    private final boolean arc;
    private final boolean enhancedArc;
    private final boolean enhancedBolt;
    private final List<ServerPlayer> audience;

    public ElectricAttackContext(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        this.player = player; this.state = state; this.skill = skill;
        experience = state.experience(skill.id());
        arc = skill.name().equals("arc_gen");
        enhancedArc = arc && state.level()>=3;
        enhancedBolt = !arc && state.level()>=5;
        // ContextManager captures its targets before SkillPerform/consume callbacks run.
        var viewers = new ArrayList<ServerPlayer>();
        viewers.add(player);
        for (var viewer : player.serverLevel().players())
            if (viewer != player && viewer.distanceToSqr(player) <= 625) viewers.add(viewer);
        audience = List.copyOf(viewers);
    }

    public static boolean handles(AbilitySkill skill) {
        return skill.name().equals("arc_gen") || skill.name().equals("thunder_bolt");
    }
    public float cpCost() { return enhancedArc ? 70 : enhancedBolt ? lerp(420,2100,experience) : arc ? lerp(30, 70, experience) : (int) (280 + 140.0 * experience); }
    public float overloadCost() { return enhancedArc ? 11 : enhancedBolt ? 27 : arc ? lerp(18, 11, state.experience(skill.id())) : lerp(50, 27, experience); }

    public int perform() {
        var level = player.serverLevel();
        double range = arc ? lerp(enhancedArc?15:6, enhancedArc?30:15, experience) : 40;
        var hit = ElectricAttackTrace.trace(player, range, arc);
        if (arc) {
            sendEffects(range, hit.getLocation(), List.of());
            boolean entityHit = hit instanceof EntityHitResult;
            if (hit instanceof EntityHitResult entity) {
                attack(player, skill, entity.getEntity(), lerp(enhancedArc?9:5, enhancedArc?15:9, experience));
                if (entity.getEntity() instanceof Creeper) ACAdvancements.grant(player, "legacy/electromaster/attack_creeper");
            } else {
                // LambdaLib2 returns a non-null MISS. ArcGen also runs this branch on a miss.
                BlockHitResult block = (BlockHitResult) hit;
                var pos = block.getBlockPos();
                if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()) {
                    if (level.random.nextDouble() < (enhancedArc || experience > .5f ? .1 : 0)) {
                        Vec3 point = hit.getLocation();
                        level.addFreshEntity(new ItemEntity(level, point.x, point.y, point.z, new ItemStack(Items.COOKED_COD)));
                        ACAdvancements.grant(player, "legacy/electromaster/arc_gen");
                    }
                } else if (level.random.nextDouble() < lerp(enhancedArc?.6f:0, enhancedArc?1f:.6f, experience)) {
                    // Original ArcGen does not run the skill's block-destruction filter here.
                    var above = pos.above();
                    if (level.isEmptyBlock(above)) level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
                }
            }
            state.addExperience(skill, entityHit ? lerp(.0048f, .0072f, state.experience(skill.id()))
                    : lerp(.0018f, .0027f, state.experience(skill.id())));
        } else {
            Entity primary = hit instanceof EntityHitResult entity ? entity.getEntity() : null;
            Vec3 center = primary == null ? hit.getLocation() : hit.getLocation().add(0, primary.getEyeHeight(), 0);
            var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(8),
                    entity -> entity != player && entity != primary && entity.distanceToSqr(center) <= 64);
            // MSG_PERFORM sends the selected targets before attack callbacks can move/remove them.
            sendEffects(range, center, targets);
            if (primary != null) {
                attack(player, skill, primary, enhancedBolt?lerp(50,150,experience):lerp(25, 50, experience));
                slowPrimary(primary, 40);
            }
            for (Entity secondary : targets) {
                attack(player, skill, secondary, enhancedBolt?lerp(30,90,experience):lerp(15, 30, experience));
                // Preserve the original ad.target reference in the AOE loop; do not slow each secondary.
                slowPrimary(primary, 20);
            }
            state.addExperience(skill, primary != null || !targets.isEmpty() ? .005f : .003f);
        }
        return enhancedArc ? 5 : enhancedBolt ? (int) lerp(50, 40, experience) : arc ? (int) lerp(15, 5, state.experience(skill.id())) : (int) lerp(120, 50, experience);
    }

    private void sendEffects(double range, Vec3 aoeCenter, List<LivingEntity> aoeTargets) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        for (int i = 0; i < (arc ? 1 : 3); i++)
            send(new ArcEffectPayload(player.getId(), arc ? ArcEffectPayload.WEAK : ArcEffectPayload.STRONG,
                    start, end, -1, arc ? 10 : 20, i == 0));
        for (var target : aoeTargets)
            send(new ArcEffectPayload(player.getId(), ArcEffectPayload.AOE,
                    aoeCenter, target.getEyePosition(), target.getId(), 0, false));
    }

    private void send(ArcEffectPayload payload) {
        for (var viewer : audience) PacketDistributor.sendToPlayer(viewer, payload);
    }

    private void slowPrimary(Entity primary, int ticks) {
        if (experience > .2f && player.serverLevel().random.nextDouble() < .8 && primary instanceof LivingEntity living)
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3));
    }

    public static void attack(ServerPlayer player, AbilitySkill skill, Entity target, float damage) {
        AbilityContext.of(player, skill).attack(target, damage);
        if (target instanceof Creeper creeper && player.getRandom().nextFloat() < .3f)
            creeper.getEntityData().set(PoweredField.VALUE, true);
    }

    private static final class PoweredField {
        @SuppressWarnings("unchecked")
        static EntityDataAccessor<Boolean> read() {
            try {
                var field = Creeper.class.getDeclaredField("DATA_IS_POWERED");
                field.setAccessible(true);
                return (EntityDataAccessor<Boolean>) field.get(null);
            } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
        static final EntityDataAccessor<Boolean> VALUE = read();
    }
    private static float lerp(float from, float to, float t) { return from + (to - from) * t; }
}
