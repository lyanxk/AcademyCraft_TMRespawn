package cn.academy.client.sound;

import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.IdentityHashMap;
import java.util.Map;

/** Original TileEntitySound loop lifecycle, driven by each processor's work/blocking state. */
public final class ACMachineSounds {
    private static final Map<ACMachineBlockEntity, MachineLoop> PLAYING = new IdentityHashMap<>();

    public static void tick(ACMachineBlockEntity machine) {
        if (!machine.kind().isProcessor()) return;
        MachineLoop previous = PLAYING.get(machine);
        if (!machine.isWorkInProgress()) {
            if (previous != null) previous.halt();
        } else if (previous == null) {
            var loop = new MachineLoop(machine);
            PLAYING.put(machine, loop);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
    }

    public static void stopAll() {
        for (var loop : java.util.List.copyOf(PLAYING.values())) loop.halt();
    }

    private static final class MachineLoop extends AbstractTickableSoundInstance {
        private final ACMachineBlockEntity machine;
        private MachineLoop(ACMachineBlockEntity machine) {
            super(ACSounds.get(machine.kind() == MachineKind.IMAG_FUSOR ? "machine.imag_fusor_work" : "machine.machine_work"),
                    SoundSource.BLOCKS, RandomSource.create());
            this.machine = machine;
            volume = .6f;
            pitch = 1;
            looping = true;
            delay = 0;
            x = machine.getBlockPos().getX() + .5;
            y = machine.getBlockPos().getY() + .5;
            z = machine.getBlockPos().getZ() + .5;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (machine.isRemoved() || machine.getLevel() != Minecraft.getInstance().level || !machine.isWorkInProgress()) halt();
        }
        private void halt() {
            stop();
            PLAYING.remove(machine, this);
        }
    }
    private ACMachineSounds() {}
}
