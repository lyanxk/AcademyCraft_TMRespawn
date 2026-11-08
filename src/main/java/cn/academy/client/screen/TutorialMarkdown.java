/*
 * Adapted from Lambda Innovation's MarkdownParser, GLMarkdownRenderer and Fragmentor.
 * Copyright (c) Lambda Innovation, 2013-2016. MIT license; see META-INF/LICENSE-LambdaLib2.txt.
 */
package cn.academy.client.screen;

import cn.academy.ability.AbilityState;
import cn.academy.client.ACKeyMappings;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACKeyHintHud;
import cn.academy.client.render.ACLegacyFont;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Original AC Markdown dialect and pen layout, rendered through modern GUI buffers. */
final class TutorialMarkdown {
    private static final Pattern IMAGE = Pattern.compile("(.*)!\\[([^\\[\\]]*)\\]\\(([^\\(\\)]+)\\)(.*)");
    private static final Pattern TAG = Pattern.compile("(.*)!\\[([^\\[\\]]+)\\](.*)");
    private static final Pattern STAR = Pattern.compile("(.*)\\*\\*(.*)");
    private static final Pattern UNDER = Pattern.compile("(.*)__(.*)");
    private static final Pattern QUOTED = Pattern.compile(" *([^=]+)= *\"([^\"]+)\"(.*)");
    private static final Pattern PROPERTY = Pattern.compile(" *([^=]+)= *([^ =]+)(.*)");
    private static final String PUNCTUATION = ",.<>?;:'\"[]{}【】：；“‘《》，。？";
    private static final int LIST = 1, EMPHASIS = 2, STRONG = 4, REFERENCE = 8;
    private record Attributes(int header, int flags) { Attributes with(int flag) { return new Attributes(header, flags | flag); } }
    private sealed interface Instruction permits Span, Tag { }
    private record Span(String text, Attributes attributes) implements Instruction { }
    private record Tag(String name, Map<String, String> attributes) implements Instruction { }
    private interface Draw { void draw(GuiGraphics gui); }
    private static final class TextDraw implements Draw {
        final String text;
        final float x, size;
        float y;
        final boolean bold, italic;
        final int color;
        TextDraw(String text, float x, float y, float size, boolean bold, boolean italic, int color) {
            this.text = text; this.x = x; this.y = y; this.size = size;
            this.bold = bold; this.italic = italic; this.color = color;
        }
        @Override public void draw(GuiGraphics gui) {
            ACLegacyFont.draw(gui, Component.literal(text), x, y, size, color, ACLegacyFont.LEFT, 0, bold, italic);
        }
    }
    private final List<Draw> draws = new ArrayList<>();
    private final Minecraft minecraft;
    private final float widthLimit, fontSize;
    private float x, y, lastSize;
    private boolean lineBegin = true;

    TutorialMarkdown(Minecraft minecraft, String source, float widthLimit, float fontSize) {
        this.minecraft = minecraft; this.widthLimit = widthLimit; this.fontSize = fontSize;
        source.lines().forEach(this::parseLine);
    }
    float height() { return y + fontSize; }
    void draw(GuiGraphics gui, float left, float top) {
        gui.pose().pushPose(); gui.pose().translate(left, top, 0);
        for (Draw draw : draws) draw.draw(gui);
        gui.pose().popPose();
    }

