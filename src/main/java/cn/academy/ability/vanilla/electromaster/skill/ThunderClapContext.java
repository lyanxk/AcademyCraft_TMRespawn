package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.data.Balance;
import cn.academy.ability.SkillHitDamage;

import cn.academy.ability.AbilityContext;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.ThunderClapLightningPayload;
import cn.academy.network.ThunderClapEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** ThunderClapContext's constructor snapshot and trace-before-payment ordering. */
public final class ThunderClapContext {
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger();
    private final int contextId = NEXT_ID.incrementAndGet();
    private final java.util.ArrayList<ServerPlayer> audience = new java.util.ArrayList<>();
    private int slot, requestId;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float experience;
    private Vec3 hit = Vec3.ZERO;
    private int ticks;
    public ThunderClapContext(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        this.player = player; this.skill = skill; experience = state.experience(skill.id());
        audience.add(player);
        for (var viewer : player.serverLevel().players())
            if (viewer != player && viewer.distanceToSqr(player) <= Balance.i("thunder_clap_context", "thunder_clap_context_threshold_1")) audience.add(viewer);
    }
    public void start(int slot, int requestId) {
        this.slot = slot; this.requestId = requestId;
        sendEffect(true);
    }
    public void end() { sendEffect(false); audience.clear(); }
    private void sendEffect(boolean start) {
        var payload = new ThunderClapEffectPayload(player.getId(), slot, contextId, requestId, start);
        audience.forEach(viewer -> PacketDistributor.sendToPlayer(viewer, payload));
    }
    public boolean tick(AbilityState state) {
        hit = ElectricAttackTrace.traceBlocks(player, 40).getLocation();
        ticks++;
        if (ticks <= Balance.i("thunder_clap_context", "tick_threshold_1") && !state.consumeRaw(skill, Balance.i("thunder_clap_context", "tick_consume_raw_1") + experience * Balance.i("thunder_clap_context", "tick_consume_raw_2"), 0, player.isCreative())) return true;
        if (ticks % 10 == 0) discharge();
        return ticks >= Balance.i("thunder_clap_context", "tick_threshold_2");
    }
    private void discharge() {
        var level = player.serverLevel();
        Vec3 center = player.position();
        var context = AbilityContext.of(player, skill);
        var targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(Balance.i("thunder_clap_context", "discharge_inflate_1")),
                entity -> entity != player && entity.distanceToSqr(center) <= Balance.i("thunder_clap_context", "discharge_threshold_1"));
        for (var target : targets) {
            context.attack(target, Balance.i("thunder_clap_context", "discharge_attack_1") + experience * Balance.i("thunder_clap_context", "discharge_attack_2"));
        }
    }
    public boolean canPerform() { return ticks >= Balance.i("thunder_clap_context", "can_perform_threshold_1"); }
    public Vec3 hit() { return hit; }
    public void perform(AbilityState state) {
        if (!canPerform()) return;
        var level = player.serverLevel();
        var effect = new ThunderClapLightningPayload(hit);
        if (player.distanceToSqr(hit) < Balance.i("thunder_clap_context", "perform_threshold_1") * Balance.i("thunder_clap_context", "perform_threshold_2")) PacketDistributor.sendToPlayer(player, effect);
        for (var viewer : level.players()) if (viewer != player && viewer.distanceToSqr(hit) < Balance.i("thunder_clap_context", "perform_threshold_3") * Balance.i("thunder_clap_context", "perform_threshold_4"))
            PacketDistributor.sendToPlayer(viewer, effect);
        // 1.12.2 weather bolts broadcast their sounds from the server. Modern visualOnly
        // still powers rods/cleans copper, so the weather graphic is client-only instead.
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                10000, .8f + level.random.nextFloat() * .2f);
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                2, .5f + level.random.nextFloat() * .2f);
        float fixed = Balance.i("thunder_clap_context", "perform_damage_1") + experience * Balance.i("thunder_clap_context", "perform_damage_2");
        float charge = (1 + ((ticks - Balance.f("thunder_clap_context", "perform_damage_3")) / Balance.f("thunder_clap_context", "perform_damage_4")) * (Balance.f("thunder_clap_context", "perform_damage_5") - 1));
        var damage = new SkillHitDamage(fixed, experience * Balance.f("thunder_clap_context", "max_health_fraction_at_mastery"));
        AbilityContext.of(player, skill).attackRange(hit, 15 + experience * 15, damage.scaled(charge),
                entity -> entity != player && !(entity instanceof net.minecraft.world.entity.LightningBolt));
        state.finishContext(skill, (int) (ticks * (Balance.i("thunder_clap_context", "perform_finish_context_1") + experience * (Balance.i("thunder_clap_context", "perform_finish_context_2") - Balance.i("thunder_clap_context", "perform_finish_context_3")))), Balance.f("thunder_clap_context", "perform_finish_context_4"));
    }
}
