package cn.academy.registry;

import cn.academy.AcademyCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ACSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, AcademyCraft.MOD_ID);
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> ALL = new LinkedHashMap<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_DENY = sound("ability.deny");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_ARC_WEAK = sound("em.arc_weak");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_ARC_STRONG = sound("em.arc_strong");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_MINEDETECT = sound("em.minedetect");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_RAILGUN = sound("em.railgun");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_MOVE_LOOP = sound("em.move_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_CHARGE_LOOP = sound("em.charge_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_INTENSIFY = sound("em.intensify_activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> EM_MAG_MANIP = sound("em.mag_manip");
    public static final DeferredHolder<SoundEvent, SoundEvent> MD_BALL = sound("md.ballshoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> MD_RAY = sound("md.ray_small");
    public static final DeferredHolder<SoundEvent, SoundEvent> MD_SHIELD = sound("md.shield_startup");
    public static final DeferredHolder<SoundEvent, SoundEvent> MD_MELTDOWNER = sound("md.meltdowner");
    public static final DeferredHolder<SoundEvent, SoundEvent> TP = sound("tp.tp");
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_PRE = sound("tp.tp_pre");
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_GUTS = sound("tp.guts");
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_SHIFT = sound("tp.tp_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_FLASHING = sound("tp.tp_flashing");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_SHOCK = sound("vecmanip.directed_shock");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_GROUNDSHOCK = sound("vecmanip.groundshock");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_BLAST = sound("vecmanip.directed_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_ACCEL = sound("vecmanip.vec_accel");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_PLASMA = sound("vecmanip.plasma_cannon");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_STORM = sound("vecmanip.storm_wing");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_DEVIATION = sound("vecmanip.vec_deviation");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEC_REFLECTION = sound("vecmanip.vec_reflection");
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_FLIP = sound("entity.flipcoin");
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_SELECT = sound("terminal.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_CONFIRM = sound("terminal.confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> MEDIA_SISTERS_NOISE = sound("media.sisters_noise");
    public static final DeferredHolder<SoundEvent, SoundEvent> MEDIA_ONLY_MY_RAILGUN = sound("media.only_my_railgun");
    public static final DeferredHolder<SoundEvent, SoundEvent> MEDIA_LEVEL5_JUDGELIGHT = sound("media.level5_judgelight");

    // Register less frequently referenced loop/startup events too, so sounds.json never points at an absent registry key.
    static {
        for (String name : new String[]{
                "em.intensify_loop", "em.lf_loop", "md.shield_loop", "md.mine_loop",
                "md.mine_basic_startup", "md.mine_luck_startup", "md.mine_expert_startup",
                "md.simple_charge", "md.md_charge", "entity.silbarn_heavy", "entity.silbarn_light",
                "vecmanip.blood_retro", "vecmanip.plasma_cannon_t",
                "machine.imag_fusor_work", "machine.machine_work"
        }) {
            sound(name);
        }
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, name);
        DeferredHolder<SoundEvent, SoundEvent> holder = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
        ALL.put(name, holder);
        return holder;
    }

    public static SoundEvent get(String name) {
        DeferredHolder<SoundEvent, SoundEvent> holder = ALL.get(name);
        return holder == null ? ABILITY_DENY.get() : holder.get();
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }

    private ACSounds() {}
}
