package cn.academy.client.render;

import cn.academy.network.ThunderClapLightningPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;

/** 1.12 client weather-bolt lifetime, using the vanilla renderer but no modern tick side effects or duplicate sounds. */
public final class LegacyWeatherLightning extends LightningBolt {
    private static int nextId = -2000000;
    private int legacyLife = 2, legacyFlashes;
    private LegacyWeatherLightning(Level level) {
        super(EntityType.LIGHTNING_BOLT, level);
        legacyFlashes = random.nextInt(3) + 1;
        setVisualOnly(true);
    }
    @Override public void tick() {
        baseTick();
        legacyLife--;
        if (legacyLife < 0) {
            if (legacyFlashes == 0) { discard(); return; }
            if (legacyLife < -random.nextInt(10)) { legacyFlashes--; legacyLife = 1; }
        }
        // Old client bolt keeps its renderer seed across secondary flashes.
        if (legacyLife >= 0) level().setSkyFlashTime(2);
    }
    public static void drain(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        var queue = mc.player.getPersistentData().getList(ThunderClapLightningPayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var tag = queue.getCompound(i);
            var bolt = new LegacyWeatherLightning(mc.level);
            bolt.setId(nextId--); bolt.setPos(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
            mc.level.addEntity(bolt);
        }
        mc.player.getPersistentData().remove(ThunderClapLightningPayload.QUEUE);
    }
}
