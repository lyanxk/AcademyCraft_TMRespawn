package cn.academy.energy;

/** Literal arithmetic from 1.12.2 WirelessNet.tick; its buffer follows the node delta's sign. */
public final class MatrixBalance {
    public static final double BUFFER_MAX = 2000;
    public record Step(double delta, double buffer, double remaining) {}
    public static Step step(double current, double target, double nodeBandwidth, double remaining, double buffer) {
        double delta = target - current;
        delta = Math.signum(delta) * Math.min(Math.abs(delta), Math.min(remaining, nodeBandwidth));
        if (buffer + delta > BUFFER_MAX) delta = BUFFER_MAX - buffer;
        else if (buffer + delta < 0) delta = -buffer;
        return new Step(delta, buffer + delta, remaining - Math.abs(delta));
    }
    private MatrixBalance() {}
}
