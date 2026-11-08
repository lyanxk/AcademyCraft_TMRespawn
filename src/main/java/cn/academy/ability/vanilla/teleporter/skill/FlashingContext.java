package cn.academy.ability.vanilla.teleporter.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.advancement.ACAdvancements;
import cn.academy.network.FlashingContextPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import static cn.academy.network.FlashingContextPayload.*;

/** Original Flashing.MainContext: lifetime is independent of its temporary directional delegates. */
public final class FlashingContext {
    private static int next;
    public final int id = ++next, slot, request;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float experience, consumption;
    private final int maxTime, cooldown;
    private final List<ServerPlayer> audience = new ArrayList<>();
    private int ticks;
    private boolean ended;

    public FlashingContext(ServerPlayer player, AbilityState state, AbilitySkill skill, int slot, int request) {
        this.player = player; this.skill = skill; this.slot = slot; this.request = request;
        experience = state.experience(skill.id()); consumption = 13 + (6 - 13) * experience;
        maxTime = (int)(60 + (150 - 60) * experience); cooldown = (int)(900 + (400 - 900) * experience);
        audience.add(player);
        for (var viewer : player.serverLevel().players()) if (viewer != player && viewer.distanceToSqr(player) <= 625) audience.add(viewer);
    }

    public static void reject(ServerPlayer player, AbilitySkill skill, int slot, int request) {
        if (skill.name().equals("flashing") && request > 0)
            PacketDistributor.sendToPlayer(player, new FlashingContextPayload(0, player.getId(), END, slot, request, 0));
    }

    public boolean start(AbilityState state) {
        send(START);
        if (!state.consumeActiveContext(skill, 80 + (60 - 80) * experience, 250 + (180 - 250) * experience, player.isCreative())) {
            end(state); return false;
        }
        return true;
    }

    public boolean tick(AbilityState state) {
        if (ended) return false;
        // The old listener checks its counter before incrementing, including the terminating tick.
        return ticks++ <= maxTime;
    }

    public boolean perform(AbilityState state, int direction) {
        if (ended || direction < 1 || direction > 4 || !player.isAlive()
                || !state.consumeActiveContext(skill, consumption, 0, player.isCreative())) return false;
        Vec3 point = destination(player, direction, 12 + (18 - 12) * experience);
        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(point.x, point.y, point.z); player.fallDistance = 0;
        state.addExperience(skill, .002f);
        if (state.recordTeleport() >= 400) ACAdvancements.grant(player, "legacy/teleporter/mastery");
        send(STEP); return true;
    }

    public void end(AbilityState state) {
        if (ended) return;
        ended = true; state.finishContext(skill, cooldown, 0); send(END); audience.clear();
    }

    private void send(int phase) {
        var packet = new FlashingContextPayload(id, player.getId(), phase, slot, request, experience);
        for (var viewer : audience) PacketDistributor.sendToPlayer(viewer, packet);
    }

    public static Vec3 destination(Player player, int direction, double range) {
        // Exact VecUtils.rotateAroundZ followed by Vec3d.rotateYaw, with float PI/trig intermediates.
        Vec3 axis = switch (direction) {
            case 1 -> new Vec3(1, 0, 0); case 2 -> new Vec3(-1, 0, 0);
            case 3 -> new Vec3(0, 0, -1); case 4 -> new Vec3(0, 0, 1);
            default -> throw new IllegalArgumentException("Flashing direction must be 1..4");
        };
        float pitch = player.getXRot() * (float)Math.PI / 180;
        Vec3 pitched = new Vec3(axis.x * Mth.cos(pitch) + axis.y * Mth.sin(pitch),
                axis.y * Mth.cos(pitch) - axis.x * Mth.sin(pitch), axis.z);
        Vec3 rotated = pitched.yRot((-90 - player.getYRot()) * (float)Math.PI / 180);
        Vec3 end = player.getEyePosition().add(rotated.scale(range));
        var hit = ElectricAttackTrace.traceSegment(player, player.position(), end,
                entity -> entity != player && entity instanceof LivingEntity);
        if (hit.getType() == HitResult.Type.MISS) return end;
        Vec3 point = hit.getLocation(); double x = point.x, y = point.y, z = point.z;
        if (hit instanceof EntityHitResult entity) y += entity.getEntity().getEyeHeight();
        else if (hit instanceof BlockHitResult block) {
            switch (block.getDirection()) {
                case DOWN -> y -= 1; case UP -> y += 1.8;
                case NORTH -> { z -= .6; y = point.y + 1.7; }
                case SOUTH -> { z += .6; y = point.y + 1.7; }
                case WEST -> { x -= .6; y = point.y + 1.7; }
                case EAST -> { x += .6; y = point.y + 1.7; }
            }
            if (block.getDirection().get3DDataValue() > 1
                    && !player.level().getBlockState(new BlockPos((int)x, (int)(y + 1), (int)z)).isAir()) y -= 1.25;
        }
        return new Vec3(x, y, z);
    }
}
