package cn.academy.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.EnumMap;

/** ACHud.Node offsets are GUI pixels, independent of the widget's own scale. */
public final class ACHudConfig {
    public enum Element {
        CPBAR("cpbar", -12, 12, 964, 147, .2f, .2f, 1, 0),
        KEYHINT("keyhint", 0, 30, 140, 210, .23f, .46f, 1, .5f),
        NOTIFICATION("notification", 0, 15, 517, 170, .25f, .25f, 0, 0),
        MEDIA("media", -6, -6, 145, 36, 1, 1, 1, 1);

        public final String id;
        public final Position defaults;
        private final float width, height, scale, previewScale, alignX, alignY;

        Element(String id, double x, double y, float width, float height, float scale,
                float previewScale, float alignX, float alignY) {
            this.id = id;
            this.defaults = new Position(x, y);
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.previewScale = previewScale;
            this.alignX = alignX;
            this.alignY = alignY;
        }

        public Bounds bounds(int screenWidth, int screenHeight, Position position, boolean preview) {
            float factor = preview ? previewScale : scale;
            float w = width * factor, h = height * factor;
            return new Bounds((screenWidth - w) * alignX + (float) position.x,
                    (screenHeight - h) * alignY + (float) position.y, w, h, factor);
        }
    }

    public record Position(double x, double y) {
        public Position { check(x); check(y); }
    }
    public record Bounds(float x, float y, float width, float height, float scale) {}
    private record Values(ModConfigSpec.DoubleValue x, ModConfigSpec.DoubleValue y) {}
    private static final EnumMap<Element, Values> VALUES = new EnumMap<>(Element.class);
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HEADS_OR_TAILS;
    public static final ModConfigSpec.BooleanValue USE_MOUSE_WHEEL;
    public static final java.util.Map<String, ModConfigSpec.ConfigValue<String>> SKILL_KEYS = new java.util.LinkedHashMap<>();

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("generic");
        HEADS_OR_TAILS = builder.define("headsOrTails", false);
        USE_MOUSE_WHEEL = builder.define("useMouseWheel", false);
        builder.pop();
        builder.push("skillKeys");
        for (var category : cn.academy.ability.AbilityRegistry.categories())
            for (var skill : category.skills()) if (skill.controllable())
                SKILL_KEYS.put(skill.id(), builder.define(skill.id(), ""));
        builder.pop();
        builder.comment("Original CustomizeUI offsets in GUI pixels (-512 to 512).") .push("gui");
        for (Element element : Element.values()) {
            builder.push(element.id);
            VALUES.put(element, new Values(builder.defineInRange("x", element.defaults.x, -512, 512),
                    builder.defineInRange("y", element.defaults.y, -512, 512)));
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
    }

    public static Position position(Element element) {
        if (!SPEC.isLoaded()) return element.defaults;
        var values = VALUES.get(element);
        return new Position(values.x.get(), values.y.get());
    }

    public static void save(Element element, Position position) {
        var values = VALUES.get(element);
        values.x.set(position.x);
        values.y.set(position.y);
        SPEC.save();
    }

    public static double parseCoordinate(String input) {
        double value = Double.parseDouble(input);
        check(value);
        // CustomizeUI stored the accepted double as a float in ACHud.Node.
        return (float) value;
    }

    private static void check(double value) {
        if (!Double.isFinite(value) || value < -512 || value > 512)
            throw new NumberFormatException("HUD coordinate must be finite and within [-512, 512]");
    }

    private ACHudConfig() {}
}
