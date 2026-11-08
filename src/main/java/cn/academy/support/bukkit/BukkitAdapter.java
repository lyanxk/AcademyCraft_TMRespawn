package cn.academy.support.bukkit;

import cn.academy.AcademyCraft;
import cn.academy.event.BlockDestroyEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.lang.reflect.Method;
import java.util.UUID;

/** Original AcademyBukkit reflection contract; inactive until a server adapter calls init. */
public final class BukkitAdapter {
    public static final BukkitAdapter INSTANCE = new BukkitAdapter();
    public static final UUID dummy = UUID.randomUUID();
    private static boolean registered;
    private static Method setWorld, setPlayer, checkBlockDestroy;
    private ServerLevel world;
    private Player player;

    public static void init(Class<?> adapter) {
        try {
            Method nextWorld = adapter.getMethod("setWorld", String.class);
            Method nextPlayer = adapter.getMethod("setPlayer", UUID.class);
            Method nextCheck = adapter.getMethod("checkBlockDestroy", int.class, int.class, int.class);
            setWorld = nextWorld; setPlayer = nextPlayer; checkBlockDestroy = nextCheck;
            INSTANCE.world = null; INSTANCE.player = null;
            if (!registered) { NeoForge.EVENT_BUS.register(INSTANCE); registered = true; }
        } catch (ReflectiveOperationException | SecurityException exception) {
            AcademyCraft.LOGGER.error("Failed to load BukkitAdapter", exception);
        }
    }

    @SubscribeEvent public void onBlockDestroy(BlockDestroyEvent event) {
        if (!(event.world instanceof ServerLevel level)) return;
        try {
            boolean changedWorld = world != level;
            if (changedWorld) {
                setWorld.invoke(null, level.getServer().getWorldData().getLevelName());
                world = level;
            }
            if (changedWorld || player != event.player) {
                setPlayer.invoke(null, event.player == null ? dummy : event.player.getUUID());
                player = event.player;
            }
            event.setCanceled((boolean) checkBlockDestroy.invoke(null,
                    event.pos.getX(), event.pos.getY(), event.pos.getZ()));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AcademyCraft.LOGGER.error("Failed to handle BlockDestroyEvent through BukkitAdapter", exception);
        }
    }

    @SubscribeEvent public void serverStopped(ServerStoppedEvent event) { world = null; player = null; }
    private BukkitAdapter() { }
}
