package cn.academy.client.sound;

import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lifecycle manager replacing LambdaLib's FollowEntitySound for ability contexts. */
public final class ACContextSounds {
    private record Key(UUID entity, String sound, int context) { }
    private static final Map<Key, FollowingLoop> PLAYING = new HashMap<>();

    public static void handle(UUID entity, String sound, boolean start, float volume) {
        handle(entity, sound, start, volume, SoundSource.PLAYERS);
    }
    public static void handle(UUID entity, String sound, boolean start, float volume, SoundSource source) {
        handle(entity,sound,start,volume,source,0);
    }
    public static void handle(UUID entity,String sound,boolean start,float volume,SoundSource source,int context) {
        play(entity,sound,start,volume,source,context,true);
    }
    public static void once(UUID entity,String sound,float volume,SoundSource source,int context) {
        play(entity,sound,true,volume,source,context,false);
    }
    /** Original playClient creates a fresh FollowEntitySound for every call, allowing overlap. */
    public static void once(Player player, String sound, float volume, SoundSource source) {
        FollowingLoop instance = new FollowingLoop(player, sound, volume,
                new Key(player.getUUID(), sound, 0), source, false);
        // The sound engine owns this one-shot; retaining it in PLAYING would leak finished instances.
        Minecraft.getInstance().getSoundManager().play(instance);
    }
    private static void play(UUID entity,String sound,boolean start,float volume,SoundSource source,int context,boolean looping) {
        Minecraft minecraft = Minecraft.getInstance();
        Key key = new Key(entity, sound, context);
        FollowingLoop previous = PLAYING.remove(key);
        if (previous != null) previous.halt();
        if (!start || minecraft.level == null) return;
        Player player = minecraft.level.getPlayerByUUID(entity);
        if (player == null) return;
        FollowingLoop loop = new FollowingLoop(player, sound, volume, key, source,looping);
        PLAYING.put(key, loop);
        minecraft.getSoundManager().play(loop);
    }

    public static void stopAll() {
        new java.util.ArrayList<>(PLAYING.values()).forEach(FollowingLoop::halt);
        PLAYING.clear();
    }

    private static final class FollowingLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private final Key key;

        private FollowingLoop(Player player, String sound, float volume, Key key, SoundSource source,boolean looping) {
            super(ACSounds.get(sound), source, RandomSource.create());
            this.player = player;
            this.key = key;
            this.volume = volume;
            this.pitch = 1;
            this.looping = looping;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = player.getX(); this.y = player.getY(); this.z = player.getZ();
        }

        @Override public void tick() {
            if (player.isRemoved() || Minecraft.getInstance().level == null) {
                halt();
                return;
            }
            x = player.getX(); y = player.getY(); z = player.getZ();
        }

        private void halt() {
            stop();
            PLAYING.remove(key, this);
        }
    }

    private ACContextSounds() { }
}
