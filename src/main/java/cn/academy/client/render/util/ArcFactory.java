package cn.academy.client.render.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Geometry from the original ArcFactory; GL display lists are replaced by cached vertex lists. */
public final class ArcFactory {
    private static final Random FACTORY_RANDOM = new Random(), UTILITY_RANDOM = new Random();
    private final Random random, utilityRandom;
    public double width = .1, lengthShrink = .7, alphaShrink = .9, maxOffset = 1.5, branchFactor = .4, widthShrink = .7;
    public int passes = 6;
    public Vec3 normal = new Vec3(0, 0, 1);
    private final List<List<Segment>> listAll = new ArrayList<>(), bufferAll = new ArrayList<>();

    public ArcFactory() { this(FACTORY_RANDOM, UTILITY_RANDOM); }
    public ArcFactory(Random random, Random utilityRandom) { this.random = random; this.utilityRandom = utilityRandom; }

    private static final class Point {
        Vec3 position;
        final double width;
        Point(Vec3 position, double width) { this.position = position; this.width = width; }
    }
    private static final class Segment {
        final Point start;
        Point end;
        final double alpha;
        Segment(Point start, Point end, double alpha) { this.start = start; this.end = end; this.alpha = alpha; }
    }
    public record Vertex(Vec3 position, double u, double v, double alpha) {}

    private void handleSingle(List<Segment> list, List<Segment> buffer, double offset) {
        buffer.clear();
        for (Segment segment : list) {
            Point average = new Point(segment.start.position.lerp(segment.end.position, .5),
                    (segment.start.width + segment.end.width) / 2);
            float theta = (float) (random.nextFloat() * Math.PI * 2);
            double sin = Mth.sin(theta), cos = Mth.cos(theta), off = random.nextFloat() * offset;
            average.position = average.position.add(0, off * sin, off * cos);
            Segment second = new Segment(average, segment.end, segment.alpha);
            segment.end = average;
            buffer.add(segment); buffer.add(second);
            if (random.nextDouble() < branchFactor) {
                Vec3 direction = randomRotate(10, average.position.subtract(segment.start.position).scale(lengthShrink), utilityRandom);
                double branchWidth = average.width * widthShrink;
                var branch = new ArrayList<Segment>();
                branch.add(new Segment(new Point(average.position, branchWidth),
                        new Point(average.position.add(direction), branchWidth), segment.alpha * alphaShrink));
                bufferAll.add(branch); listAll.add(new ArrayList<>());
            }
        }
    }

    public Arc[] generateList(int count, double lengthFrom, double lengthTo) {
        Arc[] result = new Arc[count];
        for (int i = 0; i < count; i++) result[i] = generate(lengthFrom + utilityRandom.nextDouble() * (lengthTo - lengthFrom));
        return result;
    }

    public Arc generate(double length) {
        listAll.clear(); bufferAll.clear();
        var initial = new ArrayList<Segment>();
        initial.add(new Segment(new Point(Vec3.ZERO, width), new Point(new Vec3(length, 0, 0), width), 1));
        listAll.add(initial); bufferAll.add(new ArrayList<>());
        boolean flip = false;
        double offset = maxOffset;
        for (int pass = 0; pass < passes; pass++) {
            // Deliberately re-read the size: original branch insertion participates in this pass.
            for (int j = 0; j < listAll.size(); j++) {
                if (flip) handleSingle(bufferAll.get(j), listAll.get(j), offset);
                else handleSingle(listAll.get(j), bufferAll.get(j), offset);
            }
            flip = !flip; offset /= 2;
        }
        return new Arc(flip ? bufferAll : listAll, normal, length, utilityRandom);
    }

    private static float ranged(Random random, float from, float to) { return from + random.nextFloat() * (to - from); }
    private static Vec3 randomRotate(float range, Vec3 direction, Random random) {
        float angle = (float) (ranged(random, -range, range) / 180 * Math.PI);
        Vec3 result = direction.xRot(ranged(random, -angle, angle)).yRot(ranged(random, -angle, angle));
        float z = ranged(random, -angle, angle), cosine = Mth.cos(z), sine = Mth.sin(z);
        return new Vec3(result.x * cosine + result.y * sine, result.y * cosine - result.x * sine, result.z);
    }

    private static Vec3 normalizeLegacy(Vec3 vector) {
        // 1.12.2 Vec3d.normalize uses MathHelper.sqrt(double), whose result is a float.
        double length = (float) Math.sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z);
        return length < 1.0e-4 ? Vec3.ZERO : new Vec3(vector.x / length, vector.y / length, vector.z / length);
    }

    public static final class Arc {
        private final List<List<Segment>> segments;
        private final Vec3 normal;
        private final Random random;
        private final List<Vertex> fixedVertices;
        public final double length;
        Arc(List<List<Segment>> segments, Vec3 normal, double length, Random random) {
            this.segments = new ArrayList<>(segments); this.normal = normal; this.length = length; this.random = random;
            fixedVertices = buildVertices(23333333);
        }
        public List<Vertex> fixedVertices() { return fixedVertices; }
        public List<Vertex> vertices(double length) { return buildVertices(length); }

        private List<Vertex> buildVertices(double limit) {
            var vertices = new ArrayList<Vertex>();
            for (List<Segment> branch : segments) {
                Vec3 lastDirection = null;
                for (Segment segment : branch) {
                    // Original draw(length) stops at the segment start; it does not clip its end to length.
                    if (segment.start.position.x > limit) break;
                    Vec3 direction = normalizeLegacy(randomRotate(15, segment.end.position.subtract(segment.start.position).cross(normal), random));
                    if (lastDirection == null) lastDirection = direction;
                    Vec3 first = segment.start.position.add(lastDirection.scale(segment.start.width));
                    Vec3 second = segment.start.position.add(lastDirection.scale(-segment.start.width));
                    Vec3 third = segment.end.position.add(direction.scale(segment.end.width));
                    Vec3 fourth = segment.end.position.add(direction.scale(-segment.end.width));
                    vertices.add(new Vertex(first, 0, 0, segment.alpha));
                    vertices.add(new Vertex(second, 0, 1, segment.alpha));
                    vertices.add(new Vertex(fourth, 1, 1, segment.alpha));
                    vertices.add(new Vertex(third, 1, 0, segment.alpha));
                    lastDirection = direction;
                }
            }
            return List.copyOf(vertices);
        }
    }
}
