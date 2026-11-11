package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.data.GameplayRules;

import cn.academy.data.GameplayData;

import cn.academy.data.Balance;
import cn.academy.ability.SkillHitDamage;

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
        enhancedArc = arc && state.level()>=Balance.i("electric_attack_context", "electric_attack_context_threshold_1");
        enhancedBolt = !arc && state.level()>=Balance.i("electric_attack_context", "electric_attack_context_threshold_2");
        // ContextManager captures its targets before SkillPerform/consume callbacks run.
        var viewers = new ArrayList<ServerPlayer>();
        viewers.add(player);
        for (var viewer : player.serverLevel().players())
            if (viewer != player && viewer.distanceToSqr(player) <= Balance.i("electric_attack_context", "electric_attack_context_threshold_3")) viewers.add(viewer);
        audience = List.copyOf(viewers);
    }

    public static boolean handles(AbilitySkill skill) {
        return skill.name().equals("arc_gen") || skill.name().equals("thunder_bolt");
    }
    public float cpCost() { return enhancedArc ? Balance.i("electric_attack_context", "cp_cost_value_1") : enhancedBolt ? lerp(Balance.i("electric_attack_context", "cp_cost_lerp_1"),Balance.i("electric_attack_context", "cp_cost_lerp_2"),experience) : arc ? lerp(Balance.i("electric_attack_context", "cp_cost_lerp_3"), Balance.i("electric_attack_context", "cp_cost_lerp_4"), experience) : (int) (Balance.i("electric_attack_context", "cp_cost_value_2") + Balance.d("electric_attack_context", "cp_cost_value_3") * experience); }
    public float overloadCost() { return enhancedArc ? Balance.i("electric_attack_context", "overload_cost_value_1") : enhancedBolt ? Balance.i("electric_attack_context", "overload_cost_value_2") : arc ? lerp(Balance.i("electric_attack_context", "overload_cost_lerp_1"), Balance.i("electric_attack_context", "overload_cost_lerp_2"), state.experience(skill.id())) : lerp(Balance.i("electric_attack_context", "overload_cost_lerp_3"), Balance.i("electric_attack_context", "overload_cost_lerp_4"), experience); }

    public int perform() {
        var level = player.serverLevel();
        double range = arc ? lerp(enhancedArc?Balance.i("electric_attack_context", "perform_lerp_1"):Balance.i("electric_attack_context", "perform_lerp_2"), enhancedArc?Balance.i("electric_attack_context", "perform_lerp_3"):Balance.i("electric_attack_context", "perform_lerp_4"), experience) : Balance.i("electric_attack_context", "perform_range_1");
        var hit = ElectricAttackTrace.trace(player, range, arc);
        if (arc) {
            sendEffects(range, hit.getLocation(), List.of());
            boolean entityHit = hit instanceof EntityHitResult;
            if (hit instanceof EntityHitResult entity) {
                attack(player, skill, entity.getEntity(), lerp(enhancedArc?Balance.i("electric_attack_context", "perform_lerp_5"):Balance.i("electric_attack_context", "perform_lerp_6"), enhancedArc?Balance.i("electric_attack_context", "perform_lerp_7"):Balance.i("electric_attack_context", "perform_lerp_8"), experience));
                if (entity.getEntity() instanceof Creeper) ACAdvancements.grant(player, "legacy/electromaster/attack_creeper");
            } else {
                // LambdaLib2 returns a non-null MISS. ArcGen also runs this branch on a miss.
                BlockHitResult block = (BlockHitResult) hit;
                var pos = block.getBlockPos();
                if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()) {
                    if (level.random.nextDouble() < (enhancedArc || experience > Balance.f("electric_attack_context", "perform_threshold_1") ? Balance.d("electric_attack_context", "perform_threshold_2") : 0)) {
                        Vec3 point = hit.getLocation();
                        GameplayRules.dropLoot(level, point, GameplayData.string("parameters/electric_attack_context", "fishing_loot_table"));
                        ACAdvancements.grant(player, "legacy/electromaster/arc_gen");
                    }
                } else if (level.random.nextDouble() < lerp(enhancedArc?Balance.f("electric_attack_context", "perform_lerp_9"):Balance.i("electric_attack_context", "perform_lerp_10"), enhancedArc?Balance.f("electric_attack_context", "perform_lerp_11"):Balance.f("electric_attack_context", "perform_lerp_12"), experience)) {
                    // Original ArcGen does not run the skill's block-destruction filter here.
                    var above = pos.above();
                    if (level.isEmptyBlock(above)) level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
                }
            }
            state.addExperience(skill, entityHit ? lerp(Balance.f("electric_attack_context", "perform_lerp_13"), Balance.f("electric_attack_context", "perform_lerp_14"), state.experience(skill.id()))
                    : lerp(Balance.f("electric_attack_context", "perform_lerp_15"), Balance.f("electric_attack_context", "perform_lerp_16"), state.experience(skill.id())));
        } else {
            Entity primary = hit instanceof EntityHitResult entity ? entity.getEntity() : null;
            Vec3 center = primary == null ? hit.getLocation() : hit.getLocation().add(0, primary.getEyeHeight(), 0);
            var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(Balance.i("electric_attack_context", "perform_inflate_1")),
                    entity -> entity != player && entity != primary && entity.distanceToSqr(center) <= Balance.i("electric_attack_context", "perform_threshold_3"));
            // MSG_PERFORM sends the selected targets before attack callbacks can move/remove them.
            sendEffects(range, center, targets);
            if (primary != null) {
                float damage = enhancedBolt
                        ? lerp(Balance.i("electric_attack_context", "perform_lerp_17"), Balance.i("electric_attack_context", "perform_lerp_18"), experience)
                        : SkillHitDamage.low(lerp(Balance.i("electric_attack_context", "perform_lerp_19"), Balance.i("electric_attack_context", "perform_lerp_20"), experience)).against(primary);
                attack(player, skill, primary, damage);
                slowPrimary(primary, 40);
            }
            for (Entity secondary : targets) {
                float damage = enhancedBolt
                        ? lerp(Balance.i("electric_attack_context", "perform_lerp_21"), Balance.i("electric_attack_context", "perform_lerp_22"), experience)
                        : SkillHitDamage.low(lerp(Balance.i("electric_attack_context", "perform_lerp_23"), Balance.i("electric_attack_context", "perform_lerp_24"), experience)).against(secondary);
                attack(player, skill, secondary, damage);
                // Preserve the original ad.target reference in the AOE loop; do not slow each secondary.
                slowPrimary(primary, 20);
            }
            state.addExperience(skill, primary != null || !targets.isEmpty() ? Balance.f("electric_attack_context", "perform_add_experience_1") : Balance.f("electric_attack_context", "perform_add_experience_2"));
        }
        return enhancedArc ? 5 : enhancedBolt ? (int) lerp(Balance.i("electric_attack_context", "perform_lerp_25"), Balance.i("electric_attack_context", "perform_lerp_26"), experience) : arc ? (int) lerp(Balance.i("electric_attack_context", "perform_lerp_27"), Balance.i("electric_attack_context", "perform_lerp_28"), state.experience(skill.id())) : (int) lerp(Balance.i("electric_attack_context", "perform_lerp_29"), Balance.i("electric_attack_context", "perform_lerp_30"), experience);
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
        if (experience > Balance.f("electric_attack_context", "slow_primary_threshold_1") && player.serverLevel().random.nextDouble() < Balance.d("electric_attack_context", "slow_primary_threshold_2") && primary instanceof LivingEntity living)
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, Balance.i("electric_attack_context", "slow_primary_add_effect_1")));
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
