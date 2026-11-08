package cn.academy.command;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.advancement.ACAdvancements;
import cn.academy.network.ACNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ACCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> academy = dispatcher.register(Commands.literal("academy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .then(playerArgument().executes(context -> status(context.getSource(), player(context)))))
                .then(Commands.literal("category")
                        .then(playerArgument()
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(AbilityRegistry.categoryIds(), builder))
                                        .executes(context -> category(context.getSource(), player(context),
                                                StringArgumentType.getString(context, "category"))))
                                .then(Commands.literal("clear").executes(context -> clearCategory(context.getSource(), player(context))))))
                .then(Commands.literal("level")
                        .then(playerArgument().then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(context -> level(context.getSource(), player(context),
                                        IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("learn")
                        .then(playerArgument()
                                .then(Commands.literal("all").executes(context -> learnAll(player(context))))
                                .then(skillArgument().executes(context -> learn(context.getSource(), player(context), skillName(context))))))
                .then(Commands.literal("unlearn")
                        .then(playerArgument().then(skillArgument().executes(context ->
                                unlearn(context.getSource(), player(context), skillName(context))))))
                .then(Commands.literal("experience")
                        .then(playerArgument().then(skillArgument()
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(context -> experience(context.getSource(), player(context), skillName(context),
                                                (float) DoubleArgumentType.getDouble(context, "value")))))))
                .then(Commands.literal("recover")
                        .then(playerArgument().executes(context -> recover(player(context)))))
                .then(Commands.literal("cooldowns")
                        .then(Commands.literal("clear").then(playerArgument().executes(context -> clearCooldowns(player(context))))))
                .then(Commands.literal("maxout")
                        .then(playerArgument().executes(context -> maxout(player(context)))))
                .then(Commands.literal("advancement")
                        .then(playerArgument().then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> grantAdvancement(context.getSource(), player(context),
                                        StringArgumentType.getString(context, "id")))))));

        LegacyAIMCommands.register(dispatcher);

        dispatcher.register(Commands.literal("acach").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Usage: /acach ACHIEVEMENT_NAME [PLAYER_NAME]"), false);
                    return 1;
                })
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ACAdvancements.TRIGGER_NAMES, builder))
                        .executes(context -> triggerAdvancement(context.getSource(), context.getSource().getPlayer(),
                                StringArgumentType.getString(context, "id")))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getServer().getPlayerNames(), builder))
                                .executes(context -> triggerAdvancement(context.getSource(),
                                        context.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(context, "player")),
                                        StringArgumentType.getString(context, "id"))))));
    }

    private static int triggerAdvancement(CommandSourceStack source, ServerPlayer player, String id) {
        Component result = player == null ? Component.translatable("ac.command.noplayer")
                : ACAdvancements.trigger(player, id) ? Component.translatable("ac.command.successful")
                : Component.literal("No such achievement found");
        source.sendSuccess(() -> result, false);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playerArgument() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> skillArgument() {
        return Commands.argument("skill", StringArgumentType.word()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(AbilityRegistry.categories().stream()
                        .flatMap(category -> category.skills().stream()).map(AbilitySkill::id), builder));
    }

    private static ServerPlayer player(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayer(context, "player");
    }

    private static String skillName(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "skill");
    }

    private static AbilitySkill resolveSkill(AbilityState state, String skillId) {
        return AbilityRegistry.skill(skillId.contains(".") ? skillId : state.category() + "." + skillId);
    }

    private static int listCategories(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(String.join(", ", AbilityRegistry.categoryIds())), false);
        return AbilityRegistry.categoryIds().size();
    }

    static int status(CommandSourceStack source, ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        source.sendSuccess(() -> Component.literal(player.getScoreboardName() + ": category=" + state.category()
                + ", level=" + state.level() + ", CP=" + Math.round(state.cp()) + "/" + Math.round(state.maxCp())
                + ", overload=" + Math.round(state.overload()) + "/" + Math.round(state.maxOverload())
                + ", active=" + state.active() + ", learned=" + state.learned().size()), false);
        return 1;
    }

    private static int category(CommandSourceStack source, ServerPlayer player, String category) {
        if (AbilityRegistry.category(category) == null) {
            source.sendFailure(Component.literal("Unknown AcademyCraft category: " + category));
            return 0;
        }
        AbilityState state = AbilityState.load(player);
        state.setCategory(category);
        saveSync(player, state);
        ACAdvancements.grantLegacyLevels(player, category, state.level());
        source.sendSuccess(() -> Component.literal("Set " + player.getScoreboardName() + " category to " + category), true);
        return 1;
    }

    private static int clearCategory(CommandSourceStack source, ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        state.clearCategory();
        saveSync(player, state);
        source.sendSuccess(() -> Component.literal("Cleared " + player.getScoreboardName() + " category"), true);
        return 1;
    }

    private static int level(CommandSourceStack source, ServerPlayer player, int level) {
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) {
            source.sendFailure(Component.literal("Player has no AcademyCraft category"));
            return 0;
        }
        state.setLevel(level);
        saveSync(player, state);
        ACAdvancements.grantLegacyLevels(player, state.category(), level);
        return 1;
    }

    private static int learnAll(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) return 0;
        state.learnAll();
        saveSync(player, state);
        return 1;
    }

    private static int learn(CommandSourceStack source, ServerPlayer player, String skillId) {
        AbilityState state = AbilityState.load(player);
        AbilitySkill skill = resolveSkill(state, skillId);
        if (skill == null || !skill.category().equals(state.category())) {
            source.sendFailure(Component.literal("Unknown skill for player's category: " + skillId));
            return 0;
        }
        state.learn(skill);
        saveSync(player, state);
        ACAdvancements.grantLegacySkillLearn(player, skill.category(), skill.name());
        return 1;
    }

    private static int unlearn(CommandSourceStack source, ServerPlayer player, String skillId) {
        AbilityState state = AbilityState.load(player);
        AbilitySkill skill = resolveSkill(state, skillId);
        if (!state.unlearn(skill)) {
            source.sendFailure(Component.literal("Skill was not learned: " + skillId));
            return 0;
        }
        saveSync(player, state);
        return 1;
    }

    private static int experience(CommandSourceStack source, ServerPlayer player, String skillId, float value) {
        AbilityState state = AbilityState.load(player);
        AbilitySkill skill = resolveSkill(state, skillId);
        if (!state.setExperience(skill, value)) {
            source.sendFailure(Component.literal("Skill was not learned: " + skillId));
            return 0;
        }
        saveSync(player, state);
        return 1;
    }

    private static int recover(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) return 0;
        state.recoverAll();
        saveSync(player, state);
        return 1;
    }

    private static int clearCooldowns(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        state.clearCooldowns();
        saveSync(player, state);
        return 1;
    }

    private static int maxout(ServerPlayer player) {
        AbilityState state = AbilityState.load(player);
        state.maxOutLevelProgress();
        saveSync(player, state);
        return 1;
    }

    private static void saveSync(ServerPlayer player, AbilityState state) {
        state.save(player);
        ACNetwork.sync(player, state);
    }

    private static int grantAdvancement(CommandSourceStack source, ServerPlayer player, String id) {
        String requested = id.startsWith("academy:") ? id.substring("academy:".length()) : id;
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add(requested);
        candidates.add(requested.replace('.', '/'));
        candidates.add("legacy/default/" + requested);
        for (String category : AbilityRegistry.categoryIds()) {
            candidates.add("legacy/" + category + "/" + requested);
            if (requested.startsWith(category + "."))
                candidates.add("legacy/" + category + "/" + requested.substring(category.length() + 1));
        }
        for (String path : candidates) {
            if (player.server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "academy", path)) != null) {
                ACAdvancements.grant(player, path);
                source.sendSuccess(() -> Component.literal("Granted AcademyCraft advancement " + path), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal("Unknown AcademyCraft advancement: " + requested));
        return 0;
    }

    private ACCommands() {}
}
