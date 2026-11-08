package cn.academy.event;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.event.ability.CalcEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Original Brain Course / Advanced Brain Course / Mind Course calculation handlers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class GenericPassiveEvents {
    private static boolean learned(Player player, String skill) {
        AbilityState state = AbilityState.load(player);
        return state.learned().contains(state.category() + "." + skill);
    }

    @SubscribeEvent
    public static void maxCp(CalcEvent.MaxCP event) {
        if (learned(event.player, "brain_course")) event.value += 1000;
        if (learned(event.player, "brain_course_advanced")) event.value += 1500;
    }

    @SubscribeEvent
    public static void maxOverload(CalcEvent.MaxOverload event) {
        if (learned(event.player, "brain_course_advanced")) event.value += 100;
    }

    @SubscribeEvent
    public static void cpRecovery(CalcEvent.CPRecoverSpeed event) {
        if (learned(event.player, "mind_course")) event.value *= 1.2f;
    }

    private GenericPassiveEvents() {}
}
