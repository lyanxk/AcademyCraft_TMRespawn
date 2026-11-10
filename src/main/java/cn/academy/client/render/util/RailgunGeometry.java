package cn.academy.client.render.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Original RendererRayGlow/RendererRayCylinder geometry, independent of GL and the client world. */
public final class RailgunGeometry {
    public record Vertex(Vec3 position, double u, double v) {}
    public record Glow(List<Vertex> head, List<Vertex> body, List<Vertex> tail) {}
    public static final List<Vec3> HEAD, CYLINDER;
    static {
        final int div = 12;
        double[] sins = new double[div], cosines = new double[div];
        double angle = 0;
        for (int i = 0; i < div; i++, angle += Math.PI * 2 / div) {
            sins[i] = Math.sin(angle); cosines[i] = Math.cos(angle);
        }
        List<Vec3> points = new ArrayList<>(), head = new ArrayList<>(), cylinder = new ArrayList<>();
        for (int i = 0; i <= 4; i++) for (int j = 0; j < div; j++) {
            double x = i * .25, radius = Math.sqrt(x);
            points.add(new Vec3(x, radius * sins[j], radius * cosines[j]));
        }
        for (int i = 0; i < 4; i++) {
            int offset = div * i;
            for (int j = 0; j < div - 1; j++, offset++)
                face(head, points, offset, offset + div, offset + div + 1, offset + 1);
            face(head, points, div * i, offset, offset + div, div * i + div);
        }
        points.clear();
        for (int j = 0; j < div; j++) {
            points.add(new Vec3(0, sins[j], cosines[j])); points.add(new Vec3(1, sins[j], cosines[j]));
        }
        for (int i = 0; i < div - 1; i++) face(cylinder, points, i * 2, i * 2 + 1, i * 2 + 3, i * 2 + 2);
        face(cylinder, points, 22, 23, 1, 0);
        HEAD = List.copyOf(head); CYLINDER = List.copyOf(cylinder);
    }
    private static void face(List<Vec3> out, List<Vec3> points, int a, int b, int c, int d) {
        out.add(points.get(a)); out.add(points.get(b)); out.add(points.get(c)); out.add(points.get(d));
    }
    public static Vec3 direction(float yaw, float pitch) {
        float a = Mth.cos(-yaw * .017453292F - (float) Math.PI);
        float b = Mth.sin(-yaw * .017453292F - (float) Math.PI);
        float c = -Mth.cos(-pitch * .017453292F), d = Mth.sin(-pitch * .017453292F);
        return new Vec3(b * c, d, a * c);
    }
    public static Vec3 normalize(Vec3 vector) {
        double length = (float) Math.sqrt(vector.lengthSqr());
        return length < 1e-4 ? Vec3.ZERO : new Vec3(vector.x / length, vector.y / length, vector.z / length);
    }
    public static Vec3 hand(boolean firstPerson) {
        return firstPerson ? new Vec3(-.05, -.25, .2) : new Vec3(.15, -.8, .23);
    }
    public static Vec3 rotatedHand(float yaw, boolean firstPerson) {
        return hand(firstPerson).yRot((float) ((270 - yaw) / 180 * Math.PI));
    }
    public static Glow glow(Vec3 position, Vec3 camera, float yaw, float pitch, boolean firstPerson,
                            RayAnimation.Frame frame) {
        return glow(position,camera,yaw,pitch,firstPerson,frame,1.1,-.3,.3);
    }
    public static Glow glow(Vec3 position,Vec3 camera,float yaw,float pitch,boolean firstPerson,
                            RayAnimation.Frame frame,double baseWidth,double startFix,double endFix) {
        return glow(position,camera,yaw,pitch,firstPerson,frame,baseWidth,startFix,endFix,position);
    }
    public static Glow glow(Vec3 position,Vec3 camera,float yaw,float pitch,boolean firstPerson,
                            RayAnimation.Frame frame,double baseWidth,double startFix,double endFix,Vec3 currentPosition) {
        return glow(position,camera,yaw,pitch,firstPerson,true,frame,baseWidth,startFix,endFix,currentPosition);
    }
    public static Glow freeGlow(Vec3 position,Vec3 renderOrigin,float yaw,float pitch,RayAnimation.Frame frame,double width) {
        return glow(position,renderOrigin,yaw,pitch,false,false,frame,width,0,0,position);
    }
    private static Glow glow(Vec3 position,Vec3 camera,float yaw,float pitch,boolean firstPerson,boolean viewOptimize,
                             RayAnimation.Frame frame,double baseWidth,double startFix,double endFix,Vec3 currentPosition) {
        Vec3 direction = direction(yaw, pitch);
        // Original uses new Vec3d(x,y,y) here: retain the repeated Y, even though it looks like a typo.
        Vec3 relative = position.subtract(camera);
        Vec3 relativePosition = currentPosition.subtract(new Vec3(relative.x, relative.y, relative.y));
        Vec3 up = normalize(firstPerson ? new Vec3(0, 1, -.5) : relativePosition.cross(direction));
        Vec3 start = viewOptimize?rotatedHand(yaw, firstPerson):Vec3.ZERO, end = direction.scale(frame.length());
        Vec3 look = normalize(end.subtract(start));
        start = start.add(look.scale(startFix)); end = end.add(look.scale(endFix));
        // End-cap lengths use the base width, not the animated width.
        Vec3 mid1 = start.add(look.scale(baseWidth)), mid2 = end.add(look.scale(-baseWidth));
        double width = baseWidth * frame.width();
        return new Glow(board(start, mid1, up, width), board(mid1, mid2, up, width), board(mid2, end, up, width));
    }
    private static List<Vertex> board(Vec3 start, Vec3 end, Vec3 up, double width) {
        Vec3 off = up.scale(width / 2);
        return List.of(new Vertex(start.add(off), 0, 1), new Vertex(start.subtract(off), 0, 0),
                new Vertex(end.subtract(off), 1, 0), new Vertex(end.add(off), 1, 1));
    }
    public static Matrix4f cylinderTransform(float yaw, float pitch, boolean firstPerson, double length) {
        // Unlike the glow renderer, BaseSimple uses MathUtils.toRadians(float): float PI and multiply first.
        Vec3 start = hand(firstPerson).yRot((270 - yaw) * (float) Math.PI / 180);
        return cylinderTransform(yaw,pitch,length,start);
    }
    public static Matrix4f freeCylinderTransform(float yaw,float pitch,double length) {
        return cylinderTransform(yaw,pitch,length,Vec3.ZERO);
    }
    private static Matrix4f cylinderTransform(float yaw,float pitch,double length,Vec3 start) {
        Vec3 delta = direction(yaw, pitch).scale(length).subtract(start);
        double newPitch = Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        double newYaw = Math.atan2(delta.x, delta.z);
        // Float cosFromSin loses precision near +/-90 degrees; evaluate original double rotations first.
        return new Matrix4f(new org.joml.Matrix4d().translate(start.x, start.y, start.z)
                .rotateY(-Math.PI / 2 + newYaw).rotateZ(newPitch));
    }
    private RailgunGeometry() {}
}
