package cn.academy.client.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/** Counts only our decoded buffers, sampled on Minecraft's sound executor. */
public final class MediaChannelClock implements AudioStream {
    private final AudioStream stream;
    private final int source;
    private final double bytesPerSecond;
    private final ArrayDeque<Double> buffers = new ArrayDeque<>();
    private double removedSeconds;

    public MediaChannelClock(AudioStream stream, int source) {
        this.stream = stream;
        this.source = source;
        AudioFormat format = stream.getFormat();
        bytesPerSecond = format.getSampleRate() * format.getChannels() * format.getSampleSizeInBits() / 8.0;
    }

    @Override public AudioFormat getFormat() { return stream.getFormat(); }

    @Override public ByteBuffer read(int size) throws IOException {
        ByteBuffer buffer = stream.read(size);
        if (buffer != null) buffers.addLast(buffer.remaining() / bytesPerSecond);
        return buffer;
    }

    public float seconds() {
        // Channel queues one AL buffer per read. Compare the native queue length with our
        // decoded-buffer ledger instead of replacing Channel.removeProcessedBuffers.
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        while (buffers.size() > queued) removedSeconds += buffers.removeFirst();
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED
                && AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED) == queued)
            return (float) (removedSeconds + buffers.stream().mapToDouble(Double::doubleValue).sum());
        return (float) (removedSeconds + AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET));
    }

    public boolean paused() { return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PAUSED; }

    @Override public void close() throws IOException { stream.close(); }
}
