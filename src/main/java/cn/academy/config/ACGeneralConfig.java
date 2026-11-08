package cn.academy.config;

import cn.academy.network.SettingsPolicyPayload;
import net.minecraft.world.level.Level;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

/** The original generic world/attack policy belongs to the installation, not a player or save. */
public final class ACGeneralConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ATTACK_PLAYERS;
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WORLDS_DESTROYING_BLOCKS;
    private static volatile Policy remotePolicy;

    public record Policy(boolean attackPlayers, boolean destroyBlocks, List<String> worldsDestroyingBlocks) {
        public Policy { worldsDestroyingBlocks = List.copyOf(worldsDestroyingBlocks); }
    }

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Original academy.cfg generic policy, shared by all local worlds.").push("generic");
        ATTACK_PLAYERS = builder.comment("Whether skills can directly damage another player.").define("attackPlayer", true);
        DESTROY_BLOCKS = builder.comment("Whether destructive skills may break blocks. Meltdowner skills ignore this global switch.").define("destroyBlocks", false);
        WORLDS_DESTROYING_BLOCKS = builder.comment("Worlds allowed to break blocks when destroyBlocks is false. Dimension IDs, vanilla numeric IDs or dimension save paths.")
                .defineListAllowEmpty("worldsWhitelistedDestroyingBlocks", List.of(), () -> "minecraft:overworld", value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    public static Policy localPolicy() {
        return new Policy(ATTACK_PLAYERS.get(), DESTROY_BLOCKS.get(), List.copyOf(WORLDS_DESTROYING_BLOCKS.get()));
    }

    public static Policy policy(Level level) {
        Policy remote = remotePolicy;
        return level != null && level.isClientSide && remote != null ? remote : localPolicy();
    }

    /** Received server policy never modifies the local spec or its backing file. */
    public static void receiveRemote(Policy policy) { remotePolicy = policy; }
    public static void clearRemote() { remotePolicy = null; }

    public static SettingsPolicyPayload payload() {
        Policy policy = localPolicy();
        return new SettingsPolicyPayload(policy.attackPlayers, policy.destroyBlocks, policy.worldsDestroyingBlocks);
    }

    public static void broadcast() { PacketDistributor.sendToAllPlayers(payload()); }

    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(ACGeneralConfig::broadcast);
    }

    private ACGeneralConfig() { }
}
