package cn.academy.ability.vanilla.electromaster.skill;

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
    public static boolean isAccepted(ItemStack stack) { return stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_BLOCK); }
    private static AbilitySkill skill() { return AbilityRegistry.skill("electromaster.railgun"); }

    public static void onThrow(ServerPlayer player, ACThrownItemEntity coin) {
        AbilityState state = AbilityState.load(player);
        if (!state.canUse() || !state.learned().contains(skill().id())) return;
        var payload = new cn.academy.network.RailgunChargePayload(player.getId());
        for (ServerPlayer viewer : player.serverLevel().players())
            if (viewer.distanceToSqr(player) <= 900) PacketDistributor.sendToPlayer(viewer, payload);
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
        if (!state.consumeRaw(skill, 200 + exp * 250, 180 + exp * -60, player.isCreative())) return false;
        AbilityContext context = AbilityContext.of(player, skill);
        double[] length = {45};
        var damage = new RangedRayDamage.Reflectible(context, 2, 900 + exp * 1100, reflector -> {
            var result = ElectricAttackTrace.trace(reflector, 15, false);
            if (result instanceof EntityHitResult entity) { context.attack(entity.getEntity(), 14); hitEntity = true; }
            double distance = reflector.distanceTo(player);
            length[0] = Math.min(length[0], distance);
            visual(player, reflector.getId(), 15);
        });
        damage.startDamage = 60 + exp * 50;
        damage.perform();
        state.addExperience(skill, hitEntity ? .01f : .005f);
        state.finishContext(skill, (int) (300 + exp * -140), 0);
        visual(player, -1, length[0]);
        AbilityManager.completeUse(player, state, skill);
        return true;
    }

    private static void visual(ServerPlayer player, int reflector, double length) {
        var payload = new RailgunEffectPayload(player.getId(), reflector, length);
        for (ServerPlayer viewer : player.serverLevel().players())
            if (viewer.distanceToSqr(player) <= 400) PacketDistributor.sendToPlayer(viewer, payload);
    }

    private Railgun() {}
}
