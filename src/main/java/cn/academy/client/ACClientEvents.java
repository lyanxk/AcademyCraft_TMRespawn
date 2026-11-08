package cn.academy.client;

import cn.academy.client.render.ACCPBarHud;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.client.screen.SkillTreeScreen;
import cn.academy.client.screen.TutorialScreen;
import cn.academy.client.screen.LocationTeleportScreen;
import cn.academy.client.screen.DeveloperScreen;
import cn.academy.client.render.ACVisualEffects;
import cn.academy.client.render.ACNotifications;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.client.sound.ACMediaPlayer;
import cn.academy.registry.ACItems;
import net.minecraft.core.BlockPos;
import cn.academy.network.AbilityActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import com.mojang.math.Axis;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ACClientEvents {
    @SubscribeEvent
    public static void appInstalled(cn.academy.event.AppInstalledEvent event) {
        // An integrated server also posts this event: only refresh the local logical client.
        if (event.player == Minecraft.getInstance().player && event.player.level().isClientSide)
            TerminalOverlay.refreshApps();
    }
    @SubscribeEvent
    public static void targetTeleportEffect(cn.academy.network.TargetTeleportPayload.Received event) {
        cn.academy.client.render.TargetTeleportEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void flashingEffect(cn.academy.network.FlashingContextPayload.Received event) {
        cn.academy.client.render.FlashingEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void shiftTeleportEffect(cn.academy.network.ShiftTeleportPayload.Received event) {
        cn.academy.client.render.ShiftTeleportEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void threateningTeleportEffect(cn.academy.network.ThreateningTeleportPayload.Received event) {
        cn.academy.client.render.ThreateningTeleportEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void fleshRippingEffect(cn.academy.network.FleshRippingPayload.Received event) {
        cn.academy.client.render.FleshRippingEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void plasmaCannonEffect(cn.academy.network.PlasmaCannonPayload.Received event) {
        cn.academy.client.render.PlasmaCannonEffects.receive(event.payload);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void stormWingEffect(cn.academy.network.StormWingPayload.Received event) {
        cn.academy.client.render.StormWingEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void vectorImpulseEffect(cn.academy.network.VectorImpulsePayload.Received event) {
        cn.academy.client.render.VectorImpulseEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void vectorStrikeEffect(cn.academy.network.VectorStrikePayload.Received event) {
        cn.academy.client.render.VectorStrikeEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void vectorStrikeTickPre(ClientTickEvent.Pre event) {
        cn.academy.client.render.VectorStrikeEffects.tickPre(Minecraft.getInstance());
    }
    @SubscribeEvent
    public static void vectorDefenseEffect(cn.academy.network.VectorDefensePayload.Received event) {
        cn.academy.client.render.VectorDefenseEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void meltdownerEffect(cn.academy.network.MeltdownerContextPayload.Received event) {
        if(event.receiver==Minecraft.getInstance().player)
            cn.academy.client.render.MeltdownerEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void mdRayEffect(cn.academy.network.MdRayEffectPayload.Received event) {
        if(event.receiver==Minecraft.getInstance().player)
            cn.academy.client.render.MdSmallRayEffects.receive(event.payload);
    }
    @SubscribeEvent
    public static void radiationTick(cn.academy.ability.vanilla.meltdowner.skill.MDDamageHelper.ClientTick event) {
        cn.academy.client.render.RadiationEffects.emit(event.entity);
    }
    @SubscribeEvent
    public static void electronBombEffect(cn.academy.network.ElectronBombEffectPayload.Received event) {
        if(event.receiver==Minecraft.getInstance().player)
            cn.academy.client.render.MdSmallRayEffects.receive(event.ball);
    }
    @SubscribeEvent
    public static void electronBallAdded(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide() && event.getEntity() instanceof cn.academy.entity.ACElectronBallEntity ball)
            cn.academy.client.render.ACElectronBallRenderer.spawned(ball);
        if(event.getLevel().isClientSide() && event.getEntity() instanceof cn.academy.entity.ACThrownItemEntity thrown)
            cn.academy.client.render.SilbarnEffects.spawned(thrown);
    }
    @SubscribeEvent
    public static void silbarnFragments(cn.academy.entity.ACThrownItemEntity.SilbarnFragments event) {
        cn.academy.client.render.SilbarnEffects.burst(event);
    }
    @SubscribeEvent
    public static void onRecipesUpdated(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) {
        cn.academy.crafting.MachineRecipes.invalidate();
    }
    @SubscribeEvent
    public static void onTutorialActivated(cn.academy.event.TutorialActivatedEvent event) {
        if (event.getEntity() == Minecraft.getInstance().player)
            ACNotifications.showTutorialUpdated(event.tutorial.id);
    }
    private static final AbilityKeyInput[] SLOT_INPUT = java.util.stream.IntStream
            .range(0, AbilityState.SKILL_CHANNELS).mapToObj(ignored -> new AbilityKeyInput())
            .toArray(AbilityKeyInput[]::new);
    private static boolean activateHeld;
    private static int activateTicks;
    private static final AbilityToggleInput ACTIVATE_INPUT = new AbilityToggleInput();
    private static final AbilityKeyInput TERMINAL_INPUT = new AbilityKeyInput();
    private static int lastMovementInput;
    private static boolean lastMovementEnabled;
    private static net.minecraft.client.player.LocalPlayer inputPlayer;
    private static boolean requireControlFlush;
    private static java.util.List<String> abilityKeyBindings;
    @SubscribeEvent
    public static void flashingGravity(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        cn.academy.client.render.FlashingEffects.gravityTick();
    }

    @SubscribeEvent
    public static void flashingMovement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player || !AbilityState.load(player).active()) return;
        if(cn.academy.client.render.StormWingEffects.controlsActive()) {
            var input=event.getInput();input.leftImpulse=input.forwardImpulse=0;
            input.up=input.down=input.left=input.right=false;return;
        }
        if(cn.academy.client.render.FlashingEffects.controlsActive()) {
            // Suppress locomotion only. Never mutate KeyMapping registrations or physical key state.
            var input = event.getInput();
            input.leftImpulse = input.forwardImpulse = 0;
            input.up = input.down = input.left = input.right = false;
        }
    }
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        ACCPBarHud.tickClock();
        Minecraft minecraft = Minecraft.getInstance();
        if (inputPlayer != minecraft.player) {
            resetInputState();
            ACNotifications.reset();
            ACMediaPlayer.reset();
            inputPlayer = minecraft.player;
        }
        TerminalOverlay.tick();
        if (minecraft.player == null) {
            ACContextSounds.stopAll();
            cn.academy.client.sound.ACMachineSounds.stopAll();
            ACMediaPlayer.stop();
            ACFrequencyTransmitter.stop();
            return;
        }
        if (!minecraft.isPaused()) AbilityState.load(minecraft.player).tickCooldowns();
        if (!AbilityState.load(minecraft.player).active()) cn.academy.client.render.ACKeyHintHud.reset();
        var currentBindings = java.util.Arrays.stream(ACKeyMappings.SLOTS)
                .map(key -> key.getKey().getName() + ":" + key.getKeyModifier()).toList();
        if (abilityKeyBindings != null && !abilityKeyBindings.equals(currentBindings))
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.FlushControlEvent());
        abilityKeyBindings = currentBindings;
        if (requireControlFlush) {
            requireControlFlush = false;
            abortHeldSlots();
            PacketDistributor.sendToServer(AbilityActionPayload.flushControls());
        }
        net.minecraft.nbt.ListTag soundQueue = minecraft.player.getPersistentData()
                .getList("academy:context_sound_queue", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (net.minecraft.nbt.Tag raw : soundQueue) {
            net.minecraft.nbt.CompoundTag sound = (net.minecraft.nbt.CompoundTag) raw;
            if (sound.hasUUID("Entity")) ACContextSounds.handle(sound.getUUID("Entity"), sound.getString("Sound"),
                    sound.getBoolean("Start"), sound.getFloat("Volume"));
        }
        if (!soundQueue.isEmpty()) minecraft.player.getPersistentData().remove("academy:context_sound_queue");
        ACVisualEffects.clientTick(minecraft);
        ACNotifications.tick();
        ACMediaPlayer.tick();
        ACFrequencyTransmitter.tick();
        String notice = minecraft.player.getPersistentData().getString("academy:notification");
        if (!notice.isEmpty()) {
            minecraft.player.getPersistentData().remove("academy:notification");
            if (notice.startsWith("notify:app:")) ACNotifications.showAppInstalled(notice.substring("notify:app:".length()));
            else if (notice.startsWith("notify:tutorial:")) ACNotifications.showTutorialUpdated(notice.substring("notify:tutorial:".length()));
        }
        String requestedScreen = minecraft.player.getPersistentData().getString("academy:open_screen");
        if (!requestedScreen.isEmpty()) {
            if (!requestedScreen.equals("terminal_install")) abortHeldSlots();
            minecraft.player.getPersistentData().remove("academy:open_screen");
            if (requestedScreen.equals("close")) minecraft.setScreen(null);
            else if (requestedScreen.equals("tutorial")) minecraft.setScreen(new TutorialScreen());
            else if (requestedScreen.equals("skills")) minecraft.setScreen(new SkillTreeScreen());
            else if (requestedScreen.equals("terminal")) TerminalOverlay.open();
            else if (requestedScreen.equals("terminal_install")) TerminalOverlay.install();
            else if (requestedScreen.equals("locations")) minecraft.setScreen(new LocationTeleportScreen());
            else if (requestedScreen.equals("developer:portable")) minecraft.setScreen(new DeveloperScreen(BlockPos.ZERO, true));
            else if (requestedScreen.startsWith("bridge:")) {
                String[] parts = requestedScreen.split(":");
                if (parts.length == 4) try {
                    minecraft.setScreen(new cn.academy.client.screen.EnergyBridgeScreen(new BlockPos(
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))));
                } catch (NumberFormatException ignored) { }
            }
            else if (requestedScreen.startsWith("developer:")) {
                String[] parts = requestedScreen.split(":");
                if (parts.length == 4) try {
                    minecraft.setScreen(new DeveloperScreen(new BlockPos(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), false));
                } catch (NumberFormatException ignored) { }
            }
            return;
        }
        updateTerminalInput(ACKeyMappings.physicalDown(ACKeyMappings.TERMINAL),
                minecraft.screen == null && minecraft.isWindowActive());
        while (ACKeyMappings.TERMINAL.consumeClick()) { }
        if (minecraft.screen != null || !minecraft.isWindowActive() || ACFrequencyTransmitter.active() || TerminalOverlay.active()) {
            abortHeldSlots();
            return;
        }
        if (activateHeld) activateTicks++;
        AbilityState controlState = AbilityState.load(minecraft.player);
        boolean controlsEnabled = controlState.canUse();
        sendMovementInput(physicalMovement(), controlsEnabled);
        int selected = SkillSelection.selected(controlState);
        boolean castDown = ACKeyMappings.physicalDown(ACKeyMappings.CAST_SKILL);
        boolean canPress = SkillSelection.canPress(castDown);
        for (int slot = 0; slot < ACKeyMappings.SLOTS.length; slot++) {
            boolean physicalDown = slot == selected && castDown && canPress
                    || SkillKeyBindings.down(controlState.controlSkill(slot));
            AbilitySkill skill = AbilityRegistry.skill(controlState.controlSkill(slot));
            boolean enabled = controlsEnabled && skill != null && controlState.cooldown(skill.id()) <= 0;
            updateSlotInput(slot, physicalDown, enabled);
        }
        ClientRailgun.tick(SLOT_INPUT);
    }

    @SubscribeEvent
    public static void abilityKeyboard(InputEvent.Key event) {
        handleInputEdge(mapping -> mapping.matches(event.getKey(), event.getScanCode()), event.getAction(), event.getModifiers(),
                com.mojang.blaze3d.platform.InputConstants.getKey(event.getKey(), event.getScanCode()));
    }

    @SubscribeEvent
    public static void abilityMouse(InputEvent.MouseButton.Post event) {
        handleInputEdge(mapping -> mapping.matchesMouse(event.getButton()), event.getAction(), event.getModifiers(),
                com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
    }

    private static void handleInputEdge(java.util.function.Predicate<net.minecraft.client.KeyMapping> matches,
                                        int action, int modifiers, com.mojang.blaze3d.platform.InputConstants.Key changedKey) {
        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS && action != org.lwjgl.glfw.GLFW.GLFW_RELEASE) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (inputPlayer != minecraft.player) {
            resetInputState();
            inputPlayer = minecraft.player;
        }
        boolean down = action == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (matches.test(ACKeyMappings.TERMINAL)) updateTerminalInput(down,
                minecraft.screen == null && minecraft.isWindowActive()
                        && (!down || modifierMatches(ACKeyMappings.TERMINAL, modifiers)));
        boolean inGame = minecraft.screen == null && minecraft.isWindowActive()
                && !ACFrequencyTransmitter.active() && !TerminalOverlay.active();
        var state = AbilityState.load(minecraft.player);
        if (matches.test(ACKeyMappings.ACTIVATE)) {
            if(down && inGame && modifierMatches(ACKeyMappings.ACTIVATE,modifiers)
                    && cn.academy.client.render.FlashingEffects.endByActivate())return;
            if(down && inGame && modifierMatches(ACKeyMappings.ACTIVATE,modifiers)
                    && cn.academy.client.render.StormWingEffects.endByActivate())return;
            boolean wasHeld = ACTIVATE_INPUT.held();
            if (ACTIVATE_INPUT.update(down, inGame && state.hasCategory()
                    && (!down || modifierMatches(ACKeyMappings.ACTIVATE, modifiers)), net.minecraft.Util.getMillis()))
                PacketDistributor.sendToServer(AbilityActionPayload.toggle());
            activateHeld = ACTIVATE_INPUT.held();
            ACCPBarHud.numbersHeld(activateHeld);
            if (!activateHeld || !wasHeld) activateTicks = 0;
        }
        boolean enabled = inGame && state.canUse();
        int selected = SkillSelection.selected(state);
        boolean canPress = !matches.test(ACKeyMappings.CAST_SKILL) || SkillSelection.canPress(down);
        for (int slot = 0; slot < SLOT_INPUT.length; slot++) {
            var key = ACKeyMappings.CAST_SKILL;
            String skillId = state.controlSkill(slot);
            if (!(slot == selected && matches.test(key)) && !SkillKeyBindings.keys(skillId).contains(changedKey)) continue;
            var skill = AbilityRegistry.skill(state.controlSkill(slot));
            boolean selectedDown = slot == selected && (matches.test(key)
                    ? down && canPress && modifierMatches(key, modifiers)
                    : ACKeyMappings.physicalDown(key) && SkillSelection.canPress(true));
            updateSlotInput(slot, selectedDown || SkillKeyBindings.edgeDown(skillId, changedKey, down),
                    enabled && skill != null && state.cooldown(skill.id()) <= 0);
        }
        var options = minecraft.options;
        var movementKeys = new net.minecraft.client.KeyMapping[]{options.keyUp, options.keyDown, options.keyLeft, options.keyRight};
        int movement = lastMovementInput < 0 ? physicalMovement() : lastMovementInput;
        boolean changed = false;
        for (int i = 0; i < movementKeys.length; i++) {
            if (!matches.test(movementKeys[i])) continue;
            changed = true;
            if (down && modifierMatches(movementKeys[i], modifiers)) movement |= 1 << i;
            else movement &= ~(1 << i);
        }
        if (changed) sendMovementInput(movement, enabled);
    }

    private static void updateTerminalInput(boolean down, boolean enabled) {
        if (TERMINAL_INPUT.update(down, enabled) == AbilityKeyInput.Action.UP) TerminalOverlay.toggle();
    }

    public static void suppressTerminalInputUntilRelease() {
        TERMINAL_INPUT.update(ACKeyMappings.physicalDown(ACKeyMappings.TERMINAL), false);
    }

    @SubscribeEvent
    public static void selectMusic(net.neoforged.neoforge.client.event.SelectMusicEvent event) {
        if (ACMediaPlayer.suppressesBackgroundMusic()) event.setMusic(null);
    }

    private static boolean modifierMatches(net.minecraft.client.KeyMapping mapping, int modifiers) {
        int mask = switch (mapping.getKeyModifier()) {
            case SHIFT -> org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
            case ALT -> org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
            case CONTROL -> Minecraft.ON_OSX ? org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER : org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
            default -> 0;
        };
        return mask == 0 || (modifiers & mask) != 0;
    }

    static void updateSlotInput(int slot, boolean down, boolean enabled) {
        var action = SLOT_INPUT[slot].update(down, enabled);
        if(action==AbilityKeyInput.Action.UP && cn.academy.client.render.ShiftTeleportEffects.abortRelease(slot))
            action=AbilityKeyInput.Action.ABORT;
        if (action == AbilityKeyInput.Action.UP || action == AbilityKeyInput.Action.ABORT) {
            cn.academy.client.render.VectorImpulseEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.PlasmaCannonEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.FleshRippingEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.ThreateningTeleportEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.ShiftTeleportEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.TargetTeleportEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.VectorStrikeEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.MeltdownerEffects.endLocal(slot,action==AbilityKeyInput.Action.ABORT);
            cn.academy.client.render.ThunderClapEffects.endLocal(slot);
            cn.academy.client.render.MagneticMovementEffects.endLocal(slot);
            cn.academy.client.render.MineRayEffects.endLocal(slot);
        }
        if (action == AbilityKeyInput.Action.ABORT) {
            cn.academy.client.render.IntensifyEffects.abortLocal(slot);
            cn.academy.client.render.MagneticManipulationEffects.abortLocal(slot);
        }
        if (ClientRailgun.input(slot, action)) return;
        switch (action) {
            case DOWN -> {
                int request = cn.academy.client.render.MagneticMovementEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.MagneticManipulationEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.ThunderClapEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.MineRayEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.MeltdownerEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.VectorStrikeEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.VectorImpulseEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.StormWingEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.PlasmaCannonEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.FleshRippingEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.ThreateningTeleportEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.ShiftTeleportEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.TargetTeleportEffects.beginLocal(slot);
                if (request == 0) request = cn.academy.client.render.FlashingEffects.beginLocal(slot);
                PacketDistributor.sendToServer(AbilityActionPayload.keyDown(slot, request));
            }
            case UP -> PacketDistributor.sendToServer(AbilityActionPayload.keyUp(slot));
            case ABORT -> PacketDistributor.sendToServer(AbilityActionPayload.keyAbort(slot));
            default -> { }
        }
    }

    @SubscribeEvent
    public static void renderAbilityHand(RenderHandEvent event) {
        cn.academy.client.render.VectorStrikeEffects.renderHand(event);
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Releases reach MouseHandler so an attack held before opening cannot stick.
        if (!ACFrequencyTransmitter.active() || minecraft.screen != null
                || (event.getButton() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getButton() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)) return;
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS
                && event.getButton() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            ACFrequencyTransmitter.rightClickTarget();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void interactionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || (!event.isAttack() && !event.isUseItem())) return;
        AbilityState state = AbilityState.load(minecraft.player);
        if (event.isUseItem() && (minecraft.player.getMainHandItem().is(ACItems.COIN.get())
                || minecraft.player.getMainHandItem().is(ACItems.SILBARN.get())
                || minecraft.player.getMainHandItem().is(ACItems.MAG_HOOK.get()))) return;
        if (state.active() && !state.interfered()) {
            boolean claimed = false;
            for (int slot = 0; slot < ACKeyMappings.SLOTS.length; slot++) {
                if (AbilityRegistry.skill(state.controlSkill(slot)) == null) continue;
                var vanillaKey = event.isAttack() ? minecraft.options.keyAttack : minecraft.options.keyUse;
                boolean selectedKey = slot == SkillSelection.selected(state) && ACKeyMappings.CAST_SKILL.same(vanillaKey);
                boolean directKey = SkillKeyBindings.keys(state.controlSkill(slot)).contains(vanillaKey.getKey())
                        && SkillKeyBindings.down(state.controlSkill(slot));
                if (selectedKey || directKey) {
                    claimed = true;
                    break;
                }
            }
            if (claimed) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void mouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || event.getScrollDeltaY() == 0) return;
        AbilityState state = AbilityState.load(minecraft.player);
        if (!state.active() || TerminalOverlay.active() || ACFrequencyTransmitter.active()) return;
        long window = minecraft.getWindow().getWindow();
        boolean control = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (!control) for (int slot = 0; slot < SLOT_INPUT.length; slot++) {
            AbilitySkill skill = AbilityRegistry.skill(state.controlSkill(slot));
            if (SLOT_INPUT[slot].held() && skill != null && skill.name().equals("penetrate_teleport")
                    && cn.academy.config.ACHudConfig.USE_MOUSE_WHEEL.get()) {
                cn.academy.client.render.TargetTeleportEffects.wheel(slot,(float)event.getScrollDeltaY());
                PacketDistributor.sendToServer(new cn.academy.network.PenetrationWheelPayload((float) event.getScrollDeltaY()));
                event.setCanceled(true);
                return;
            }
        }
        if (!control) return;
        int previous = SkillSelection.selected(state);
        if (previous >= 0 && SLOT_INPUT[previous].held() && !SkillKeyBindings.down(state.controlSkill(previous)))
            updateSlotInput(previous, false, false);
        SkillSelection.scroll(state, event.getScrollDeltaY() > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    private static int physicalMovement() {
        var options = Minecraft.getInstance().options;
        int movement = 0;
        if (ACKeyMappings.physicalDown(options.keyUp)) movement |= 1;
        if (ACKeyMappings.physicalDown(options.keyDown)) movement |= 2;
        if (ACKeyMappings.physicalDown(options.keyLeft)) movement |= 4;
        if (ACKeyMappings.physicalDown(options.keyRight)) movement |= 8;
        return movement;
    }

    private static void sendMovementInput(int movement, boolean enabled) {
        cn.academy.client.render.StormWingEffects.movement(movement,enabled);
        cn.academy.client.render.FlashingEffects.movement(movement,enabled);
        if (movement == lastMovementInput && enabled == lastMovementEnabled) return;
        lastMovementInput = movement;
        lastMovementEnabled = enabled;
        PacketDistributor.sendToServer(enabled ? AbilityActionPayload.movementInput(movement)
                : AbilityActionPayload.movementAbort(movement));
    }

    static void resetInputState() {
        ClientRailgun.reset();
        cn.academy.client.render.ACKeyHintHud.reset();
        ACCPBarHud.reset();
        for (AbilityKeyInput input : SLOT_INPUT) input.reset();
        SkillSelection.reset();
        ACTIVATE_INPUT.reset();
        TERMINAL_INPUT.reset();
        activateHeld = false;
        activateTicks = 0;
        // A new player needs an initial movement packet, even when the bits are unchanged.
        lastMovementInput = -1;
        lastMovementEnabled = false;
        requireControlFlush = false;
        abilityKeyBindings = null;
    }

    private static void abortHeldSlots() {
        var localPlayer = Minecraft.getInstance().player;
        var state = localPlayer == null ? null : AbilityState.load(localPlayer);
        cn.academy.client.render.VectorImpulseEffects.endLocal(-1,true);
        cn.academy.client.render.PlasmaCannonEffects.endLocal(-1,true);
        cn.academy.client.render.FleshRippingEffects.endLocal(-1,true);
        cn.academy.client.render.ThreateningTeleportEffects.endLocal(-1,true);
        cn.academy.client.render.ShiftTeleportEffects.endLocal(-1,true);
        cn.academy.client.render.TargetTeleportEffects.endLocal(-1,true);
        cn.academy.client.render.VectorStrikeEffects.endLocal(-1,true);
        cn.academy.client.render.MeltdownerEffects.endLocal(-1,true);
        cn.academy.client.render.IntensifyEffects.abortLocal(-1);
        cn.academy.client.render.ThunderClapEffects.endLocal(-1);
        cn.academy.client.render.MagneticMovementEffects.endLocal(-1);
        cn.academy.client.render.MineRayEffects.endLocal(-1);
        cn.academy.client.render.MagneticManipulationEffects.abortLocal(-1);
        ClientRailgun.abort();
        ACCPBarHud.numbersHeld(false);
        ACTIVATE_INPUT.reset();
        activateHeld = false;
        activateTicks = 0;
        abortMovementInput();
        for (int slot = 0; slot < SLOT_INPUT.length; slot++) {
            boolean down = state != null && (SkillKeyBindings.down(state.controlSkill(slot))
                    || slot == SkillSelection.selected(state) && ACKeyMappings.physicalDown(ACKeyMappings.CAST_SKILL));
            if (SLOT_INPUT[slot].update(down, false) == AbilityKeyInput.Action.ABORT)
                PacketDistributor.sendToServer(AbilityActionPayload.keyAbort(slot));
        }
    }

    private static void abortMovementInput() {
        sendMovementInput(physicalMovement(), false);
    }

    @SubscribeEvent
    public static void abilityDeactivated(cn.academy.event.ability.AbilityDeactivateEvent event) {
        if (event.player == Minecraft.getInstance().player) {
            cn.academy.client.render.ACKeyHintHud.reset();
            abortHeldSlots(); ClientRailgun.rebuild();
        }
    }

    @SubscribeEvent
    public static void presetUpdated(cn.academy.event.ability.PresetUpdateEvent event) {
        if (event.player == Minecraft.getInstance().player) { abortHeldSlots(); ClientRailgun.rebuild(); }
    }

    @SubscribeEvent
    public static void presetSwitched(cn.academy.event.ability.PresetSwitchEvent event) {
        if (event.player == Minecraft.getInstance().player) {
            abortHeldSlots();
            ClientRailgun.rebuild();
            ACCPBarHud.presetSwitched();
        }
    }

    @SubscribeEvent
    public static void flushControls(cn.academy.event.ability.FlushControlEvent event) {
        ClientRailgun.rebuild();
        requireControlFlush = true;
    }

    @SubscribeEvent
    public static void configModified(cn.academy.event.ConfigModifyEvent event) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.FlushControlEvent());
    }

    @SubscribeEvent
    public static void clearServerPolicy(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        cn.academy.config.ACGeneralConfig.clearRemote();
        cn.academy.crafting.MachineRecipes.invalidate();
    }

    @SubscribeEvent
    public static void throwLocalCoin(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        ClientRailgun.throwCoin(event);
    }

    @SubscribeEvent
    public static void informRailgunCoin(cn.academy.event.CoinThrowEvent event) {
        ClientRailgun.onThrow(event);
    }

    @SubscribeEvent
    public static void renderLevelEffects(RenderLevelStageEvent event) {
        ACVisualEffects.render(event);
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void intensifyHudPre(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        intensifyHud(event);
    }
    @SubscribeEvent
    public static void intensifyHudPost(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post event) {
        intensifyHud(event);
    }
    private static void intensifyHud(net.neoforged.neoforge.client.event.RenderGuiLayerEvent event) {
        if(event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR))
            cn.academy.client.render.VectorDefenseEffects.renderHud(event.getGuiGraphics());
        // The old parent listener drew both phases; NeoForge requires concrete event subscriptions.
        if (event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_BAR))
            cn.academy.client.render.IntensifyEffects.renderHud(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        GuiGraphics gui = event.getGuiGraphics();
        ACNotifications.render(gui, minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        ACMediaPlayer.renderHud(gui, minecraft);
        ACFrequencyTransmitter.render(gui, minecraft);
        TerminalOverlay.render(gui, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        AbilityState state = AbilityState.load(minecraft.player);
        if (!state.hasCategory()) return;
        var contexts = minecraft.player.getPersistentData().getCompound("academy:ability_contexts");
        float consumption = 0;
        boolean special = cn.academy.client.render.FlashingEffects.controlsActive();
        for (String key : contexts.getAllKeys()) {
            var context = contexts.getCompound(key);
            String skill = context.getString("Skill");
            special |= skill.equals("vecmanip.storm_wing")
                    || skill.equals("vecmanip.vec_deviation") || skill.equals("vecmanip.vec_reflection");
            if (consumption == 0) consumption = context.getFloat("ConsumptionHint");
        }
        boolean held = java.util.Arrays.stream(SLOT_INPUT).anyMatch(AbilityKeyInput::held);
        String hint = special ? "endspecial" : held ? "endskill" : null;
        if (hint != null) hint = "[" + cn.academy.client.render.ACKeyHintHud.keyLabel(ACKeyMappings.ACTIVATE) + "]: "
                + Component.translatable("ac.activate_key." + hint + ".desc").getString();
        ACCPBarHud.render(gui, state, hint, consumption);
        cn.academy.client.render.ACKeyHintHud.render(gui, minecraft, state);
    }

    private ACClientEvents() {}
}
