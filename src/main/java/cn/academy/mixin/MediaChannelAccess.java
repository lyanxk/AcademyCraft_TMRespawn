package cn.academy.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only source access; does not intercept playback or buffer management. */
@Mixin(Channel.class)
public interface MediaChannelAccess {
    @Accessor("source") int academy$source();
}
