package cn.academy.tutorial;

import net.minecraft.world.entity.player.Player;
import java.util.function.Predicate;

/** Original composable tutorial predicate, evaluated against the owning player. */
@FunctionalInterface
public interface Condition extends Predicate<Player> {
    default Condition or(Condition other) { return player -> test(player) || other.test(player); }
    default Condition and(Condition other) { return player -> test(player) && other.test(player); }
}
