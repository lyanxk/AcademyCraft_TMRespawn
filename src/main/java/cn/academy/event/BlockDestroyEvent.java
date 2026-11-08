package cn.academy.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Original per-position destruction veto; a canceled position stops that beam line. */
public final class BlockDestroyEvent extends Event implements ICancellableEvent {
    public final Level world;
    public final Player player;
    public final BlockPos pos;
    public final cn.academy.ability.AbilitySkill skill;
    public BlockDestroyEvent(Level world, Player player, BlockPos pos) {
        this(world, player, pos, null);
    }
    public BlockDestroyEvent(Level world, Player player, BlockPos pos, cn.academy.ability.AbilitySkill skill) {
        this.world = world; this.player = player; this.pos = pos.immutable(); this.skill = skill;
    }
    public BlockDestroyEvent(Level world, BlockPos pos) { this(world, null, pos); }
    public BlockDestroyEvent(Player player, BlockPos pos) { this(player.level(), player, pos); }
}