    private void parseLine(String line) {
        int header = 0, flags = 0;
        boolean newline = false;
        if (line.startsWith("#")) {
            int count = 0;
            while (count < line.length() && line.charAt(count) == '#') count++;
            header = Math.min(6, count);
            line = line.substring(count).replaceFirst("^ *", "");
            newline = true;
        } else if (line.startsWith("* ")) { flags = LIST; line = line.substring(2); newline = true; }
        else if (line.startsWith("> ")) { flags = REFERENCE; line = line.substring(2); }
        List<Instruction> instructions = new ArrayList<>();
        parseSpan(line, new Attributes(header, flags), instructions);
        if (instructions.isEmpty()) newline(false);
        else for (Instruction instruction : instructions) {
            if (instruction instanceof Span span) text(span.text, span.attributes);
            else if (instruction instanceof Tag tag) tag(tag.name, tag.attributes);
        }
        if (newline) newline(false);
    }
    private static void parseSpan(String line, Attributes attributes, List<Instruction> out) {
        var image = IMAGE.matcher(line);
        var tag = TAG.matcher(line);
        if (image.matches()) {
            parseSpan(image.group(1), attributes, out);
            out.add(new Tag("img", Map.of("hover", image.group(2), "src", image.group(3))));
            parseSpan(image.group(4), attributes, out);
        } else if (tag.matches()) {
            parseSpan(tag.group(1), attributes, out);
            String content = tag.group(2);
            int space = content.indexOf(' ');
            int end = space < 0 ? content.length() : space;
            out.add(new Tag(content.substring(0, end), properties(content.substring(end))));
            parseSpan(tag.group(3), attributes, out);
        } else if (!styledSpan(line, attributes, out, STAR, EMPHASIS)
                && !styledSpan(line, attributes, out, UNDER, STRONG) && !line.isEmpty()) {
            out.add(new Span(line, attributes));
        }
    }
    private static boolean styledSpan(String line, Attributes attributes, List<Instruction> out, Pattern pattern, int flag) {
        var last = pattern.matcher(line);
        if (!last.matches()) return false;
        var first = pattern.matcher(last.group(1));
        if (!first.matches()) return false;
        parseSpan(first.group(1), attributes, out);
        out.add(new Span(first.group(2), attributes.with(flag)));
        parseSpan(last.group(2), attributes, out);
        return true;
    }
    private static Map<String, String> properties(String source) {
        Map<String, String> result = new HashMap<>();
        while (true) {
            var quoted = QUOTED.matcher(source);
            var plain = PROPERTY.matcher(source);
            var matcher = quoted.matches() ? quoted : plain.matches() ? plain : null;
            if (matcher == null) return result;
            result.put(matcher.group(1).trim(), matcher.group(2).trim());
            source = matcher.group(3);
        }
    }

    private void text(String text, Attributes attributes) {
        float size = fontSize;
        boolean bold = false, italic = false;
        if (attributes.header > 0) {
            // Original indexes [1.8,1.6,1.4,1.2,1.0] by the 1-based header level.
            // Its #5/#6 overflow is bounded here; built-in pages only use #1 and #2.
            size *= new float[]{1.8f, 1.6f, 1.4f, 1.2f, 1}[Math.min(4, attributes.header)];
            bold = attributes.header <= 3;
        }
        if ((attributes.flags & EMPHASIS) != 0) { italic = true; bold = false; }
        if ((attributes.flags & STRONG) != 0) { bold = true; italic = false; }
        boolean reference = (attributes.flags & REFERENCE) != 0;
        if (reference && x == 0) x = size;
        List<String> lines = fragment(text, x, size, bold, italic);
        if ((attributes.flags & LIST) != 0 && lineBegin) {
            float dot = size * .2f;
            x = size * 1.2f;
            float dotX = x, dotY = y + size / 2 - dot / 2;
            draws.add(gui -> rect(gui, dotX, dotY, dot, dot));
            x += dot * 2;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float width = ACLegacyFont.advance(line, size, bold, italic);
            if (i != 0 || width + x > widthLimit * 1.2f) newline(true);
            lastSize = size;
            draws.add(new TextDraw(line, x, y, size, bold, italic, reference ? 0xFFE1C385 : 0xFFFFFFFF));
            x += width;
        }
    }
    private void newline(boolean continuation) {
        x = 0;
        y += lastSize + (continuation ? 0 : 4);
        if (!continuation) lastSize = 0;
        lineBegin = !continuation;
    }

