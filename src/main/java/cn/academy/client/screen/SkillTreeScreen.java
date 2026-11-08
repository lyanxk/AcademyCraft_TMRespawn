package cn.academy.client.screen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Original SkillTreeAppUI uses the same Common page, with no developer attached. */
public final class SkillTreeScreen extends DeveloperScreen {
    static record Point(int x, int y) {}
    static final Map<String, Point> POSITIONS = positions();

    public SkillTreeScreen() { super(BlockPos.ZERO, false, true); }

    private static Map<String, Point> positions() {
        Map<String, Point> p = new HashMap<>();
        p.put("arc_gen", new Point(24, 46)); p.put("charging", new Point(55, 18));
        p.put("body_intensify", new Point(97, 15)); p.put("mine_detect", new Point(225, 12));
        p.put("mag_movement", new Point(137, 35)); p.put("thunder_bolt", new Point(86, 67));
        p.put("railgun", new Point(164, 59)); p.put("thunder_clap", new Point(204, 80));
        p.put("mag_manip", new Point(204, 33));

        p.put("electron_bomb", new Point(15, 45)); p.put("rad_intensify", new Point(35, 75));
        p.put("scatter_bomb", new Point(70, 50)); p.put("light_shield", new Point(55, 15));
        p.put("meltdowner", new Point(115, 40)); p.put("mine_ray_basic", new Point(140, 70));
        p.put("ray_barrage", new Point(140, 10)); p.put("jet_engine", new Point(170, 32));
        p.put("mine_ray_expert", new Point(172, 70)); p.put("mine_ray_luck", new Point(205, 82));
        p.put("electron_missile", new Point(210, 35));

        p.put("threatening_teleport", new Point(14, 42)); p.put("dim_folding_theorem", new Point(50, 75));
        p.put("penetrate_teleport", new Point(60, 46)); p.put("mark_teleport", new Point(70, 16));
        p.put("flesh_ripping", new Point(130, 12)); p.put("location_teleport", new Point(118, 50));
        p.put("shift_tp", new Point(175, 47)); p.put("space_fluct", new Point(160, 80));
        p.put("flashing", new Point(220, 20));

        p.put("dir_shock", new Point(16, 45)); p.put("ground_shock", new Point(64, 85));
        p.put("vec_accel", new Point(76, 40)); p.put("storm_wing", new Point(130, 20));
        p.put("dir_blast", new Point(136, 80)); p.put("vec_deviation", new Point(145, 53));
        p.put("plasma_cannon", new Point(175, 14)); p.put("vec_reflection", new Point(210, 50));
        p.put("blood_retro", new Point(204, 83));

        p.put("brain_course", new Point(30, 110)); p.put("brain_course_advanced", new Point(115, 110));
        p.put("mind_course", new Point(205, 110));
        return Map.copyOf(p);
    }

}
