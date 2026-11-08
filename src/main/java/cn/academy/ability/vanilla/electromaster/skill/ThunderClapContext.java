package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.ability.AbilityContext;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.ThunderClapLightningPayload;
import cn.academy.network.ThunderClapEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
            if (viewer != player && viewer.distanceToSqr(player) <= 625) audience.add(viewer);
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
        return (ticks <= 40 && !state.consumeRaw(skill, 18 + experience * 7, 0, player.isCreative())) || ticks >= 60;
    }
    public boolean canPerform() { return ticks >= 40; }
    public Vec3 hit() { return hit; }
    public void perform(AbilityState state) {
        if (!canPerform()) return;
        var level = player.serverLevel();
        var effect = new ThunderClapLightningPayload(hit);
        if (player.distanceToSqr(hit) < 512 * 512) PacketDistributor.sendToPlayer(player, effect);
        for (var viewer : level.players()) if (viewer != player && viewer.distanceToSqr(hit) < 512 * 512)
            PacketDistributor.sendToPlayer(viewer, effect);
        // 1.12.2 weather bolts broadcast their sounds from the server. Modern visualOnly
        // still powers rods/cleans copper, so the weather graphic is client-only instead.
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                10000, .8f + level.random.nextFloat() * .2f);
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                2, .5f + level.random.nextFloat() * .2f);
        float damage = (36 + experience * 36) * (1 + ((ticks - 40.0f) / 60.0f) * (1.2f - 1));
        AbilityContext.of(player, skill).attackRange(hit, 15 + experience * 15, damage,
                entity -> entity != player && !(entity instanceof net.minecraft.world.entity.LightningBolt));
        state.finishContext(skill, (int) (ticks * (10 + experience * (6 - 10))), .003f);
    }
}
