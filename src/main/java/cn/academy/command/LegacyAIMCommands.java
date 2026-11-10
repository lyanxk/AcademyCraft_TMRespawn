package cn.academy.command;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.ACNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Self/target ability commands, available directly to permitted command sources. */
public final class LegacyAIMCommands {
    private static final List<String> HELP = List.of("help", "cat", "learn", "unlearn", "reset",
            "fullcp", "level", "exp", "cd_clear");
    private static final List<String> COMMANDS = List.of("help", "?", "cat", "learn", "unlearn",
            "reset", "fullcp", "level", "exp", "cd_clear", "status");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : List.of("aim", "aimp")) {
            boolean targeted = name.equals("aimp");
            dispatcher.register(Commands.literal(name)
                    .requires(source -> source.hasPermission(4) && (targeted || source.getEntity() instanceof ServerPlayer))
                    .executes(context -> execute(context.getSource(), targeted, ""))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                            .suggests((context, builder) -> suggest(context.getSource(), targeted, builder))
                            .executes(context -> execute(context.getSource(), targeted,
                                    StringArgumentType.getString(context, "arguments")))));
        }
    }

    private static CompletableFuture<Suggestions> suggest(CommandSourceStack source, boolean targeted,
                                                          SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        int tokenStart = input.length();
        while (tokenStart > 0 && !Character.isWhitespace(input.charAt(tokenStart - 1))) tokenStart--;
        String preceding = input.substring(0, tokenStart).strip();
        String[] args = preceding.isEmpty() ? new String[0] : preceding.split("\\s+");
        // Replace only the token at the cursor, preserving the command and earlier arguments.
        SuggestionsBuilder token = builder.createOffset(builder.getStart() + tokenStart);
        ServerPlayer player;
        if (targeted) {
            if (args.length == 0) {
                var names = new ArrayList<>(List.of(source.getServer().getPlayerNames()));
                names.add("help");
                names.add("?");
                return SharedSuggestionProvider.suggest(names, token);
            }
            player = source.getServer().getPlayerList().getPlayerByName(args[0]);
            if (player == null) return token.buildFuture();
            args = Arrays.copyOfRange(args, 1, args.length);
        } else {
            player = source.getEntity() instanceof ServerPlayer self ? self : null;
        }
        if (args.length == 0) {
            return SharedSuggestionProvider.suggest(COMMANDS, token);
        }
        String command = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (command.equals("cat"))
                return SharedSuggestionProvider.suggest(AbilityRegistry.categoryIds(), token);
            if (command.equals("level"))
                return SharedSuggestionProvider.suggest(List.of("1", "2", "3", "4", "5"), token);
            if (player != null && List.of("learn", "unlearn", "exp").contains(command)) {
                var category = AbilityRegistry.category(AbilityState.load(player).category());
                if (category != null) {
                    var skills = new ArrayList<String>();
                    if (!command.equals("unlearn")) skills.add("all");
                    for (int i = 0; i < category.skills().size(); i++) {
                        var skill = category.skills().get(i);
                        skills.add(skill.name());
                        if (command.equals("unlearn")) {
                            skills.add(skill.id());
                            skills.add(Integer.toString(i));
                        }
                    }
                    return SharedSuggestionProvider.suggest(skills, token);
                }
            }
        } else if (args.length == 2 && command.equals("exp") && player != null) {
            var state = AbilityState.load(player);
            if (state.hasCategory() && (args[1].equalsIgnoreCase("all")
                    || namedSkill(state, args[1].toLowerCase(Locale.ROOT)) != null))
                return SharedSuggestionProvider.suggest(List.of("0", "0.25", "0.5", "0.75", "1"), token);
        }
        return token.buildFuture();
    }

    private static int execute(CommandSourceStack source, boolean targeted, String input) {
        String prefix = "ac.command." + (targeted ? "aimp." : "aim.");
        String[] args = input.isBlank() ? new String[0] : input.toLowerCase(Locale.ROOT).strip().split("\\s+");
        ServerPlayer player;
        if (targeted) {
            if (args.length == 0) return message(source, prefix + "help");
            player = source.getServer().getPlayerList().getPlayerByName(args[0]);
            if (player == null) {
                if (args[0].equals("help") || args[0].equals("?")) return help(source, prefix);
                return message(source, "ac.command.noplayer");
            }
            args = Arrays.copyOfRange(args, 1, args.length);
        } else {
            if (!(source.getEntity() instanceof ServerPlayer self)) return 0;
            player = self;
        }
        if (args.length == 0) return message(source, prefix + "help");
        AbilityState state = AbilityState.load(player);
        String command = args[0];
        switch (command) {
            case "help", "?": return help(source, prefix);
            case "cat":
                if (args.length == 1) return message(source, prefix + "curcat", state.hasCategory()
                        ? AbilityRegistry.category(state.category()).displayName() : Component.translatable(prefix + "nonecat"));
                if (args.length != 2) return message(source, "ac.command.invalid");
                if (AbilityRegistry.category(args[1]) == null) return message(source, prefix + "nocat");
                state.setCategory(args[1]);
                break;
            case "reset": state.clearCategory(); break;
            case "level":
                if (args.length == 1) return literal(source, Integer.toString(state.level()));
                if (args.length != 2) return message(source, "ac.command.invalid");
                if (!state.hasCategory()) return message(source, prefix + "nonecathint");
                try {
                    int level = Integer.parseInt(args[1]);
                    if (level < 1 || level > 5) return message(source, prefix + "outofrange", 1, 5);
                    state.setLevel(level);
                } catch (NumberFormatException error) { return message(source, "ac.command.aim.invalidnum", args[1]); }
                break;
            case "learn", "unlearn", "exp":
                if (!state.hasCategory()) return message(source, prefix + "nonecathint");
                if (args.length < 2 || args.length > (command.equals("exp") ? 3 : 2))
                    return message(source, "ac.command.invalid");
                boolean all = !command.equals("unlearn") && args[1].equals("all");
                AbilitySkill skill = all ? null : command.equals("unlearn")
                        ? skill(state, args[1]) : namedSkill(state, args[1]);
                if (!all && skill == null) return message(source, prefix + "noskill");
                if (command.equals("learn")) {
                    if (all) state.learnAll();
                    else state.learn(skill);
                }
                else if (command.equals("unlearn")) state.unlearn(skill);
                else {
                    if (args.length == 2) {
                        if (all) return message(source, "ac.command.invalid");
                        return message(source, prefix + "curexp", skill.displayName(),
                                Math.round(state.experience(skill.id()) * 100));
                    }
                    try {
                        float exp = Float.parseFloat(args[2]);
                        if (!Float.isFinite(exp) || exp < 0 || exp > 1)
                            return message(source, prefix + "outofrange", 0, 1);
                        // AbilityData.setSkillExp ignores skills that have not been learned.
                        if (all) {
                            for (var entry : AbilityRegistry.category(state.category()).skills())
                                state.setExperience(entry, exp);
                        } else state.setExperience(skill, exp);
                    } catch (NumberFormatException error) { return message(source, "ac.command.aim.invalidnum", args[2]); }
                }
                sync(player, state);
                return command.equals("exp") ? message(source, "ac.command.successful") : 1;
            case "fullcp", "cd_clear":
                if (!state.hasCategory()) return message(source, prefix + "nonecathint");
                switch (command) {
                    case "fullcp" -> state.restoreCommandResources();
                    case "cd_clear" -> state.clearCooldowns();
                }
                break;
            case "status": return ACCommands.status(source, player); // Existing modern diagnostic alias.
            default: return message(source, prefix + "nocomm");
        }
        sync(player, state);
        return message(source, "ac.command.successful");
    }

    private static AbilitySkill skill(AbilityState state, String token) {
        var category = AbilityRegistry.category(state.category());
        try {
            int index = Integer.parseInt(token);
            return index >= 0 && index < category.skills().size() ? category.skills().get(index) : null;
        } catch (NumberFormatException ignored) {
            return category.skill(token.startsWith(state.category() + ".")
                    ? token.substring(state.category().length() + 1) : token);
        }
    }

    private static AbilitySkill namedSkill(AbilityState state, String token) {
        return AbilityRegistry.category(state.category()).skills().stream()
                .filter(skill -> skill.name().equals(token)).findFirst().orElse(null);
    }

    private static int help(CommandSourceStack source, String prefix) {
        for (String command : HELP) message(source, prefix + command);
        return 1;
    }
    private static void sync(ServerPlayer player, AbilityState state) { state.save(player); ACNetwork.sync(player, state); }
    private static int message(CommandSourceStack source, String key, Object... args) {
        send(source, Component.translatable(key, args)); return 1;
    }
    private static int literal(CommandSourceStack source, String text) { send(source, Component.literal(text)); return 1; }
    private static void send(CommandSourceStack source, Component message) { source.sendSuccess(() -> message, false); }
    private LegacyAIMCommands() {}
}
