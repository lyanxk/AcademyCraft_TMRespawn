package cn.academy.ability;

import cn.academy.data.GameplayData;

import cn.academy.event.ability.CalcEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Shared attack semantics ported from 1.12.2 AbilityContext. */
public final class AbilityContext {
    public final ServerPlayer player;
    public final AbilitySkill skill;

    public static AbilityContext of(ServerPlayer player, AbilitySkill skill) {
        return new AbilityContext(player, skill);
    }

    private AbilityContext(ServerPlayer player, AbilitySkill skill) {
        this.player = java.util.Objects.requireNonNull(player);
        this.skill = java.util.Objects.requireNonNull(skill);
    }

    public boolean attack(Entity target, float damage) { return attack(target, damage, false); }

    public boolean attackIgnoreArmor(Entity target, float damage) { return attack(target, damage, true); }

    /** Original WorldUtils sphere selection and float falloff, including nonliving entities and zero edge damage events. */
    public void attackRange(net.minecraft.world.phys.Vec3 center, double range, float damage,
                            java.util.function.Predicate<Entity> selector) {
        for (var entity : player.level().getEntities((Entity) null, new net.minecraft.world.phys.AABB(center, center).inflate(range),
                target -> target.distanceToSqr(center) <= range * range && selector.test(target))) {
            float ratio = (float) (entity.position().distanceTo(center) / range);
            float factor = 1 - Math.max(0, Math.min(1, ratio));
            attack(entity, damage * factor);
        }
    }

    public void attackReflect(Entity target, float damage, Runnable reflectCallback) {
        var event = new cn.academy.event.ability.ReflectEvent(player, skill, target);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) reflectCallback.run();
        else attack(target, damage);
    }

    public boolean canBreakBlock(net.minecraft.world.level.Level world) {
        return skill.shouldDestroyBlocks() && AbilityPipeline.canBreakBlock(world, skill);
    }

    /** Original coordinate overload: a skill gate and the per-position BlockDestroyEvent. */
    public boolean canBreakBlock(net.minecraft.world.level.Level world,net.minecraft.core.BlockPos pos) {
        if(!skill.shouldDestroyBlocks())return false;
        var event=new cn.academy.event.BlockDestroyEvent(world,player,pos,skill);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);return !event.isCanceled();
    }

    private boolean attack(Entity target, float damage, boolean ignoreArmor) {
        damage = CalcEvent.calc(new CalcEvent.SkillAttack(player, skill, target, damage));
        if (!(damage > 0) || !Float.isFinite(damage)) return false;
        if (target instanceof Player && !GameplayData.flag("policy", "attack_players"))
            return false;
        if ((target instanceof net.minecraft.world.entity.decoration.Painting
                || target instanceof net.minecraft.world.entity.decoration.ItemFrame)
                && !canBreakBlock(player.level())) return false;
        float scaled = damage * GameplayData.decimal("ability", "damage_scale") * skill.damageScale();
        return scaled > 0 && target.hurt(new SkillDamageSource(player, skill, ignoreArmor), scaled);
    }
}
