package cn.academy.data;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FluidState;
public final class FluidReactions {
    public static JsonObject find(FluidState source, FluidState neighbor) {
        String sourceId = BuiltInRegistries.FLUID.getKey(source.getType()).toString();
        String neighborId = BuiltInRegistries.FLUID.getKey(neighbor.getType()).toString();
        for (var entry : GameplayData.value("fluid_interactions", "reactions").getAsJsonArray()) {
            var reaction = entry.getAsJsonObject();
            var sourceFluid = GameplayRules.fluid(reaction.get("source").getAsString());
            var neighborFluid = GameplayRules.fluid(reaction.get("neighbor").getAsString());
            if (source.getType().isSame(sourceFluid) && neighbor.getType().isSame(neighborFluid)) return reaction;
        }
        return null;
    }
    private FluidReactions() { }
}
