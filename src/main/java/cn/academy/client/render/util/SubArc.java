package cn.academy.client.render.util;

import net.minecraft.world.phys.Vec3;
import java.util.Random;
import org.joml.Matrix4f;

/** Original fragmented-arc lifecycle; death is removed by the owner on the following tick. */
public final class SubArc {
    public final Vec3 position;
    public final double rotX, rotY, rotZ;
    public int pattern, ticks;
    public boolean shown, dead;
    public double frameRate = 1, switchRate = 1;
    public int life = 30;
    private final int templateCount;
    public SubArc(Vec3 position, int count, Random random, Random utilityRandom) {
        this.position = position; templateCount = count; pattern = random.nextInt(count);
        rotX = utilityRandom.nextDouble() * 360;
        rotY = utilityRandom.nextDouble() * 360;
        rotZ = utilityRandom.nextDouble() * 360;
    }
    public void tick(Random random) {
        if (random.nextDouble() < .5 * frameRate) pattern = random.nextInt(templateCount);
        if (random.nextDouble() < .9) ticks++;
        if (ticks == life) dead = true;
        if (shown) { if (random.nextDouble() < .4 * switchRate) shown = false; }
        else if (random.nextDouble() < .3 * switchRate) shown = true;
    }
    public Matrix4f transform(double templateLength) {
        return new Matrix4f().translate((float) position.x, (float) position.y, (float) position.z)
                .rotateZ((float) Math.toRadians(rotZ)).rotateY((float) Math.toRadians(rotY))
                .rotateX((float) Math.toRadians(rotX)).scale(.3f).translate((float) (-templateLength / 2), 0, 0);
    }
}
