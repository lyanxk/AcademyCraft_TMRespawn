package cn.academy.client.render.util;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Randomly gens a point on face of a cube with size(w, h, l). (placed at 0, 0, 0)
 * Preserves the original uniform choice of six faces (not surface-area weighting).
 * @author WeathFolD
 */
public class CubePointFactory {
    
    /*
     * Face id: 
     * 0 -Y
     * 1 +Y
     * 2 -Z
     * 3 +Z
     * 4 -X
     * 5 +X
     */
    private double w, h, l;
    private final Random RNG;
    boolean centered;

    public CubePointFactory(double _w, double _h, double _l) {
        this(_w, _h, _l, new Random());
    }

    public CubePointFactory(double _w, double _h, double _l, Random random) {
        RNG = random;
        setSize(_w, _h, _l);
    }
    
    public CubePointFactory setCentered(boolean b) {
        centered = b;
        return this;
    }
    
    public void setSize(double _w, double _h, double _l) {
        w = _w;
        h = _h;
        l = _l;
    }
    
    private double getArea(int f) {
        if(f == 0 || f == 1) {
            return w * l;
        }
        if(f == 2 || f == 3) {
            return h * l;
        }
        return h * w;
    }
    
    private int randFace() {
        return RNG.nextInt(6);
    }

    public Vec3 next() {
        int face = randFace();
        double a, b;
        double xOffset = 0, zOffset = 0;
        if(centered) {
            xOffset = -w * 0.5;
            zOffset = -l * 0.5;
        }
        switch(face) {
        case 0:
        case 1:
            a = RNG.nextDouble() * w;
            b = RNG.nextDouble() * l;
            return new Vec3(a + xOffset, face == 0 ? 0 : h, b + zOffset);
        case 2:
        case 3:
            a = RNG.nextDouble() * h;
            b = RNG.nextDouble() * w;
            return new Vec3(b + xOffset, a, (face == 2 ? 0 : l) + zOffset);
        case 4:
        case 5:
            a = RNG.nextDouble() * h;
            b = RNG.nextDouble() * l;
            return new Vec3((face == 4 ? 0 : w) + xOffset, a, b + zOffset);
        }
        return null; //Not supposed to happen
    }

}
