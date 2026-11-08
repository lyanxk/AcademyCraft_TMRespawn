package cn.academy.network;

import cn.academy.ability.AbilityManager;
import cn.academy.ability.AbilityContextManager;
import cn.academy.ability.AbilityExecutor;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.block.entity.ACMultiblockPartEntity;
import cn.academy.menu.ACMachineMenu;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.ability.AbilityRegistry;
import cn.academy.advancement.ACAdvancements;
import cn.academy.block.MachineKind;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ACNetwork {
    private static final java.util.Map<java.util.UUID, WirelessAuthorization> WIRELESS_AUTH = new java.util.HashMap<>();
    private record WirelessAuthorization(net.minecraft.core.BlockPos primary, int linkAction,
                                         String password, long expiresAt) { }
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("62");
        registrar.playToClient(SettingsPolicyPayload.TYPE, SettingsPolicyPayload.STREAM_CODEC,
                (payload, context) -> payload.apply());
        registrar.playToClient(TerminalInstalledPayload.TYPE, TerminalInstalledPayload.STREAM_CODEC,
                (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(FlashingContextPayload.TYPE,FlashingContextPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToServer(FlashingInputPayload.TYPE,FlashingInputPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.flashingInput(player,payload);
        });
        registrar.playToClient(MeltdownerContextPayload.TYPE,MeltdownerContextPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(VectorDefensePayload.TYPE,VectorDefensePayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(VectorStrikePayload.TYPE,VectorStrikePayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(VectorImpulsePayload.TYPE,VectorImpulsePayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(ThreateningTeleportPayload.TYPE,ThreateningTeleportPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(ShiftTeleportPayload.TYPE,ShiftTeleportPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(TargetTeleportPayload.TYPE,TargetTeleportPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToServer(TargetTeleportInputPayload.TYPE,TargetTeleportInputPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.targetTeleportInput(player,payload);
        });
        registrar.playToClient(FleshRippingPayload.TYPE,FleshRippingPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(PlasmaCannonPayload.TYPE,PlasmaCannonPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToServer(PlasmaCannonInputPayload.TYPE,PlasmaCannonInputPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.plasmaInput(player,payload);
        });
        registrar.playToClient(StormWingPayload.TYPE,StormWingPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToServer(StormWingInputPayload.TYPE,StormWingInputPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.stormWingInput(player,payload);
        });
        registrar.playToServer(VectorImpulseInputPayload.TYPE,VectorImpulseInputPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.vectorImpulseInput(player,payload);
        });
        registrar.playToServer(VectorStrikeInputPayload.TYPE,VectorStrikeInputPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.vectorStrikeInput(player,payload);
        });
        registrar.playToServer(VectorDefenseEndPayload.TYPE,VectorDefenseEndPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.endVectorDefense(player,payload.context());
        });
        registrar.playToServer(MeltdownerEndPayload.TYPE,MeltdownerEndPayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player)AbilityContextManager.endMeltdowner(player,payload.context());
        });
        registrar.playToClient(MdRayEffectPayload.TYPE,MdRayEffectPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(RadiationMarkPayload.TYPE,RadiationMarkPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(ElectronBombEffectPayload.TYPE,ElectronBombEffectPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(MineRayParticlesPayload.TYPE,MineRayParticlesPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(MineRayEffectPayload.TYPE,MineRayEffectPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(MagneticManipulationPayload.TYPE,MagneticManipulationPayload.STREAM_CODEC,(payload,context)->payload.applyTo(context.player()));
        registrar.playToClient(MagneticMovementPayload.TYPE, MagneticMovementPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToServer(MagneticMovementUpdatePayload.TYPE, MagneticMovementUpdatePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AbilityContextManager.magneticUpdate(player, payload);
        });
        registrar.playToClient(MineDetectEffectPayload.TYPE, MineDetectEffectPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(ThunderClapEffectPayload.TYPE, ThunderClapEffectPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(ThunderClapLightningPayload.TYPE, ThunderClapLightningPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(IntensifyEffectPayload.TYPE, IntensifyEffectPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(ChargingEffectPayload.TYPE, ChargingEffectPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(RailgunChargePayload.TYPE, RailgunChargePayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToServer(RailgunPerformPayload.TYPE, RailgunPerformPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                cn.academy.ability.vanilla.electromaster.skill.Railgun.performClientRequest(player, payload.slot(), payload.coin());
        });
        registrar.playToClient(RailgunEffectPayload.TYPE, RailgunEffectPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(ArcEffectPayload.TYPE, ArcEffectPayload.STREAM_CODEC, (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(TutorialActivatedPayload.TYPE, TutorialActivatedPayload.STREAM_CODEC,
                (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(AbilityLifecyclePayload.TYPE, AbilityLifecyclePayload.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (player == null || payload.event() < AbilityLifecyclePayload.CATEGORY_CHANGED
                    || payload.event() > AbilityLifecyclePayload.PRESET_UPDATED) return;
            AbilityState.replace(player, payload.snapshot(), payload.event() != AbilityLifecyclePayload.PRESET_UPDATED);
            if (payload.event() == AbilityLifecyclePayload.CATEGORY_CHANGED) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.CategoryChangeEvent(player));
            } else if (payload.event() == AbilityLifecyclePayload.SKILL_LEARNED) {
                AbilitySkill skill = AbilityRegistry.skill(payload.skill());
                if (skill != null && AbilityState.load(player).learned().contains(skill.id()))
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.SkillLearnEvent(player, skill));
            } else if (payload.event() == AbilityLifecyclePayload.PRESET_UPDATED) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.PresetUpdateEvent(player));
            } else {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(payload.event() == AbilityLifecyclePayload.ACTIVATED
                        ? new cn.academy.event.ability.AbilityActivateEvent(player)
                        : new cn.academy.event.ability.AbilityDeactivateEvent(player));
            }
        });
        registrar.playToServer(PenetrationWheelPayload.TYPE, PenetrationWheelPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                AbilityContextManager.mouseWheel(player, AbilityState.load(player), payload.offset());
        });
        registrar.playToClient(TPCritHitPayload.TYPE, TPCritHitPayload.STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            if (player == null || payload.level() < 0 || payload.level() > 2) return;
            var target = player.level().getEntity(payload.target());
            if (target != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new cn.academy.ability.vanilla.teleporter.util.TPSkillHelper.TPCritHitEvent(player, target, payload.level()));
        });
        registrar.playToServer(AbilityActionPayload.TYPE, AbilityActionPayload.STREAM_CODEC, ACNetwork::handleAction);
        registrar.playToClient(AbilitySyncPayload.TYPE, AbilitySyncPayload.STREAM_CODEC, ACNetwork::handleSync);
        registrar.playToClient(AbilityContextSyncPayload.TYPE, AbilityContextSyncPayload.STREAM_CODEC, ACNetwork::handleContextSync);
        registrar.playToClient(ContextSoundPayload.TYPE, ContextSoundPayload.STREAM_CODEC, ACNetwork::handleContextSound);
        registrar.playToClient(OpenClientScreenPayload.TYPE, OpenClientScreenPayload.STREAM_CODEC, ACNetwork::handleOpenScreen);
        registrar.playToClient(VisualEffectPayload.TYPE, VisualEffectPayload.STREAM_CODEC, ACNetwork::handleVisualEffect);
        registrar.playToServer(MachineActionPayload.TYPE, MachineActionPayload.STREAM_CODEC, ACNetwork::handleMachineAction);
        registrar.playToServer(NodeConfigPayload.TYPE, NodeConfigPayload.STREAM_CODEC, ACNetwork::handleNodeConfig);
        registrar.playToServer(MatrixConfigPayload.TYPE, MatrixConfigPayload.STREAM_CODEC, ACNetwork::handleMatrixConfig);
        registrar.playToServer(InterfererConfigPayload.TYPE, InterfererConfigPayload.STREAM_CODEC,
                ACNetwork::handleInterfererConfig);
        registrar.playToServer(LocationActionPayload.TYPE, LocationActionPayload.STREAM_CODEC, ACNetwork::handleLocationAction);
        registrar.playToServer(WirelessConfigPayload.TYPE, WirelessConfigPayload.STREAM_CODEC, ACNetwork::handleWirelessConfig);
        registrar.playToServer(WirelessPageRequestPayload.TYPE, WirelessPageRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) cn.academy.energy.WirelessPageService.request(player, payload);
        });
        registrar.playToServer(WirelessPageActionPayload.TYPE, WirelessPageActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) cn.academy.energy.WirelessPageService.action(player, payload);
        });
        registrar.playToClient(WirelessPageSnapshotPayload.TYPE, WirelessPageSnapshotPayload.STREAM_CODEC,
                (payload, context) -> payload.applyTo(context.player()));
        registrar.playToServer(FrequencyTransmitterPayload.TYPE, FrequencyTransmitterPayload.STREAM_CODEC,
                ACNetwork::handleFrequencyTransmitter);
        registrar.playToClient(FrequencyTransmitterResultPayload.TYPE, FrequencyTransmitterResultPayload.STREAM_CODEC,
                ACNetwork::handleFrequencyResult);
        registrar.playToServer(DeveloperActionPayload.TYPE, DeveloperActionPayload.STREAM_CODEC, ACNetwork::handleDeveloperAction);
        registrar.playToClient(DeveloperUserPayload.TYPE, DeveloperUserPayload.STREAM_CODEC,
                (payload, context) -> payload.applyTo(context.player()));
        registrar.playToClient(DevelopmentSyncPayload.TYPE, DevelopmentSyncPayload.STREAM_CODEC, ACNetwork::handleDevelopmentSync);
    }

    private static void handleAction(AbilityActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        switch (payload.action()) {
            case AbilityActionPayload.TOGGLE -> AbilityManager.toggle(player, state);
            case AbilityActionPayload.USE_SLOT -> { return; } // Legacy instant-use packets cannot bypass key contexts.
            case AbilityActionPayload.SELECT_PRESET -> { return; }
            case AbilityActionPayload.REQUEST_SYNC -> { }
            case AbilityActionPayload.LEARN_SKILL -> { } // Learning is performed by a developer, not the terminal.
            case AbilityActionPayload.SET_SETTING -> {
                // Only the integrated server's owner sees these world-wide settings in the original UI.
                // Remote/LAN players cannot change server policy through a forged settings packet.
                if (!player.server.isSingleplayer() || !player.server.isSingleplayerOwner(player.getGameProfile())) return;
                var property = switch (payload.argument()) {
                    case "attack_players" -> cn.academy.config.ACConfig.ATTACK_PLAYERS;
                    case "destroy_blocks" -> cn.academy.config.ACConfig.DESTROY_BLOCKS;
                    default -> null;
                };
                if (property == null) return;
                property.set(payload.value() != 0);
                cn.academy.config.ACGeneralConfig.SPEC.save();
                cn.academy.config.ACGeneralConfig.broadcast();
                return;
            }
            case AbilityActionPayload.SWITCH_PRESET -> { return; }
            case AbilityActionPayload.KEY_DOWN -> AbilityContextManager.keyDown(player, state, payload.value(), payload.contextRequestId());
            case AbilityActionPayload.KEY_UP -> AbilityContextManager.keyUp(player, state, payload.value(), false);
            case AbilityActionPayload.KEY_ABORT -> AbilityContextManager.keyUp(player, state, payload.value(), true);
            case AbilityActionPayload.FLUSH_CONTROLS -> AbilityContextManager.presetUpdated(player, state);
            case AbilityActionPayload.MOUSE_WHEEL -> AbilityContextManager.mouseWheel(player, state, payload.value());
            case AbilityActionPayload.MOVEMENT_INPUT -> {
                AbilityContextManager.updateMovementInput(player, payload.value());
                return;
            }
            case AbilityActionPayload.MOVEMENT_ABORT -> {
                AbilityContextManager.updateMovementInput(player, payload.value(), false);
                return;
            }
            default -> { return; }
        }
        sync(player, state);
    }

    private static void handleSync(AbilitySyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player != null) AbilityState.replace(player, payload.data());
    }

    private static void handleContextSync(AbilityContextSyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null || payload.slot() < AbilityContextSyncPayload.FLASHING_CONTEXT || payload.slot() >= AbilityState.SKILL_CHANNELS
                || payload.slot() == AbilityContextSyncPayload.FLASHING_CONTEXT && !payload.skill().equals("teleporter.flashing")) return;
        net.minecraft.nbt.CompoundTag contexts = player.getPersistentData().getCompound("academy:ability_contexts");
        String key = Integer.toString(payload.slot());
        if (payload.state() == AbilityContextSyncPayload.ENDED) {
            contexts.remove(key);
        } else {
            net.minecraft.nbt.CompoundTag value = new net.minecraft.nbt.CompoundTag();
            value.putString("Skill", payload.skill());
            value.putInt("Ticks", payload.ticks());
            value.putInt("Target", payload.targetTicks());
            value.putInt("State", payload.state());
            value.putFloat("ConsumptionHint", Float.isFinite(payload.consumptionHint())
                    ? Math.max(0, payload.consumptionHint()) : 0);
            contexts.put(key, value);
        }
        player.getPersistentData().put("academy:ability_contexts", contexts);
        if (payload.state() == AbilityContextSyncPayload.ENDED && payload.skill().equals("teleporter.flashing"))
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.FlushControlEvent());
    }

    private static void handleContextSound(ContextSoundPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;
        net.minecraft.nbt.CompoundTag value = new net.minecraft.nbt.CompoundTag();
        value.putUUID("Entity", payload.entity());
        value.putString("Sound", payload.sound());
        value.putBoolean("Start", payload.start());
        value.putFloat("Volume", payload.volume());
        net.minecraft.nbt.ListTag queue = player.getPersistentData().getList("academy:context_sound_queue", 10);
        queue.add(value);
        player.getPersistentData().put("academy:context_sound_queue", queue);
    }

    private static void handleDeveloperAction(DeveloperActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player)
            cn.academy.ability.DevelopmentManager.handle(player, payload);
    }

    private static void handleDevelopmentSync(DevelopmentSyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;
        net.minecraft.nbt.CompoundTag value = new net.minecraft.nbt.CompoundTag();
        value.putInt("State", payload.state());
        value.putInt("Action", payload.action());
        value.putString("Skill", payload.skill());
        value.putFloat("Progress", payload.progress());
        value.putBoolean("Portable", payload.portable());
        value.putBoolean("Offhand", payload.offhand());
        value.putDouble("Energy", payload.energy());
        value.putInt("MaxEnergy", payload.maxEnergy());
        player.getPersistentData().put("academy:development", value);

        // A Screen does not own a container menu, so normal slot synchronization can trail the
        // six-tick development update. Mirror the authoritative value onto the held portable
        // developer immediately; this drives both its item bar and its empty/half/full artwork.
        if (payload.portable() && payload.maxEnergy() > 0) {
            ItemStack stack = payload.offhand() ? player.getOffhandItem() : player.getMainHandItem();
            if (stack.is(ACItems.DEVELOPER_PORTABLE.get()))
                cn.academy.energy.api.IFItemManager.instance.setEnergy(stack,
                        Math.max(0, Math.min(payload.maxEnergy(), payload.energy())));
        }
    }

    private static void handleFrequencyTransmitter(FrequencyTransmitterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        int result = 3;
        String displayName = "";
        ACMachineBlockEntity primary = wirelessMachine(player, payload.primary());
        ACMachineBlockEntity target = wirelessMachine(player, payload.target());
        boolean appAvailable = state.terminalInstalled() && state.apps().contains("freq_transmitter");
        switch (payload.action()) {
            case FrequencyTransmitterPayload.AUTH_MATRIX -> {
                if (appAvailable && primary != null && primary.kind() == MachineKind.MATRIX
                        && player.distanceToSqr(payload.primary().getCenter()) <= 36) {
                    displayName = primary.networkId();
                    result = primary.authenticateMatrix(payload.password()) ? 0 : 1;
                    if (result == 0) WIRELESS_AUTH.put(player.getUUID(), new WirelessAuthorization(
                            primary.getBlockPos(), FrequencyTransmitterPayload.LINK_NODE, payload.password(),
                            player.serverLevel().getGameTime() + 400));
                    else WIRELESS_AUTH.remove(player.getUUID());
                }
            }
            case FrequencyTransmitterPayload.AUTH_NODE -> {
                if (appAvailable && primary != null && primary.kind().isNetworkNode()
                        && primary.kind() != MachineKind.MATRIX
                        && player.distanceToSqr(payload.primary().getCenter()) <= 36) {
                    displayName = primary.nodeName();
                    result = primary.authenticateNode(payload.password()) ? 0 : 1;
                    if (result == 0) WIRELESS_AUTH.put(player.getUUID(), new WirelessAuthorization(
                            primary.getBlockPos(), FrequencyTransmitterPayload.LINK_USER, payload.password(),
                            player.serverLevel().getGameTime() + 400));
                    else WIRELESS_AUTH.remove(player.getUUID());
                }
            }
            case FrequencyTransmitterPayload.LINK_NODE -> {
                WirelessAuthorization auth = validWirelessAuthorization(player, payload);
                if (appAvailable && auth != null && primary != null && target != null
                        && player.distanceToSqr(payload.target().getCenter()) <= 36)
                    result = target.linkToMatrix(player, primary, auth.password());
            }
            case FrequencyTransmitterPayload.LINK_USER -> {
                WirelessAuthorization auth = validWirelessAuthorization(player, payload);
                if (appAvailable && auth != null && primary != null && target != null
                        && player.distanceToSqr(payload.target().getCenter()) <= 36)
                    result = target.linkToNode(player, primary, auth.password());
            }
            default -> { }
        }
        PacketDistributor.sendToPlayer(player, new FrequencyTransmitterResultPayload(payload.requestId(),
                payload.action(), result, displayName));
    }

    private static WirelessAuthorization validWirelessAuthorization(ServerPlayer player,
                                                                     FrequencyTransmitterPayload payload) {
        WirelessAuthorization auth = WIRELESS_AUTH.get(player.getUUID());
        if (auth == null || auth.linkAction() != payload.action() || !auth.primary().equals(payload.primary())
                || player.serverLevel().getGameTime() > auth.expiresAt()) {
            WIRELESS_AUTH.remove(player.getUUID());
            return null;
        }
        return auth;
    }

    public static void clearWirelessAuthorization(ServerPlayer player) {
        WIRELESS_AUTH.remove(player.getUUID());
    }

    private static ACMachineBlockEntity wirelessMachine(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        if (blockEntity instanceof ACMultiblockPartEntity part)
            blockEntity = player.serverLevel().getBlockEntity(part.origin());
        return blockEntity instanceof ACMachineBlockEntity machine ? machine : null;
    }

    private static void handleFrequencyResult(FrequencyTransmitterResultPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;
        net.minecraft.nbt.CompoundTag result = new net.minecraft.nbt.CompoundTag();
        result.putInt("Request", payload.requestId());
        result.putInt("Action", payload.action());
        result.putInt("Result", payload.result());
        result.putString("Name", payload.displayName());
        player.getPersistentData().put("academy:frequency_result", result);
    }

    private static void handleWirelessConfig(WirelessConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.pos().getCenter()) > 100) return;
        AbilityState state = AbilityState.load(player);
        boolean openedMachine = player.containerMenu instanceof ACMachineMenu open && open.machine() != null
                && open.machine().getBlockPos().equals(payload.pos());
        if (!openedMachine && (!state.terminalInstalled() || !state.apps().contains("freq_transmitter"))) return;
        if (!(player.serverLevel().getBlockEntity(payload.pos()) instanceof ACMachineBlockEntity machine)) return;
        int result;
        if (payload.target().isPresent()) {
            net.minecraft.core.BlockPos targetPos = payload.target().get();
            ACMachineBlockEntity target = player.serverLevel().hasChunkAt(targetPos)
                    ? wirelessMachine(player, targetPos) : null;
            result = target == null ? 3 : machine.kind().isNetworkNode()
                    ? machine.linkToMatrix(player, target, payload.password())
                    : machine.linkToNode(player, target, payload.password());
        } else result = machine.configureNetwork(player, payload.network(), payload.password());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(switch (result) {
            case 0 -> "ac.frequency.configured";
            case 1 -> "ac.frequency.permission_denied";
            case 2 -> "ac.frequency.authentication_failed";
            default -> "ac.frequency.invalid";
        }), false);
    }

    private static void handleLocationAction(LocationActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        if (!state.category().equals("teleporter") || !state.learned().contains("teleporter.location_teleport")) return;
        boolean changed = switch (payload.action()) {
            case LocationActionPayload.ADD -> state.addTeleportLocation(payload.name(), player.level(),
                    player.getX(), player.getY(), player.getZ());
            case LocationActionPayload.REMOVE -> state.removeTeleportLocation(payload.index());
            case LocationActionPayload.TELEPORT -> AbilityExecutor.performLocationTeleport(player, state, payload.index());
            default -> false;
        };
        if (changed) {
            state.save(player);
            sync(player, state);
        }
    }

    private static void handleMachineAction(MachineActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.pos().getCenter()) > 64
                || !(player.containerMenu instanceof ACMachineMenu open) || open.machine() == null
                || !open.machine().getBlockPos().equals(payload.pos())) return;
        if (player.serverLevel().getBlockEntity(payload.pos()) instanceof ACMachineBlockEntity machine) {
            if (payload.action() == MachineActionPayload.CYCLE_MODE) machine.cycleMode(1);
            else if (payload.action() == MachineActionPayload.CYCLE_MODE_BACK) machine.cycleMode(-1);
        }
    }

    private static void handleInterfererConfig(InterfererConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.pos().getCenter()) > 64
                || !(player.containerMenu instanceof ACMachineMenu open) || open.machine() == null
                || !open.machine().getBlockPos().equals(payload.pos())) return;
        open.machine().configureInterferer(player, payload.enabled(), payload.range(), payload.whitelist(), payload.field());
    }

    private static void handleMatrixConfig(MatrixConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.pos().getCenter()) > 64
                || !(player.containerMenu instanceof ACMachineMenu open) || open.machine() == null
                || !open.machine().getBlockPos().equals(payload.pos())) return;
        int result = open.machine().configureMatrix(player, payload.ssid(), payload.password(), payload.updatePassword());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(switch (result) {
            case 0 -> "ac.frequency.configured";
            case 1 -> "ac.frequency.permission_denied";
            case 2 -> "ac.frequency.authentication_failed";
            default -> "ac.frequency.invalid";
        }), false);
    }

    private static void handleNodeConfig(NodeConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.pos().getCenter()) > 64
                || !(player.containerMenu instanceof ACMachineMenu open) || open.machine() == null
                || !open.machine().getBlockPos().equals(payload.pos())) return;
        int result = open.machine().configureNode(player, payload.name(), payload.password(), payload.updatePassword());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(result == 0
                ? "ac.node.configured" : result == 1 ? "ac.frequency.permission_denied" : "ac.frequency.invalid"), false);
    }

    private static void handleVisualEffect(VisualEffectPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Effect", payload.effect());
        tag.putDouble("SX", payload.startX()); tag.putDouble("SY", payload.startY()); tag.putDouble("SZ", payload.startZ());
        tag.putDouble("EX", payload.endX()); tag.putDouble("EY", payload.endY()); tag.putDouble("EZ", payload.endZ());
        tag.putFloat("Scale", payload.scale()); tag.putInt("Color", payload.color()); tag.putInt("Duration", payload.duration());
        net.minecraft.nbt.ListTag queue = player.getPersistentData().getList("academy:visual_effect_queue", 10);
        queue.add(tag);
        player.getPersistentData().put("academy:visual_effect_queue", queue);
    }

    private static void handleOpenScreen(OpenClientScreenPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player != null) payload.applyTo(player.getPersistentData());
    }

    public static void sync(ServerPlayer player, AbilityState state) {
        state.save(player);
        PacketDistributor.sendToPlayer(player, new AbilitySyncPayload(state.toTag()));
    }

    private ACNetwork() {}
}