    private void tag(String name, Map<String, String> attributes) {
        if (name.equals("img")) image(attributes);
        if (name.equals("key")) {
            String id = attributes.getOrDefault("id", "");
            KeyMapping mapping = switch (id) {
                case "ability_activation" -> ACKeyMappings.ACTIVATE;
                case "open_data_terminal" -> ACKeyMappings.TERMINAL;
                case "ability_0", "ability_1", "ability_2", "ability_3" -> ACKeyMappings.CAST_SKILL;
                default -> null;
            };
            String label = mapping == null ? "???" : mapping.getKey().getType() == InputConstants.Type.MOUSE
                    ? "BUTTON" + mapping.getKey().getValue() : ACKeyHintHud.keyLabel(mapping);
            text(label, new Attributes(0, REFERENCE));
        }
        if (name.equals("misakaname") && minecraft.player != null) {
            String nameText = Component.translatable("ac.tutorial.misaka", AbilityState.load(minecraft.player).misakaId()).getString();
            text(nameText, new Attributes(0, STRONG));
        }
    }
    private void image(Map<String, String> attributes) {
        ResourceLocation texture = ResourceLocation.tryParse(attributes.getOrDefault("src", ""));
        if (texture == null) return;
        float scale = Float.parseFloat(attributes.getOrDefault("scale", "1"));
        float width, height;
        if (attributes.containsKey("width") && attributes.containsKey("height")) {
            width = Float.parseFloat(attributes.get("width")) * scale;
            height = Float.parseFloat(attributes.get("height")) * scale;
        } else {
            try (var stream = minecraft.getResourceManager().open(texture); var image = NativeImage.read(stream)) {
                width = image.getWidth() * scale; height = image.getHeight() * scale;
            } catch (java.io.IOException exception) { width = height = 16 * scale; }
        }
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return;
        if (width + x > widthLimit) { float ratio = (widthLimit - x) / width; width *= ratio; height *= ratio; }
        float imageX = x, imageY = height >= lastSize ? y : y + lastSize - height;
        float imageWidth = width, imageHeight = height;
        draws.add(gui -> ACGuiTextures.blit(gui, texture, imageX, imageY, imageWidth, imageHeight, 0, 0, 1, 1, 1, 1));
        float nextY = y + Math.max(0, height - fontSize);
        for (int i = draws.size() - 2; i >= 0 && draws.get(i) instanceof TextDraw; i--) {
            TextDraw draw = (TextDraw) draws.get(i);
            if (draw.y == y) draw.y = nextY;
        }
        x += width; y = nextY; lastSize = fontSize;
    }

    private enum Token { WORD, SPACE, CJKV, PUNCT }
    private static Token type(char value) {
        return PUNCTUATION.indexOf(value) >= 0 ? Token.PUNCT : Character.isWhitespace(value) ? Token.SPACE
                : Character.isIdeographic(value) ? Token.CJKV : Token.WORD;
    }
    private List<String> fragment(String source, double localX, float size, boolean bold, boolean italic) {
        List<String> result = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < source.length()) {
            int start = index;
            Token token = type(source.charAt(index++));
            while (index < source.length()) {
                Token next = type(source.charAt(index));
                if (token != Token.CJKV && token != next && next != Token.PUNCT) break;
                index++;
            }
            String content = source.substring(start, index);
            double length = ACLegacyFont.advance(content, size, bold, italic);
            if (localX + length > widthLimit) {
                if (token == Token.WORD) {
                    if (!builder.isEmpty()) result.add(builder.toString());
                    builder.setLength(0); builder.append(content); localX = length;
                } else {
                    while (!content.isEmpty()) {
                        double accumulated = 0;
                        int count = 0;
                        for (; count < content.length() && localX + accumulated <= widthLimit; count++)
                            accumulated += ACLegacyFont.advance(content.substring(count, count + 1), size, bold, italic);
                        if (count < content.length() && PUNCTUATION.indexOf(content.charAt(count)) >= 0) count++;
                        builder.append(content, 0, count); result.add(builder.toString());
                        builder.setLength(0); localX = 0; content = content.substring(count);
                    }
                }
            } else { builder.append(content); localX += length; }
        }
        if (!builder.isEmpty()) result.add(builder.toString());
        return result;
    }
    private static void rect(GuiGraphics gui, float x, float y, float width, float height) {
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(width, height, 1);
        gui.fill(0, 0, 1, 1, 0xFFFFFFFF); gui.pose().popPose();
    }
}
