package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.data.Balance;
import cn.academy.ability.SkillHitDamage;

import cn.academy.ability.*;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.network.RailgunEffectPayload;
import cn.academy.registry.ACItems;
import cn.academy.util.RangedRayDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.network.PacketDistributor;


/** Railgun.scala's item/coin delegate and server perform handlers. */
public final class Railgun {
    // Preserved from the original singleton: a reflected entity hit latches the larger XP reward.
    private static boolean hitEntity;
    public static boolean isAccepted(ItemStack stack) { return stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "railgun_ammunition"))); }
    private static AbilitySkill skill() { return AbilityRegistry.skill("electromaster.railgun"); }

    public static void onThrow(ServerPlayer player, ACThrownItemEntity coin) {
        AbilityState state = AbilityState.load(player);
        if (!state.canUse() || !state.learned().contains(skill().id())) return;
        var payload = new cn.academy.network.RailgunChargePayload(player.getId());
        for (ServerPlayer viewer : player.serverLevel().players())
            if (viewer.distanceToSqr(player) <= Balance.i("railgun", "on_throw_threshold_1")) PacketDistributor.sendToPlayer(viewer, payload);
    }

    /** Original client QTE/charge completion. Never reinterpret the local QTE using delayed server progress. */
    public static boolean performClientRequest(ServerPlayer player, int slot, boolean coinRequest) {
        AbilityState state = AbilityState.load(player);
        if (slot < 0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS || !skill().id().equals(state.controlSkill(slot)) || !state.canStartContext(skill())) return false;
        if (!coinRequest) return fireItem(player, state);
        ACThrownItemEntity coin = playerCoin(player);
        if (coin == null) return false;
        coin.consumeForRailgun();
        return performServer(player, state);
    }

    public static ACThrownItemEntity playerCoin(ServerPlayer player) {
        for (Entity entity : player.serverLevel().getAllEntities())
            if (entity instanceof ACThrownItemEntity coin && !coin.isRemoved()
                    && coin.getItem().is(ACItems.COIN.get()) && coin.getOwner() == player) return coin;
        return null;
    }

    public static boolean fireItem(ServerPlayer player, AbilityState state) {
        ItemStack stack = player.getMainHandItem();
        if (!isAccepted(stack)) return false;
        if (!player.isCreative()) stack.shrink(1);
        return performServer(player, state);
    }

    private static boolean performServer(ServerPlayer player, AbilityState state) {
        AbilitySkill skill = skill();
        float exp = state.experience(skill.id());
        boolean enhanced = state.level() >= Balance.i("railgun", "perform_server_threshold_1");
        float baseExp = enhanced ? 1 : exp;
        double radiusScale = enhanced ? Balance.i("railgun", "perform_server_radius_scale_1") : 1;
        if (!state.consumeRaw(skill, enhanced ? Balance.i("railgun", "perform_server_consume_raw_1") + exp * Balance.i("railgun", "perform_server_consume_raw_2") : Balance.i("railgun", "perform_server_consume_raw_3") + exp * Balance.i("railgun", "perform_server_consume_raw_4"),
                enhanced ? Balance.i("railgun", "perform_server_consume_raw_5") + exp * Balance.i("railgun", "perform_server_consume_raw_6") : Balance.i("railgun", "perform_server_consume_raw_7") + exp * -Balance.i("railgun", "perform_server_consume_raw_8"), player.isCreative())) return false;
        AbilityContext context = AbilityContext.of(player, skill);
        double[] length = {90};
        var damage = new RangedRayDamage.Reflectible(context, Balance.i("railgun", "perform_server_damage_1") * radiusScale, Balance.i("railgun", "perform_server_damage_2") + baseExp * Balance.i("railgun", "perform_server_damage_3"), reflector -> {
            var result = ElectricAttackTrace.trace(reflector, Balance.i("railgun", "perform_server_trace_1"), false);
            if (result instanceof EntityHitResult entity) { context.attack(entity.getEntity(), Balance.i("railgun", "perform_server_attack_1")); hitEntity = true; }
            double distance = reflector.distanceTo(player);
            length[0] = Math.min(length[0], distance);
            visual(player, reflector.getId(), Balance.i("railgun", "perform_server_damage_4"), radiusScale);
        });
        damage.maxIncrement = Balance.i("railgun", "perform_server_damage_max_increment_1");
        float fixedDamage = enhanced ? Balance.i("railgun", "perform_server_damage_start_damage_1") + exp * Balance.i("railgun", "perform_server_damage_start_damage_2") : Balance.i("railgun", "perform_server_damage_start_damage_3") + exp * Balance.i("railgun", "perform_server_damage_start_damage_4");
        float fraction = enhanced ? exp * Balance.f("railgun", "enhanced_max_health_fraction") : 0;
        damage.startDamage = new SkillHitDamage(fixedDamage, fraction);
        damage.perform();
        state.addExperience(skill, hitEntity ? Balance.f("railgun", "perform_server_add_experience_1") : Balance.f("railgun", "perform_server_add_experience_2"));
        state.finishContext(skill, enhanced ? (int) (Balance.i("railgun", "perform_server_finish_context_1") - exp * Balance.i("railgun", "perform_server_finish_context_2")) : (int) (Balance.i("railgun", "perform_server_finish_context_3") + exp * -Balance.i("railgun", "perform_server_finish_context_4")), 0);
        visual(player, -1, length[0], radiusScale);
        AbilityManager.completeUse(player, state, skill);
        return true;
    }

    private static void visual(ServerPlayer player, int reflector, double length, double radiusScale) {
        var payload = new RailgunEffectPayload(player.getId(), reflector, length, radiusScale);
        for (ServerPlayer viewer : player.serverLevel().players())
            if (viewer.distanceToSqr(player) <= 400) PacketDistributor.sendToPlayer(viewer, payload);
    }

    private Railgun() {}
}
