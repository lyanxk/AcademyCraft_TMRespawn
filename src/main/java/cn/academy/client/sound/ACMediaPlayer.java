package cn.academy.client.sound;

import cn.academy.AcademyCraft;
import cn.academy.client.sound.ACMediaLibrary.Track;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.config.ACHudConfig;
import cn.academy.mixin.MediaSoundEngineAccess;
import cn.academy.mixin.MediaSoundManagerAccess;
import cn.academy.mixin.MediaChannelAccess;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** MediaBackend: one independent streaming source, with the actual audio channel's playback clock. */
public final class ACMediaPlayer {
    private static final class Playback {
        final Track track;
        volatile ChannelAccess.ChannelHandle handle;
        MediaChannelClock clock;
        volatile boolean started, paused;
        volatile float seconds, volume;
        final java.util.concurrent.atomic.AtomicReference<AudioStream> pending = new java.util.concurrent.atomic.AtomicReference<>();
        Playback(Track track, float volume) { this.track = track; this.volume = volume; }
    }
    private static volatile Playback playback;
    private static Track lastPlayed;
    private static float scheduler = .25f;
    private static double hudUpdated = -1;
    private static Component hudTitle = Component.empty(), hudTime = Component.empty();
    private static float hudProgress;

    public static void play(Track track) {
        if (track == null) return;
        stop();
        var minecraft = Minecraft.getInstance();
        var engine = (MediaSoundEngineAccess) ((MediaSoundManagerAccess) minecraft.getSoundManager()).academy$soundEngine();
        if (!engine.academy$loaded()) return;
        Playback requested = new Playback(track, volume());
        playback = requested;
        lastPlayed = track;
        CompletableFuture.supplyAsync(() -> open(track), Util.nonCriticalIoPool()).whenComplete((stream, error) -> {
            if (error != null) { failed(requested, error); return; }
            requested.pending.set(stream);
            minecraft.execute(() -> {
                if (playback != requested || !engine.academy$loaded()) {
                    closePending(requested);
                    if (playback == requested) stop();
                    return;
                }
                engine.academy$channels().createHandle(Library.Pool.STREAMING).thenAccept(handle -> {
                    if (handle == null) { closePending(requested); failed(requested, new IOException("No streaming channel available")); return; }
                    requested.handle = handle;
                    handle.execute(channel -> {
                        AudioStream ready = requested.pending.getAndSet(null);
                        if (ready == null) { channel.stop(); return; }
                        if (playback != requested) { close(ready); channel.stop(); return; }
                        boolean attached = false;
                        try {
                            requested.clock = new MediaChannelClock(ready, ((MediaChannelAccess) channel).academy$source());
                            channel.setPitch(1);
                            channel.setLooping(false);
                            channel.disableAttenuation();
                            channel.setRelative(true);
                            channel.setSelfPosition(Vec3.ZERO);
                            channel.setVolume(requested.volume);
                            attached = true; // Channel takes ownership before it starts pumping buffers.
                            channel.attachBufferStream(requested.clock);
                            channel.play();
                            if (requested.paused) channel.pause();
                            requested.started = true;
                            sample(requested, channel);
                        } catch (RuntimeException exception) {
                            if (!attached) close(ready);
                            channel.stop();
                            failed(requested, exception);
                        }
                    });
                });
            });
        });
    }
    private static AudioStream open(Track track) {
        try {
            var input = Files.newInputStream(track.source());
            try { return new JOrbisAudioStream(input); }
            catch (Throwable error) { input.close(); throw error; }
        } catch (IOException exception) { throw new CompletionException(exception); }
    }
    private static void close(AudioStream stream) {
        try { stream.close(); }
        catch (IOException exception) { AcademyCraft.LOGGER.warn("Cannot close media stream", exception); }
    }
    private static void closePending(Playback current) {
        AudioStream stream = current.pending.getAndSet(null);
        if (stream != null) close(stream);
    }
    private static void failed(Playback requested, Throwable error) {
        AcademyCraft.LOGGER.warn("Cannot play media {}", requested.track.source(), error);
        Minecraft.getInstance().execute(() -> { if (playback == requested) stop(); });
    }
    private static void sample(Playback current, Channel channel) {
        if (current.clock == null) return;
        current.seconds = current.clock.seconds();
        current.paused = current.clock.paused();
    }
    public static void togglePause(Track first) {
        if (playback == null) play(lastPlayed == null ? first : lastPlayed);
        else if (playback.paused) resume();
        else pause();
    }
    public static void pause() {
        Playback current = playback;
        if (current == null) return;
        current.paused = true;
        if (current.handle != null) current.handle.execute(channel -> { channel.pause(); sample(current, channel); });
    }
    public static void resume() {
        Playback current = playback;
        if (current == null) return;
        current.paused = false;
        if (current.handle != null) current.handle.execute(channel -> { channel.unpause(); sample(current, channel); });
    }
    public static void stop() {
        Playback old = playback;
        playback = null;
        if (old != null) {
            closePending(old);
            if (old.handle != null) old.handle.execute(Channel::stop);
        }
    }
    public static void reset() {
        stop(); lastPlayed = null; scheduler = .25f;
        hudUpdated = -1; hudTitle = Component.empty(); hudTime = Component.empty(); hudProgress = 0;
    }
    public static Track current() { return playback == null ? null : playback.track; }
    public static boolean paused() { return playback != null && playback.paused; }
    public static boolean suppressesBackgroundMusic() {
        Playback current = playback;
        return current != null && current.started && !current.paused;
    }
    public static float elapsedSeconds() { return playback == null ? 0 : playback.seconds; }
    public static float progress() {
        Track track = current();
        return track == null || track.lengthSeconds() <= 0 ? 0 : elapsedSeconds() / track.lengthSeconds();
    }
    public static float volume() { return ACMediaLibrary.volume(); }
    public static void setVolume(float volume) {
        float value = Mth.clamp(volume, 0, 1);
        ACMediaLibrary.volume(value);
        Playback current = playback;
        if (current != null) {
            current.volume = value;
            if (current.handle != null) current.handle.execute(channel -> channel.setVolume(value));
        }
    }
    public static void tick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        Playback current = playback;
        if (current != null && current.handle != null && current.started)
            current.handle.execute(channel -> sample(current, channel));
        // Original TickScheduler.every(5): preserve its float-second counter and initial phase.
        scheduler -= .05f;
        if (scheduler > 0) return;
        scheduler += .25f;
        if (current == null) return;
        if (current.handle != null && current.handle.isStopped()) stop();
    }

    public static void renderHud(GuiGraphics gui, Minecraft minecraft) {
        if (current() == null) return;
        double now = cn.academy.client.TerminalOverlay.seconds();
        if (hudUpdated < 0 || now - hudUpdated > .5) {
            hudUpdated = now;
            hudTitle = current().name(); hudTime = Component.literal(formatTime(elapsedSeconds())); hudProgress = progress();
        }
        var element = ACHudConfig.Element.MEDIA;
        var bounds = element.bounds(gui.guiWidth(), gui.guiHeight(), ACHudConfig.position(element), false);
        gui.pose().pushPose();
        gui.pose().translate(bounds.x(), bounds.y(), 0);
        gui.pose().scale(bounds.scale(), bounds.scale(), 1);
        drawHud(gui, hudTitle, hudTime, hudProgress);
        gui.pose().popPose();
    }
    public static void renderPreview(GuiGraphics gui) {
        drawHud(gui, Component.literal("Only My Railgun"), Component.literal("04:30"), .5f);
    }
    private static void drawHud(GuiGraphics gui, Component title, Component time, float progress) {
        rect(gui, 14, 27, 120 * Mth.clamp(progress, 0, 1), 1.5f, 0xCCFFFFFF);
        ACLegacyFont.draw(gui, emit(title, 10, 100), 13, 17, 10, 0xFFFFFFFF, ACLegacyFont.LEFT, 0, false);
        rect(gui, 14, 27.2f, 120, 1.1f, 0x33000000);
        ACLegacyFont.draw(gui, emit(time, 8.5f, 25), 117, 28.5f, 8.5f, 0xFFFFFFFF, ACLegacyFont.LEFT, 0, false);
    }
    private static Component emit(Component text, float size, float width) {
        String value = text.getString(); float advance = 0; int end = 0;
        while (end < value.length() && advance < width) {
            int next = value.offsetByCodePoints(end, 1);
            advance += ACLegacyFont.width(Component.literal(value.substring(end, next)), size, false);
            end = next;
        }
        return Component.literal(value.substring(0, end));
    }
    private static void rect(GuiGraphics gui, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0) return;
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(width, height, 1);
        gui.fill(0, 0, 1, 1, color); gui.pose().popPose();
    }
    public static String formatTime(float seconds) {
        int value = Math.max(0, (int) seconds);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", value / 60, value % 60);
    }
    private ACMediaPlayer() {}
}
