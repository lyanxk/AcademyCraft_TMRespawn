package cn.academy.client;

import cn.academy.ability.AbilityState;

/** Local wheel selection; skill channels remain stable on both sides of the connection. */
public final class SkillSelection {
    private static String category = "";
    private static int selected = -1;
    private static boolean waitForRelease;

    public static int selected(AbilityState state) {
        var available = state.controllableSlots();
        if (!category.equals(state.category()) || !available.contains(selected)) {
            category = state.category();
            selected = available.isEmpty() ? -1 : available.getFirst();
        }
        return selected;
    }

    public static void scroll(AbilityState state, int direction) {
        int current = selected(state);
        var available = state.controllableSlots();
        if (available.isEmpty()) return;
        selected = available.get(Math.floorMod(available.indexOf(current) + direction, available.size()));
        waitForRelease = ACKeyMappings.physicalDown(ACKeyMappings.CAST_SKILL);
    }

    public static boolean canPress(boolean down) {
        if (!down) waitForRelease = false;
        return !waitForRelease;
    }

    public static void reset() { category = ""; selected = -1; waitForRelease = false; }
    private SkillSelection() {}
}
