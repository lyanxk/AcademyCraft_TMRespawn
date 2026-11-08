package cn.academy.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Sprite particle using the original AcademyCraft effect sheets. */
public final class ACLegacyParticle extends TextureSheetParticle {
    public enum Style {
        ARC(.35f, .78f, 1f, .22f, 12, 0),
        MELTDOWNER(.38f, 1f, .48f, .4f, 18, -.004f),
        TELEPORT(.76f, .86f, 1f, .32f, 20, 0),
        VECTOR(.48f, .76f, 1f, .5f, 22, -.002f);
        final float red, green, blue, size, gravity;
        final int lifetime;
        Style(float red, float green, float blue, float size, int lifetime, float gravity) {
            this.red = red; this.green = green; this.blue = blue; this.size = size;
            this.lifetime = lifetime; this.gravity = gravity;
        }
    }

    private final SpriteSet sprites;

    private ACLegacyParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             SpriteSet sprites, Style style) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.rCol = style.red; this.gCol = style.green; this.bCol = style.blue;
        this.alpha = .9f;
        this.quadSize = style.size * (.75f + random.nextFloat() * .5f);
        this.lifetime = style.lifetime + random.nextInt(Math.max(2, style.lifetime / 2));
        this.gravity = style.gravity;
        this.friction = .94f;
        this.roll = random.nextFloat() * ((float) Math.PI * 2);
        this.oRoll = roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
            alpha = Math.max(0, 1 - age / (float) lifetime);
            roll += .08f;
            quadSize *= .985f;
        }
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;
        public Provider(SpriteSet sprites, Style style) { this.sprites = sprites; this.style = style; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ACLegacyParticle(level, x, y, z, vx, vy, vz, sprites, style);
        }
    }
}
