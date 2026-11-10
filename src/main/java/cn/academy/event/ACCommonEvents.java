package cn.academy.event;

import cn.academy.AcademyCraft;
import cn.academy.advancement.ACAdvancements;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.block.entity.ACMultiblockPartEntity;
import cn.academy.config.ACConfig;
import cn.academy.command.ACCommands;
import cn.academy.network.ACNetwork;
import cn.academy.network.OpenClientScreenPayload;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;


@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ACCommonEvents {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ACCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        state.tick();
        state.tutorialData().tick(player);

        state.tickInterference();
        if (state.interfered()) cn.academy.ability.AbilityExecutor.clearTransientDefenses(player);
        cn.academy.ability.AbilityContextManager.tick(player, state);
        cn.academy.ability.AbilityExecutor.tickTransient(player, state);
        cn.academy.ability.DevelopmentManager.tick(player, state);
        state.save(player);
        if (player.tickCount % 10 == 0) ACNetwork.sync(player, state);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, cn.academy.config.ACGeneralConfig.payload());
        AbilityState state = AbilityState.load(player);
        ACNetwork.sync(player, state);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityState state = AbilityState.load(player);
            cn.academy.ability.AbilityContextManager.abortAll(player, state);
            state.save(player);
            cn.academy.ability.AbilityExecutor.clearTransientDefenses(player);
            cn.academy.ability.DevelopmentManager.abort(player, false);
            ACMachineBlockEntity.releaseDevelopers(player);
            ACNetwork.clearWirelessAuthorization(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ACNetwork.sync(player, AbilityState.load(player));
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ACMachineBlockEntity.releaseDevelopers(player);
            ACNetwork.sync(player, AbilityState.load(player));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        AbilityState.replace(newPlayer, AbilityState.load(event.getOriginal()).toTag());
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if(event.getEntity() instanceof ServerPlayer player
                && cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.cancelIncoming(player)) {
            event.setCanceled(true);
            cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.queueCounterAttack(player,event.getSource(),event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.processCounterAttacks(event.getServer());
        cn.academy.ability.vanilla.vecmanip.skill.DirectedShockKnockback.sync(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.clearPendingCounters(event.getServer());
        cn.academy.ability.vanilla.vecmanip.skill.DirectedShockKnockback.clear(event.getServer());
    }

    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        // Native Pre runs after armor/resistance/enchantments and before absorption hearts.
        float damage = event.getNewDamage();
        if (damage <= 0) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            damage = cn.academy.ability.AbilityContextManager.absorbLightShield(player, event.getSource(), damage);
            if (damage > 0)
                damage = cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.reduceDamage(player, damage);
        }
        if (damage > 0)
            damage = cn.academy.ability.vanilla.meltdowner.skill.MDDamageHelper.amplifyDamage(event.getEntity(), damage);
        event.setNewDamage(Math.max(0, damage));
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living)
            cn.academy.ability.vanilla.meltdowner.skill.MDDamageHelper.livingTick(living);
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (event.wakeImmediately() || event.updateLevel()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityState state = AbilityState.load(player);
            state.recoverAll();
            state.save(player);
            ACNetwork.sync(player, state);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityState state = AbilityState.load(player);
            state.setActive(false);
            cn.academy.ability.AbilityContextManager.abortAll(player, state);
            cn.academy.ability.AbilityExecutor.clearTransientDefenses(player);
            cn.academy.ability.DevelopmentManager.abort(player, false);
            ACMachineBlockEntity.releaseDevelopers(player);
            state.recoverAll();
            state.save(player);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        boolean handled = true;

        if (item == ACItems.DEVELOPER_PORTABLE.get()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new OpenClientScreenPayload("developer:portable"));
        } else if (item == ACItems.COIN.get()) {
            if (cn.academy.ability.vanilla.electromaster.skill.Railgun.playerCoin(player) != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.PASS);
                return;
            }
            ACThrownItemEntity coin = new ACThrownItemEntity(player, player.serverLevel(), stack);
            player.serverLevel().addFreshEntity(coin);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new CoinThrowEvent(player, coin));
            consume(player, stack);
            player.serverLevel().playSound(player, player.blockPosition(), ACSounds.COIN_FLIP.get(), SoundSource.PLAYERS, .5f, 1);
        } else if (item == ACItems.MAG_HOOK.get() || item == ACItems.SILBARN.get()) {
            ACThrownItemEntity projectile = new ACThrownItemEntity(player, player.serverLevel(), stack);
            player.serverLevel().addFreshEntity(projectile);
            player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.EGG_THROW,
                    SoundSource.PLAYERS, .5f, .4f / (player.getRandom().nextFloat() * .4f + .8f));
            consume(player, stack);
        } else if (item == ACItems.TUTORIAL.get()) {
            ACAdvancements.trigger(player, "open_misaka_cloud");
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new OpenClientScreenPayload("tutorial"));
        } else {
            handled = false;
        }

        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onCoinThrow(CoinThrowEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            cn.academy.ability.vanilla.electromaster.skill.Railgun.onThrow(player, event.coin);
    }

    @SubscribeEvent
    public static void onReflect(cn.academy.event.ability.ReflectEvent event) {
        cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.onReflect(event);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level
                && level.getBlockEntity(event.getPos()) instanceof ACMultiblockPartEntity part
                && part.originState() != null
                && event.getPlayer() instanceof ServerPlayer player) {
            BlockPos origin = part.origin();
            BlockEvent.BreakEvent originEvent = new BlockEvent.BreakEvent(level, origin,
                    level.getBlockState(origin), player);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(originEvent);
            if (originEvent.isCanceled()) event.setCanceled(true);
        }
        // Inventory drops are handled by ACMachineBlock.onRemove only after the break is actually accepted.
    }

    private static void consume(ServerPlayer player, ItemStack stack) {
        if (!player.isCreative()) stack.shrink(1);
    }


    @SubscribeEvent
    public static void onMatterUnitHarvest(MatterUnitHarvestEvent event) {
        if (event.player instanceof ServerPlayer player) {
            // DispatcherAch triggers this for both PHASE_LIQUID and NONE, including placement.
            ACAdvancements.grant(player, "legacy/default/phase_liquid");
        }
    }

    private ACCommonEvents() {}
}
