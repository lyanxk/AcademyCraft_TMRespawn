package cn.academy.mixin;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface MediaSoundEngineAccess {
    @Accessor("channelAccess") ChannelAccess academy$channels();
    @Accessor("loaded") boolean academy$loaded();
}
