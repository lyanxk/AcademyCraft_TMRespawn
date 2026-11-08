package cn.academy.client.render.util;

import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** RenderDummy followed by RailgunHandEffect, including its use of the local viewer's pitch. */
public final class RailgunHandPose {
    public static int frame(double elapsedSeconds) {
        return elapsedSeconds >= .04 * 40 ? -1 : (int) (elapsedSeconds / .04);
    }
    public static Matrix4f local(float yaw, float ownerPitch, float viewerPitch, float viewerEyeHeight, boolean firstPerson) {
        var matrix = new Matrix4d().rotateY(Math.toRadians(180 - yaw));
        if (firstPerson) {
            matrix.rotateX(Math.toRadians(-ownerPitch));
            double pitch = Math.toRadians(viewerPitch);
            matrix.translate(0, Math.cos(pitch) * viewerEyeHeight, Math.sin(pitch) * viewerEyeHeight)
                    .translate(.26, -.15, -.24).scale((double) .4f, (double) .4f, 1);
        } else {
            matrix.translate(.15, -.8, .23).translate(0, 1.8, -1).rotateX(Math.toRadians(-viewerPitch));
        }
        return new Matrix4f(matrix);
    }
    private RailgunHandPose() {}
}
