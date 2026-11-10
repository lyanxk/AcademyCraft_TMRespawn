package cn.academy.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** GuiTutorial's ViewRenderEvent projection, separate from ordinary 2D recipe widgets. */
public final class TutorialPreviewTransform {
    public static Matrix4f projection(float width, float height, float displayAspect, float frameScale,
                                       float areaScreenX, float areaScreenY) {
        float scale = 366f / width * frameScale;
        return new Matrix4f().translation(-1 + 2 * (frameScale + areaScreenX) / width,
                        1 - 2 * (frameScale + areaScreenY) / height, 0)
                .scale(scale, -scale * displayAspect, -.5f)
                .perspective((float) Math.toRadians(50), 1, 1, 100);
    }
    public static Matrix4f model() {
        var axis = new Vector3f(1, 0, .1f).normalize();
        return new Matrix4f().translation(0, 0, -4).translate(.55f, .55f, .5f)
                .scale(.75f, -.75f, .75f).rotate((float) Math.toRadians(-20), axis);
    }
    public static Matrix4f block(double seconds) {
        return model().translate(.15f, .1f, -1)
                .rotateY((float) Math.toRadians((seconds / 80) % 360))
                .scale(.8f).translate(-.5f, -.5f, -.5f);
    }
    private TutorialPreviewTransform() {}
}
