package cn.academy.event.ability;

import cn.academy.ability.AbilitySkill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Mutable calculation events, following the original AcademyCraft event contract. */
public class CalcEvent<T> extends Event {
    public T value;

    public CalcEvent(T initial) { value = initial; }

    public static <T> T calc(CalcEvent<T> event) {
        NeoForge.EVENT_BUS.post(event);
        return event.value;
    }

    public static class PlayerCalcEvent<T> extends CalcEvent<T> {
        public final Player player;

        public PlayerCalcEvent(Player player, T initial) {
            super(initial);
            this.player = player;
        }
    }

    public static class SkillAttack extends PlayerCalcEvent<Float> {
        public final AbilitySkill skill;
        public final Entity target;

        public SkillAttack(Player player, AbilitySkill skill, Entity target, float initial) {
            super(player, initial);
            this.skill = skill;
            this.target = target;
        }
    }

    public static class MaxCP extends PlayerCalcEvent<Float> {
        public MaxCP(Player player, float initial) { super(player, initial); }
    }

    public static class MaxOverload extends PlayerCalcEvent<Float> {
        public MaxOverload(Player player, float initial) { super(player, initial); }
    }

    public static class CPRecoverSpeed extends PlayerCalcEvent<Float> {
        public CPRecoverSpeed(Player player, float initial) { super(player, initial); }
    }

    public static class OverloadRecoverSpeed extends PlayerCalcEvent<Float> {
        public OverloadRecoverSpeed(Player player, float initial) { super(player, initial); }
    }

    public static class SkillPerform extends AbilityEvent {
        public float cp;
        public float overload;

        public SkillPerform(Player player, float overload, float cp) {
            super(player);
            this.overload = overload;
            this.cp = cp;
        }
    }
}
