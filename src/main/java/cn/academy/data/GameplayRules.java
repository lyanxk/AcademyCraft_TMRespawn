package cn.academy.data;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/** Registry-aware helpers shared by machines, items and server/client prediction. */
public final class GameplayRules {
    public static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    public static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }
    public static Fluid fluid(String id) { return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id)); }
    public static boolean itemMatches(ItemStack stack, String id) {
        return id.startsWith("#") ? stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1)))) : stack.is(item(id));
    }
    public static TagKey<Item> itemTag(String id) { return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("academy", id)); }
    public static void dropLoot(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 point, String id) {
        var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id)));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, point)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        table.getRandomItems(params, stack -> level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, point.x, point.y, point.z, stack)));
    }
    public static JsonObject core(int level) {
        var cores = GameplayData.value("wireless", "cores").getAsJsonArray();
        return cores.get(Math.clamp(level - 1, 0, cores.size() - 1)).getAsJsonObject();
    }
    public static String machine(cn.academy.block.MachineKind kind) { return "machines/" + kind.name().toLowerCase(java.util.Locale.ROOT); }
    public static int tankCapacity(cn.academy.block.MachineKind kind) {
        var document = GameplayData.document(machine(kind));
        return document.has("tank_capacity") ? document.get("tank_capacity").getAsInt() : 0;
    }
    public static boolean recipeFluid(cn.academy.crafting.MachineRecipes.Recipe recipe, cn.academy.block.MachineKind kind) {
        var profile = GameplayData.document(machine(kind));
        return recipe.fluid().isEmpty() || profile.has("fluid") && recipe.fluid().is(fluid(profile.get("fluid").getAsString()));
    }
    public static int[][] offsets(cn.academy.block.MachineKind kind) {
        var values = GameplayData.document("multiblocks").get(kind.name().toLowerCase(java.util.Locale.ROOT));
        if (values == null) return new int[0][];
        int[][] result = new int[values.getAsJsonArray().size()][3];
        for (int i = 0; i < result.length; i++) for (int j = 0; j < 3; j++) result[i][j] = values.getAsJsonArray().get(i).getAsJsonArray().get(j).getAsInt();
        return result;
    }
    private GameplayRules() { }
}
