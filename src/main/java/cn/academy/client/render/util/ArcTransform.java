package cn.academy.client.render.util;

import org.joml.Matrix4f;

/** EntityArc.Renderer and ViewOptimize.fix, in their original multiplication order. */
public final class ArcTransform {
    public static Matrix4f local(float yaw, float pitch, boolean firstPerson) {
        var matrix = new Matrix4f().rotateY((float) Math.toRadians(-yaw - 90))
                .rotateZ((float) Math.toRadians(-pitch));
        return firstPerson ? matrix.translate(-.05f, -.25f, .2f) : matrix.translate(.15f, -.8f, .23f);
    }
    private ArcTransform() {}
}
