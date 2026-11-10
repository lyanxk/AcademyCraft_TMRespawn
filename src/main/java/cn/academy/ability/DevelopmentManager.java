package cn.academy.ability;

import cn.academy.data.GameplayRules;

import cn.academy.data.GameplayData;

import cn.academy.advancement.ACAdvancements;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.network.ACNetwork;
import cn.academy.network.DeveloperActionPayload;
import cn.academy.network.DevelopmentSyncPayload;
import cn.academy.registry.ACDataComponents;
import cn.academy.registry.ACItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Restores DevelopData's per-stimulation, interruptible server lifecycle. */
public final class DevelopmentManager {
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final int action;
        final BlockPos pos;
        final boolean portable;
        final boolean offhand;
        final Developer developer;
        final String skill;
        final int ticksPerStimulation;
        final int energyPerTick;
        final int maxStimulations;
        int stimulations;
        int tickThisStimulation;
        int tickSync = 5;
        int state = DevelopmentSyncPayload.DEVELOPING;
        boolean dirty = true;

        Session(int action, BlockPos pos, boolean portable, boolean offhand, String skill,
                int ticksPerStimulation, int energyPerTick, int stimulations, Developer developer) {
            this.action = action;
            this.pos = pos;
            this.portable = portable;
            this.offhand = offhand;
            this.skill = skill;
            this.ticksPerStimulation = ticksPerStimulation;
            this.energyPerTick = energyPerTick;
            this.maxStimulations = stimulations;
            this.developer = developer;
        }
    }

    public static void handle(ServerPlayer player, DeveloperActionPayload payload) {
        if (payload.action() == DeveloperActionPayload.CLOSE) {
            if (!payload.portable() && player.serverLevel().getBlockEntity(payload.pos()) instanceof ACMachineBlockEntity machine)
                machine.releaseDeveloper(player);
            return;
        }
        if (payload.action() == DeveloperActionPayload.ABORT) {
            abort(player, true);
            return;
        }
        StartData start = validateStart(player, payload);
        if (start == null) {
            PacketDistributor.sendToPlayer(player, new DevelopmentSyncPayload(DevelopmentSyncPayload.FAILED,
                    payload.action(), payload.skill(), 0, payload.portable(), false, 0, 0));
            return;
        }
        // The old DevelopData API replaced an existing action when a new one started.
        ACTIVE.remove(player.getUUID());
        Session session = new Session(payload.action(), payload.pos(), payload.portable(), start.offhand,
                payload.skill(), start.ticksPerStimulation, start.energyPerTick, start.stimulations, start.developer);
        ACTIVE.put(player.getUUID(), session);
    }

    public static void tick(ServerPlayer player, AbilityState state) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        // DevelopData syncs before advancing, including the terminal state on the next tick.
        if (session.dirty) {
            session.dirty = false;
            sync(player, session, session.state, progress(session));
        }
        if (session.state != DevelopmentSyncPayload.DEVELOPING) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (session.tickSync-- == 0) {
            session.tickSync = 5;
            sync(player, session, session.state, progress(session));
        }
        if (!consumeTick(player, session)) {
            finish(session, false);
            return;
        }
        // Keep the original strict '>' boundary: a stimulation costs TPS + 1 energy pulls.
        if (++session.tickThisStimulation > session.ticksPerStimulation) {
            session.tickThisStimulation = 0;
            if (++session.stimulations >= session.maxStimulations) {
                finish(session, complete(player, state, session));
            }
        }
    }

    private static void finish(Session session, boolean success) {
        session.stimulations = session.tickThisStimulation = 0;
        session.state = success ? DevelopmentSyncPayload.DONE : DevelopmentSyncPayload.FAILED;
        session.dirty = true;
    }

    public static void abort(ServerPlayer player, boolean notify) {
        Session session = ACTIVE.remove(player.getUUID());
        if (session != null && notify) sync(player, session, DevelopmentSyncPayload.FAILED, 0);
    }

    public static boolean active(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());
        return session != null && session.state == DevelopmentSyncPayload.DEVELOPING;
    }

    private static StartData validateStart(ServerPlayer player, DeveloperActionPayload payload) {
        Developer developer = findDeveloper(player, payload.pos(), payload.portable());
        if (developer == null) return null;
        AbilityState state = AbilityState.load(player);
        int stimulations;
        switch (payload.action()) {
            case DeveloperActionPayload.LEARN_SKILL -> {
                AbilitySkill skill = AbilityRegistry.skill(payload.skill());
                if (skill == null || skill.level() > developer.maximumLevel || !state.canLearn(skill)) return null;
                stimulations = (int) (GameplayData.number("developers", "learning.base_stimulations") + skill.level() * skill.level() * GameplayData.number("developers", "learning.level_squared_multiplier"));
            }
            case DeveloperActionPayload.LEVEL_UP -> {
                if (state.hasCategory() && (state.level() >= 5 || state.levelProgress() < 1)) return null;
                stimulations = GameplayData.integer("developers", "level_up.stimulations_per_level") * (state.level() + 1);
            }
            case DeveloperActionPayload.RESET_CATEGORY -> {
                if (developer.maximumLevel < GameplayData.integer("developers", "reset.developer_level") || state.level() < GameplayData.integer("developers", "reset.minimum_level")
                        || !player.getMainHandItem().is(GameplayRules.item(GameplayData.string("developers", "reset.item")))
                        || differentFactor(player, state.category()) == null) return null;
                stimulations = state.level() * GameplayData.integer("developers", "reset.stimulations_per_level");
            }
            default -> { return null; }
        }
        return new StartData(developer.offhand, developer.ticksPerStimulation,
                developer.energyPerStimulation / developer.ticksPerStimulation, stimulations, developer);
    }

    private static boolean consumeTick(ServerPlayer player, Session session) {
        Developer developer = session.developer;
        if (session.portable) {
            ItemStack stack = portableStack(player);
            // PortableDevData pulls directly: a final insufficient charge is still drained.
            return !stack.isEmpty() && cn.academy.energy.api.IFItemManager.instance.pull(
                    stack, session.energyPerTick, true) == session.energyPerTick;
        }
        return developer.machine.consumeDevelopmentEnergy(session.energyPerTick);
    }

    private static boolean complete(ServerPlayer player, AbilityState state, Session session) {
        Developer developer = session.developer;
        if (session.action == DeveloperActionPayload.LEARN_SKILL) {
            AbilitySkill skill = AbilityRegistry.skill(session.skill);
            if (skill == null || skill.level() > developer.maximumLevel || !state.canLearn(skill)) return false;
            state.learn(skill);
            player.displayClientMessage(Component.translatable("ac.developer.learned", skill.displayName()), false);
            ACAdvancements.grantLegacySkillLearn(player, skill.category(), skill.name());
        } else if (session.action == DeveloperActionPayload.LEVEL_UP) {
            if (state.hasCategory()) {
                if (state.level() >= 5 || state.levelProgress() < 1) return false;
                state.setLevel(state.level() + 1);
            } else {
                ItemStack factor = anyFactor(player, "");
                String category = factor == null ? AbilityRegistry.randomCategory(player.getRandom())
                        : cn.academy.item.InductionFactorItem.category(factor);
                if (AbilityRegistry.category(category) == null) return false;
                state.setCategory(category);
                if (factor != null) clearFactor(player, factor);
                player.displayClientMessage(Component.translatable("ac.ability.category_acquired",
                        AbilityRegistry.category(category).displayName()), false);
            }
            ACAdvancements.grantLegacyLevels(player, state.category(), state.level());
        } else if (session.action == DeveloperActionPayload.RESET_CATEGORY) {
            ItemStack factor = differentFactor(player, state.category());
            if (developer.maximumLevel < GameplayData.integer("developers", "reset.developer_level") || state.level() < GameplayData.integer("developers", "reset.minimum_level") || factor == null
                    || !player.getMainHandItem().is(GameplayRules.item(GameplayData.string("developers", "reset.item")))) return false;
            String category = cn.academy.item.InductionFactorItem.category(factor);
            int previousLevel = state.level();
            var event = new cn.academy.event.ability.TransformCategoryEvent(player,
                    AbilityRegistry.category(category), Math.max(1, previousLevel - GameplayData.integer("developers", "reset.levels_lost")));
            if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()) {
                state.setCategory(category);
                state.setLevel(Math.max(1, previousLevel - GameplayData.integer("developers", "reset.levels_lost")));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                clearFactor(player, factor);
            }
        } else return false;
        state.save(player);
        ACNetwork.sync(player, state);
        return true;
    }

    private static Developer findDeveloper(ServerPlayer player, BlockPos pos, boolean portable) {
        if (portable) {
            ItemStack stack = portableStack(player);
            if (stack.isEmpty()) return null;
            return new Developer(null, stack, false, GameplayData.integer("developers", "portable.maximum_level"), GameplayData.integer("developers", "portable.ticks_per_stimulation"), GameplayData.integer("developers", "portable.energy_per_stimulation"));
        }
        if (player.distanceToSqr(pos.getCenter()) > 64
                || !(player.serverLevel().getBlockEntity(pos) instanceof ACMachineBlockEntity machine)) return null;
        return switch (machine.kind()) {
            case DEVELOPER_NORMAL -> new Developer(machine, ItemStack.EMPTY, false, GameplayData.integer("developers", "normal.maximum_level"), GameplayData.integer("developers", "normal.ticks_per_stimulation"), GameplayData.integer("developers", "normal.energy_per_stimulation"));
            case DEVELOPER_ADVANCED -> new Developer(machine, ItemStack.EMPTY, false, GameplayData.integer("developers", "advanced.maximum_level"), GameplayData.integer("developers", "advanced.ticks_per_stimulation"), GameplayData.integer("developers", "advanced.energy_per_stimulation"));
            default -> null;
        };
    }

    private static ItemStack portableStack(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.is(ACItems.DEVELOPER_PORTABLE.get()) ? stack : ItemStack.EMPTY;
    }

    private static void clearFactor(ServerPlayer player, ItemStack factor) {
        int index = player.getInventory().items.indexOf(factor);
        if (index >= 0) player.getInventory().items.set(index, ItemStack.EMPTY);
    }

    private static ItemStack anyFactor(ServerPlayer player, String excludedCategory) {
        for (ItemStack candidate : player.getInventory().items) {
            String value = cn.academy.item.InductionFactorItem.category(candidate);
            if (candidate.is(ACItems.INDUCTION_FACTOR.get()) && AbilityRegistry.category(value) != null
                    && (excludedCategory.isEmpty() || !excludedCategory.equals(value))) return candidate;
        }
        return null;
    }

    private static ItemStack differentFactor(ServerPlayer player, String category) {
        return anyFactor(player, category);
    }

    private static double developerEnergy(Developer developer) {
        if (developer == null) return 0;
        if (developer.machine != null) return developer.machine.energy.exactEnergy();
        return cn.academy.energy.api.IFItemManager.instance.getEnergy(developer.stack);
    }

    private static int developerCapacity(Developer developer) {
        if (developer == null) return 0;
        if (developer.machine != null) return developer.machine.energy.getMaxEnergyStored();
        return (int) cn.academy.energy.api.IFItemManager.instance.getMaxEnergy(developer.stack);
    }

    private static float progress(Session session) {
        // tickThisStim was not serialized in 1.12.2, so the client saw completed stimulations only.
        return session.state == DevelopmentSyncPayload.DEVELOPING
                ? session.stimulations / (float) session.maxStimulations : 0;
    }

    private static void sync(ServerPlayer player, Session session, int state, float progress) {
        Developer developer = session.state != DevelopmentSyncPayload.DEVELOPING ? null
                : session.portable ? findDeveloper(player, session.pos, true) : session.developer;
        PacketDistributor.sendToPlayer(player, new DevelopmentSyncPayload(state, session.action, session.skill, progress,
                session.portable, session.offhand, developerEnergy(developer), developerCapacity(developer)));
    }

    private record StartData(boolean offhand, int ticksPerStimulation, int energyPerTick, int stimulations, Developer developer) {}
    private record Developer(ACMachineBlockEntity machine, ItemStack stack, boolean offhand,
                             int maximumLevel, int ticksPerStimulation, int energyPerStimulation) {}

    private DevelopmentManager() { }
}
