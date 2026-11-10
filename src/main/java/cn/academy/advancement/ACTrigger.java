package cn.academy.advancement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
/** Datapack criteria can filter the category, skill and ability level of an event. */
public final class ACTrigger extends SimpleCriterionTrigger<ACTrigger.Instance> {
    private static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("category").forGetter(Instance::category),
            Codec.STRING.optionalFieldOf("skill").forGetter(Instance::skill),
            Codec.intRange(0,5).optionalFieldOf("minimum_level",0).forGetter(Instance::minimumLevel)
    ).apply(instance, Instance::new));
    @Override public Codec<Instance> codec() { return CODEC; }
    public void trigger(ServerPlayer player) { trigger(player, ""); }
    public void trigger(ServerPlayer player, String skill) {
        var state = cn.academy.ability.AbilityState.load(player);
        trigger(player, condition -> condition.category().map(state.category()::equals).orElse(true)
                && condition.skill().map(skill::equals).orElse(true) && state.level() >= condition.minimumLevel());
    }
    public record Instance(Optional<String> category, Optional<String> skill, int minimumLevel) implements SimpleInstance {
        @Override public Optional<ContextAwarePredicate> player() { return Optional.empty(); }
    }
}
