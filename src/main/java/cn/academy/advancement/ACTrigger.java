package cn.academy.advancement;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Original ACTrigger: no JSON conditions; dispatch to every listening criterion. */
public final class ACTrigger extends SimpleCriterionTrigger<ACTrigger.Instance> {
    private static final Codec<Instance> CODEC = Codec.unit(new Instance());

    @Override
    public Codec<Instance> codec() { return CODEC; }

    public void trigger(ServerPlayer player) { trigger(player, instance -> true); }

    public record Instance() implements SimpleInstance {
        @Override
        public Optional<ContextAwarePredicate> player() { return Optional.empty(); }
    }
}
