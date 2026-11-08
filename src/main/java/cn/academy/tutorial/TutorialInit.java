package cn.academy.tutorial;

import cn.academy.registry.ACItems;
import cn.academy.terminal.AppRegistry;
import static cn.academy.tutorial.Conditions.itemObtained;
import static cn.academy.tutorial.ViewGroups.*;

/** Original TutorialInit order and conditions, plus the existing FE bridge support tutorial. */
public final class TutorialInit {
    private static boolean initialized;
    public static synchronized void bootstrap() {
        if (initialized) return;
        initialized = true;
        defnTut("welcome");
        defnTut("ores").addCondition(itemObtained(ACItems.CONSTRAINT_METAL))
                .addCondition(itemObtained(ACItems.IMAGSIL_ORE)).addCondition(itemObtained(ACItems.CRYSTAL_ORE))
                .addCondition(itemObtained(ACItems.RESO_ORE));
        defnTut("phase_generator").addCondition(itemObtained(ACItems.PHASE_GEN));
        defnTut("solar_generator").addCondition(itemObtained(ACItems.SOLAR_GEN));
        defnTut("wind_generator").addCondition(itemObtained(ACItems.WINDGEN_BASE))
                .addCondition(itemObtained(ACItems.WINDGEN_FAN)).addCondition(itemObtained(ACItems.WINDGEN_MAIN))
                .addCondition(itemObtained(ACItems.WINDGEN_PILLAR));
        defnTut("metal_former").addCondition(itemObtained(ACItems.METAL_FORMER));
        defnTut("imag_fusor").addCondition(itemObtained(ACItems.IMAG_FUSOR));
        var terminal = defnTut("terminal").addCondition(itemObtained(ACItems.TERMINAL_INSTALLER))
                .addPreview(recipes(ACItems.TERMINAL_INSTALLER));
        for (var app : AppRegistry.enumeration()) {
            if (!app.isPreInstalled()) {
                var installer = java.util.Objects.requireNonNull(ACItems.ALL.get("app_" + app.getName()),
                        "Missing installer for " + app.getName());
                terminal.addCondition(itemObtained(installer)).addPreview(recipes(installer));
            }
        }
        defnTut("ability_developer").addCondition(itemObtained(ACItems.DEVELOPER_PORTABLE))
                .addCondition(itemObtained(ACItems.DEV_NORMAL)).addCondition(itemObtained(ACItems.DEV_ADVANCED));
        defnTut("ability_basis");
        defnTut("misc");
        defnTut("develop_ability");
        defnTut("wireless_network");
        defnTut("energy_bridge").addCondition(itemObtained(ACItems.FE_INPUT)).addCondition(itemObtained(ACItems.FE_OUTPUT));
        TutorialRegistry.getTutorial("ores").addPreview(drawsBlock(ACItems.CONSTRAINT_METAL), drawsBlock(ACItems.IMAGSIL_ORE),
                drawsBlock(ACItems.CRYSTAL_ORE), drawsBlock(ACItems.RESO_ORE), displayIcon("items/phase_liquid_mat", 0, 0, 1, -1),
                recipes(ACItems.CONSTRAINT_PLATE), recipes(ACItems.IMAG_SILICON_INGOT), recipes(ACItems.WAFER), recipes(ACItems.IMAG_SILICON_PIECE));
        TutorialRegistry.getTutorial("phase_generator").addPreview(recipes(ACItems.PHASE_GEN));
        TutorialRegistry.getTutorial("solar_generator").addPreview(recipes(ACItems.SOLAR_GEN));
        TutorialRegistry.getTutorial("wind_generator").addPreview(recipes(ACItems.WINDGEN_BASE), recipes(ACItems.WINDGEN_PILLAR),
                recipes(ACItems.WINDGEN_MAIN), recipes(ACItems.WINDGEN_FAN));
        TutorialRegistry.getTutorial("metal_former").addPreview(recipes(ACItems.METAL_FORMER));
        TutorialRegistry.getTutorial("imag_fusor").addPreview(recipes(ACItems.IMAG_FUSOR));
        TutorialRegistry.getTutorial("ability_developer").addPreview(recipes(ACItems.DEVELOPER_PORTABLE), recipes(ACItems.DEV_NORMAL), recipes(ACItems.DEV_ADVANCED));
        TutorialRegistry.getTutorial("energy_bridge").addPreview(recipes(ACItems.FE_INPUT), recipes(ACItems.FE_OUTPUT));
    }
    public static ACTutorial defnTut(String id) { return TutorialRegistry.addTutorial(id); }
    private TutorialInit() {}
}
