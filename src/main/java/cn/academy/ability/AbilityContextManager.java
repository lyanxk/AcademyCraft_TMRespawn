package cn.academy.ability;

import cn.academy.network.AbilityContextSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative reconstruction of the 1.12.2 Context/KeyDelegate lifecycle.
 * Key-down creates a context, server ticks own all consumption, and key-up either performs or terminates it.
 */
public final class AbilityContextManager {
    private enum Mode { INSTANT, HOLD, CONTINUOUS, TOGGLE }

    private static final Map<UUID, Map<Integer, Session>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> MOVEMENT_INPUT = new HashMap<>();
    private static final Map<UUID, Boolean> MOVEMENT_ENABLED = new HashMap<>();
    private record StartupKey(UUID player, int slot) {}
    private static final java.util.Set<StartupKey> STARTING_SLOTS = new java.util.HashSet<>();

    private static final class Session {
        final int slot;
        final AbilitySkill skill;
        final Mode mode;
        final float initialExperience;
        final cn.academy.ability.vanilla.teleporter.FlashingInput flashingInput =
                new cn.academy.ability.vanilla.teleporter.FlashingInput();
        boolean terminated;
        boolean starting, endRequested, abortRequested, performRequested, startRejected;
        int ticks;
        float value;
        cn.academy.ability.vanilla.electromaster.skill.ChargingContext charging;
        cn.academy.ability.vanilla.electromaster.skill.IntensifyEffectContext intensify;
        cn.academy.ability.vanilla.electromaster.skill.ThunderClapContext thunder;
        cn.academy.ability.vanilla.electromaster.skill.MagneticMovementContext magnetic;
        cn.academy.ability.vanilla.electromaster.skill.MagneticManipulationContext manipulation;
        cn.academy.ability.vanilla.meltdowner.skill.MineRayContext mining;
        cn.academy.ability.vanilla.meltdowner.skill.ElectronCloudContext electrons;
        cn.academy.ability.vanilla.meltdowner.skill.MeltdownerContext meltdowner;
        cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext vectorDefense;
        cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext vectorStrike;
        cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext vectorImpulse;
        cn.academy.ability.vanilla.vecmanip.skill.StormWingContext stormWing;
        cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext plasma;
        cn.academy.ability.vanilla.teleporter.skill.FleshRippingContext flesh;
        cn.academy.ability.vanilla.teleporter.skill.ThreateningTeleportContext threatening;
        cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext shift;
        cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext targetTeleport;
        cn.academy.ability.vanilla.teleporter.skill.FlashingContext flashing;

        Session(int slot, AbilitySkill skill, Mode mode, float value, float experience) {
            this.slot = slot;
            this.skill = skill;
            this.mode = mode;
            this.value = value;
            initialExperience = experience;
        }
    }

    public static void keyDown(ServerPlayer player, AbilityState state, int slot) {
        keyDown(player, state, slot, 0);
    }
    public static void keyDown(ServerPlayer player, AbilityState state, int slot, int clientRequestId) {
        if (slot < 0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS) return;
        AbilitySkill skill = AbilityRegistry.skill(state.controlSkill(slot));
        if (skill == null) {
            AbilityManager.deny(player, Component.translatable("ac.ability.empty_preset", slot + 1));
            return;
        }
        // Railgun's delegate lives on the client; generic key packets must not create a second server charge.
        if (skill.name().equals("railgun")) return;
        Mode mode = mode(skill.name());
        if (skill.name().equals("location_teleport")) {
            AbilityManager.usePreset(player, state, slot);
            return;
        }
        if (mode == Mode.INSTANT) {
            performInstant(player, state, skill);
            return;
        }

        int contextSlot = skill.name().equals("flashing") ? AbilityContextSyncPayload.FLASHING_CONTEXT : slot;
        // Only a synchronous listener can reenter startup: wait for this slot's matching end.
        // Other slots may still start independently, as separate original Context instances did.
        if (STARTING_SLOTS.contains(new StartupKey(player.getUUID(), contextSlot))) return;
        Map<Integer, Session> sessions = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (Session existing : sessions.values().toArray(Session[]::new)) {
            if (!isCurrent(player, existing)) continue;
            if (existing.slot == contextSlot || !existing.skill.id().equals(skill.id())) continue;
            if (mode == Mode.TOGGLE) {
                sessions.remove(existing.slot, existing);
                terminate(player, state, existing, false, false);
                cleanup(player);
            } else AbilityManager.deny(player, Component.translatable("ac.ability.context.active"));
            return;
        }
        Session old = sessions.get(contextSlot);
        if (old != null) {
            if (mode == Mode.TOGGLE && old.skill.id().equals(skill.id())) {
                sessions.remove(contextSlot, old);
                terminate(player, state, old, false, false);
                cleanup(player);
                return;
            }
            sessions.remove(contextSlot, old);
            terminate(player, state, old, true, false);
            sessions = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
            if (sessions.containsKey(contextSlot)) return; // A termination listener installed a replacement.
        }

        if (!skill.name().equals("mag_movement") && !skill.name().equals("mag_manip") && !AbilityExecutor.validate(player, skill)) {
            cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.StormWingContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.FleshRippingContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.ThreateningTeleportContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.reject(player,skill,contextSlot,clientRequestId);
            AbilityManager.deny(player, Component.translatable("ac.ability.missing_reagent", skill.displayName()));
            cleanup(player);
            return;
        }
        if (!state.canStartContext(skill)) {
            cn.academy.ability.vanilla.teleporter.skill.FlashingContext.reject(player,skill,slot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.StormWingContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.FleshRippingContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.ThreateningTeleportContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext.reject(player,skill,contextSlot,clientRequestId);
            cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.reject(player,skill,contextSlot,clientRequestId);
            denyUse(player, state, skill);
            cleanup(player);
            return;
        }

        float exp = state.experience(skill.id());
        if(skill.name().equals("flashing")) {
            Session session=new Session(contextSlot,skill,mode,exp,exp);
            session.flashing=new cn.academy.ability.vanilla.teleporter.skill.FlashingContext(player,state,skill,slot,Math.max(0,clientRequestId));
            if (!startSession(player, state, session, () -> session.flashing.start(state))) return;
            int bits=MOVEMENT_INPUT.getOrDefault(player.getUUID(),0);
            if(MOVEMENT_ENABLED.getOrDefault(player.getUUID(),true))session.flashingInput.update(bits);
            else session.flashingInput.suspend(bits);
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.targetTeleport=new cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.targetTeleport.start();return true;});
            syncSession(player,state,session);return;
        }
        if(skill.name().equals("shift_tp")) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.shift=new cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.shift.start();return true;});
            syncSession(player,state,session);return;
        }
        if(skill.name().equals("threatening_teleport")) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.threatening=new cn.academy.ability.vanilla.teleporter.skill.ThreateningTeleportContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.threatening.start();return true;});
            syncSession(player,state,session);return;
        }
        if(skill.name().equals("flesh_ripping")) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.flesh=new cn.academy.ability.vanilla.teleporter.skill.FleshRippingContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.flesh.start();return true;});
            syncSession(player,state,session);return;
        }
        if(skill.name().equals("plasma_cannon")) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.plasma=new cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.plasma.start(state);return true;});
            syncSession(player,state,session);return;
        }
        if(skill.name().equals("storm_wing")) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.stormWing=new cn.academy.ability.vanilla.vecmanip.skill.StormWingContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.stormWing.start();return true;});
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.vectorImpulse=new cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            if(!startSession(player,state,session,() -> {session.vectorImpulse.start();return true;}))return;
            if(skill.name().equals("blood_retro")) {
                try {session.vectorImpulse.releaseWithoutClient(state);}
                finally {terminate(player,state,session,false,false);cleanup(player);}
                return;
            }
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.vectorStrike=new cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            if(!startSession(player,state,session,() -> {session.vectorStrike.start();return true;}))return;
            if(skill.name().equals("ground_shock")) {
                try {session.vectorStrike.release(state,0);}
                finally {terminate(player,state,session,false,false);cleanup(player);}
                return;
            }
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.vectorDefense=new cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext(player,state,skill);
            startSession(player,state,session,() -> {session.vectorDefense.start(state);return true;});
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.meltdowner.skill.MeltdownerContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.meltdowner=new cn.academy.ability.vanilla.meltdowner.skill.MeltdownerContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,() -> {session.meltdowner.start(state);return true;});
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.meltdowner.skill.ElectronCloudContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.electrons=new cn.academy.ability.vanilla.meltdowner.skill.ElectronCloudContext(player,state,skill);
            startSession(player,state,session,() -> {session.electrons.start(state);return true;});
            syncSession(player,state,session);return;
        }
        if(cn.academy.ability.vanilla.meltdowner.skill.MineRayContext.handles(skill)) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.mining=new cn.academy.ability.vanilla.meltdowner.skill.MineRayContext(player,state,skill,contextSlot,Math.max(0,clientRequestId));
            if (startSession(player,state,session,() -> {session.mining.start(state);return true;})) {
                syncSession(player,state,session);
                AbilityExecutor.startContext(player,state,skill);
            }
            return;
        }
        if (skill.name().equals("mag_manip")) {
            Session session=new Session(contextSlot,skill,mode,0,exp);
            session.manipulation=new cn.academy.ability.vanilla.electromaster.skill.MagneticManipulationContext(
                    player,state,skill,contextSlot,Math.max(0,clientRequestId));
            startSession(player,state,session,session.manipulation::start);
            syncSession(player,state,session);
            return;
        }
        if (skill.name().equals("mag_movement")) {
            Session session = new Session(contextSlot, skill, mode, 0, exp);
            session.magnetic = new cn.academy.ability.vanilla.electromaster.skill.MagneticMovementContext(
                    player, state, skill, contextSlot, Math.max(0, clientRequestId));
            startSession(player,state,session,() -> session.magnetic.start(state));
            syncSession(player,state,session);
            return;
        }
        float[] initial = initialCost(skill.name(), exp);
        var charging = skill.name().equals("charging")
                ? new cn.academy.ability.vanilla.electromaster.skill.ChargingContext(player, state, skill) : null;
        var intensify = skill.name().equals("body_intensify")
                ? new cn.academy.ability.vanilla.electromaster.skill.IntensifyEffectContext(player, contextSlot) : null;
        var thunder = skill.name().equals("thunder_clap")
                ? new cn.academy.ability.vanilla.electromaster.skill.ThunderClapContext(player, state, skill) : null;
        Session session = new Session(contextSlot, skill, mode, 0, exp);
        session.charging = charging;
        session.intensify = intensify;
        session.thunder = thunder;
        if (startSession(player,state,session,() -> {
            if (thunder != null) thunder.start(contextSlot, Math.max(0, clientRequestId)); // Original effect precedes unchecked payment.
            if (!state.consumeRaw(skill, initial[0], initial[1], player.isCreative()) && thunder == null) {
                session.startRejected = true;
                AbilityManager.deny(player, Component.translatable("ac.ability.insufficient_cp"));
                return false;
            }
            if (charging != null) charging.onStarted(state);
            if (intensify != null) intensify.start();
            AbilityExecutor.startContext(player, state, skill);
            return true;
        })) syncSession(player, state, session);
    }

    public static void keyUp(ServerPlayer player, AbilityState state, int slot, boolean abort) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        Session session = sessions.get(slot);
        if (session == null || session.starting || session.mode == Mode.TOGGLE) return;
        if(session.targetTeleport!=null && !abort) {
            if(session.targetTeleport.request!=0)return;
            session.targetTeleport.release(state,session.targetTeleport.selected());
        }
        if(session.shift!=null && !abort)session.shift.release(state);
        if(session.threatening!=null && !abort)session.threatening.release(state);
        if(session.flesh!=null && !abort)session.flesh.release(state);
        if(session.plasma!=null && !abort) {
            if(session.plasma.request!=0)return;
            if(session.plasma.releaseWithoutClient(state))return;
        }
        if(session.vectorImpulse!=null && !abort) {
            if(session.vectorImpulse.request!=0)return;
            session.vectorImpulse.releaseWithoutClient(state);
        }
        if(session.vectorStrike!=null && !abort) {
            if(session.vectorStrike.request!=0)return; // The dedicated packet carries the original client tick count.
            if(session.vectorStrike.release(state,session.vectorStrike.ticks()))return;
        }
        if(session.meltdowner!=null && !abort && session.meltdowner.release(state))return;
        sessions.remove(slot, session);
        terminate(player, state, session, abort, false);
        cleanup(player);
    }

    public static void endVectorDefense(ServerPlayer player,int context) {
        var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;
        var state=AbilityState.load(player);
        for(var session:sessions.values().toArray(Session[]::new))if(session.vectorDefense!=null && session.vectorDefense.id==context) {
            sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);
            cn.academy.network.ACNetwork.sync(player,state);return;
        }
    }

    public static void vectorStrikeInput(ServerPlayer player,cn.academy.network.VectorStrikeInputPayload packet) {
        if(packet.request()<=0)return;
        var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;var state=AbilityState.load(player);
        for(var session:sessions.values().toArray(Session[]::new))if(session.vectorStrike!=null && session.vectorStrike.request==packet.request()) {
            if(!packet.abort() && packet.ticks()>=0 && session.vectorStrike.release(state,packet.ticks()))return;
            sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);
            cn.academy.network.ACNetwork.sync(player,state);return;
        }
    }
    public static void vectorImpulseInput(ServerPlayer player,cn.academy.network.VectorImpulseInputPayload packet) {
        if(packet.request()<0)return;var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;var state=AbilityState.load(player);
        for(var session:sessions.values().toArray(Session[]::new))if(session.vectorImpulse!=null && session.vectorImpulse.request==packet.request()) {
            if(!packet.abort())session.vectorImpulse.perform(state,packet.target());
            sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);cn.academy.network.ACNetwork.sync(player,state);return;
        }
    }
    public static void plasmaInput(ServerPlayer player,cn.academy.network.PlasmaCannonInputPayload packet) {
        if(packet.request()<=0)return;var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;var state=AbilityState.load(player);
        for(var session:sessions.values().toArray(Session[]::new))if(session.plasma!=null && session.plasma.request==packet.request()) {
            if(packet.abort()) {
                sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);
            } else {session.plasma.perform(state);syncSession(player,state,session);}
            cn.academy.network.ACNetwork.sync(player,state);return;
        }
    }
    public static void stormWingInput(ServerPlayer player,cn.academy.network.StormWingInputPayload packet) {
        if(packet.request()<0)return;var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;var state=AbilityState.load(player);
        for(var session:sessions.values().toArray(Session[]::new))if(session.stormWing!=null && session.stormWing.request==packet.request()) {
            if(packet.phase()==cn.academy.network.StormWingPayload.END) {
                sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);cn.academy.network.ACNetwork.sync(player,state);
            } else {session.stormWing.input(packet.phase(),packet.applying());syncSession(player,state,session);}
            return;
        }
    }

    public static void targetTeleportInput(ServerPlayer player,cn.academy.network.TargetTeleportInputPayload packet) {
        if(packet.request()<=0)return;
        var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;var state=AbilityState.load(player);
        for(var session:sessions.values().toArray(Session[]::new))if(session.targetTeleport!=null && session.targetTeleport.request==packet.request()) {
            // Bound malformed/network-only values to the legitimate wheel range. Ordinary clients use the original endpoints.
            if(!packet.abort() && (session.targetTeleport.kind==cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.MARK
                    || Float.isFinite(packet.distance()) && packet.distance()>=.5f && packet.distance()<=10+(35-10)*session.initialExperience))
                session.targetTeleport.release(state,packet.distance());
            sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);
            cn.academy.network.ACNetwork.sync(player,state);return;
        }
    }

    public static void mouseWheel(ServerPlayer player, AbilityState state, double delta) {
        // The original useMouseWheel preference gates client input, not server player data.
        if (!Double.isFinite(delta) || delta == 0) return;
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!isCurrent(player, session)) continue;
            if(session.targetTeleport!=null && session.targetTeleport.kind==cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.PENETRATE) {
                session.targetTeleport.wheel((float)delta);return;
            }
            if (session.skill.name().equals("penetrate_teleport")) {
                float max = lerp(10, 35, session.initialExperience);
                float proposed = session.value + (float) delta;
                if (proposed >= .5f && proposed <= max) session.value = proposed;
                return;
            }
        }
    }

    public static void tick(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null || sessions.isEmpty()) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (session.starting || !isCurrent(player, session)) continue;
            String name = session.skill.name();
            boolean flashing = name.equals("flashing");
            if (!player.isAlive() || !session.skill.isEnabled() || (!state.canUse() && !flashing)
                    || !state.learned().contains(session.skill.id())
                    || session.meltdowner==null && session.vectorDefense==null && session.vectorStrike==null && session.vectorImpulse==null && session.stormWing==null && session.plasma==null && session.flesh==null && session.threatening==null && session.shift==null && session.targetTeleport==null && !AbilityExecutor.contextAlive(player, session.skill)) {
                sessions.remove(session.slot, session);
                terminate(player, state, session, true, false);
                continue;
            }
            session.ticks++;
            if(session.flashing!=null) {
                if(session.flashing.request==0 && (!state.canUse() || !player.isCreative() && state.cp()<lerp(13,6,session.initialExperience)))
                    session.flashingInput.cancelActive();
                if(!session.flashing.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.targetTeleport!=null) {
                session.targetTeleport.tick();if(session.ticks%3==0)syncSession(player,state,session);continue;
            }
            if(session.shift!=null) {
                if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.threatening!=null) {
                if(!session.threatening.tick()){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.flesh!=null) {
                if(!session.flesh.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.plasma!=null) {
                if(!session.plasma.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.stormWing!=null) {
                if(!session.stormWing.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.vectorImpulse!=null) {
                if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.vectorStrike!=null) {
                if(!session.vectorStrike.tick()){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.vectorDefense!=null) {
                if(!session.vectorDefense.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.meltdowner!=null) {
                if(!session.meltdowner.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.electrons!=null) {
                if(!session.electrons.tick(state)){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.mining!=null) {
                boolean paid=session.mining.tick(state);
                AbilityExecutor.tickContext(player,state,session.skill,session.ticks,0,0);
                if(!paid){sessions.remove(session.slot, session);terminate(player,state,session,true,false);}
                else if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if(session.manipulation!=null) {
                session.manipulation.tick();
                if(session.ticks%3==0)syncSession(player,state,session);
                continue;
            }
            if (session.magnetic != null) {
                if (!session.magnetic.tick(state)) {
                    sessions.remove(session.slot, session);
                    terminate(player, state, session, true, false);
                } else if (session.ticks % 3 == 0) syncSession(player, state, session);
                continue;
            }
            if (session.charging != null) {
                if (!session.charging.tick(state)) {
                    sessions.remove(session.slot, session);
                    terminate(player, state, session, true, false);
                } else {
                    if (session.ticks % 3 == 0) syncSession(player, state, session);
                }
                continue;
            }
            if (session.thunder != null) {
                if (session.thunder.tick(state)) {
                    sessions.remove(session.slot, session);
                    terminate(player, state, session, false, session.thunder.canPerform());
                } else {
                    if (session.ticks % 3 == 0) syncSession(player, state, session);
                }
                continue;
            }
            float exp = name.equals("body_intensify") ? session.initialExperience : state.experience(session.skill.id());
            float cp = tickCp(name, exp, session.ticks);
            float overload = tickOverload(name, exp, session.ticks);
            if ((cp > 0 || overload > 0) && !state.consumeRaw(session.skill, cp, overload, player.isCreative())) {
                sessions.remove(session.slot, session);
                boolean autoPerform = autoPerforms(name, session.ticks, exp);
                terminate(player, state, session, !autoPerform, autoPerform);
                continue;
            }

            if (!isCurrent(player, session)) continue;
            int currentMovement = movementDirection(player);
            AbilityExecutor.tickContext(player, state, session.skill, session.ticks,
                    currentMovement, session.value);
            if (session.ticks % 3 == 0) syncSession(player, state, session);

            if (!isCurrent(player, session)) continue;
            int limit = lifetime(name, exp);
            if (limit > 0 && session.ticks >= limit) {
                sessions.remove(session.slot, session);
                boolean perform = autoPerforms(name, session.ticks, exp);
                if (name.equals("scatter_bomb") && session.ticks >= 200 && !player.isCreative())
                    player.hurt(player.damageSources().magic(), 6);
                terminate(player, state, session, !perform, perform);
            }
        }
        cleanup(player);
    }

    public static boolean terminateToggleContexts(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return false;
        boolean terminated = false;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!isCurrent(player, session)) continue;
            if (session.mode == Mode.TOGGLE) {
                sessions.remove(session.slot, session);
                terminate(player, state, session, false, false);
                terminated = true;
            }
        }
        cleanup(player);
        return terminated;
    }

    public static void abortAll(ServerPlayer player, AbilityState state) {
        MOVEMENT_INPUT.remove(player.getUUID());
        MOVEMENT_ENABLED.remove(player.getUUID());
        abortContexts(player, state);
    }

    public static void categoryChanged(ServerPlayer player, AbilityState state) {
        // Like preset changes, a category change does not release the user's physical movement keys.
        controlsDisabled(player, state);
    }

    public static void controlsDisabled(ServerPlayer player, AbilityState state) {
        // Only disconnect/death or an explicit input-abort packet clears physical movement.
        abortContexts(player, state);
        AbilityExecutor.clearTransientDefenses(player);
    }

    public static boolean abortHeldContexts(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null || sessions.isEmpty()) return false;
        abortContexts(player, state);
        return true;
    }

public static void interferenceStarted(ServerPlayer player, AbilityState state) {
        abortPresetContexts(player, state);
        AbilityExecutor.clearTransientDefenses(player);
    }

    /** ClientRuntime.abortDelegates cancels a flashing direction, not its independent MainContext. */
    public static void presetUpdated(ServerPlayer player, AbilityState state) {
        abortPresetContexts(player, state);
    }

    private static void abortPresetContexts(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!isCurrent(player, session)) continue;
            if (session.skill.name().equals("flashing")) session.flashingInput.cancelActive();
            else {
                sessions.remove(session.slot, session);
                terminate(player, state, session, true, false);
            }
        }
        cleanup(player);
    }

    private static void abortContexts(ServerPlayer player, AbilityState state) {
        Map<Integer, Session> sessions = ACTIVE.remove(player.getUUID());
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) terminate(player, state, session, true, false);
    }

    public static boolean hasContext(ServerPlayer player, String skillName) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        return sessions != null && sessions.values().stream().anyMatch(s -> s.skill.name().equals(skillName));
    }
    public static float absorbLightShield(ServerPlayer player,net.minecraft.world.damagesource.DamageSource source,float damage) {
        return cn.academy.ability.vanilla.meltdowner.skill.MeltdownerContext.absorbFirstShield(source,damage);
    }
    public static void endMeltdowner(ServerPlayer player,int context) {
        var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;
        for(var session:sessions.values().toArray(Session[]::new))if(session.meltdowner!=null && session.meltdowner.id==context) {
            var state=AbilityState.load(player);sessions.remove(session.slot, session);terminate(player,state,session,true,false);
            cleanup(player);cn.academy.network.ACNetwork.sync(player,state);return;
        }
    }

    public static boolean magneticAlive(ServerPlayer player) {
        var sessions = ACTIVE.get(player.getUUID());
        return sessions != null && sessions.values().stream().anyMatch(s -> s.magnetic != null && s.magnetic.alive());
    }

    /** Coordinates are an effects endpoint only; ownership and lifetime come from the server session. */
    public static void magneticUpdate(ServerPlayer player, cn.academy.network.MagneticMovementUpdatePayload payload) {
        var sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!isCurrent(player, session)) continue;
            if (session.magnetic == null || session.magnetic.id != payload.contextId()) continue;
            if (payload.phase() == cn.academy.network.MagneticMovementPayload.END) {
                var state = AbilityState.load(player);
                sessions.remove(session.slot, session);
                terminate(player, state, session, true, false);
                cleanup(player);
                cn.academy.network.ACNetwork.sync(player, state);
            } else session.magnetic.updateEffects(payload.phase(), payload.target());
            return;
        }
    }

    private static void terminate(ServerPlayer player, AbilityState state, Session session,
                                  boolean abort, boolean perform) {
        if (session.terminated) return;
        if (session.starting) {
            session.endRequested = true;
            session.abortRequested |= abort;
            session.performRequested = !session.abortRequested && (session.performRequested || perform);
            return;
        }
        session.terminated = true;
        var current = ACTIVE.get(player.getUUID());
        if (current != null) current.remove(session.slot, session);
        PacketDistributor.sendToPlayer(player, new AbilityContextSyncPayload(session.slot, session.skill.id(),
                session.ticks, 0, AbilityContextSyncPayload.ENDED));
        if (session.startRejected) return; // Generic startup payment failed before any effect was started.
        String name = session.skill.name();
        if(session.flashing!=null) {session.flashing.end(state);return;}
        if(session.targetTeleport!=null) {
            session.targetTeleport.end();if(session.targetTeleport.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.shift!=null) {
            session.shift.end();if(session.shift.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.threatening!=null) {
            session.threatening.end();if(session.threatening.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.flesh!=null) {
            session.flesh.end();if(session.flesh.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.plasma!=null) {
            session.plasma.end();if(session.plasma.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.stormWing!=null) {
            session.stormWing.end(state);AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.vectorImpulse!=null) {
            session.vectorImpulse.end(state);if(session.vectorImpulse.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.vectorStrike!=null) {
            session.vectorStrike.end();if(session.vectorStrike.performed())AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.vectorDefense!=null) {
            session.vectorDefense.end();AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.meltdowner!=null) {
            session.meltdowner.end(state);AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.electrons!=null) {
            session.electrons.end(state);AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.mining!=null) {
            session.mining.end(state);AbilityExecutor.endContext(player,session.skill);
            AbilityManager.completeUse(player,state,session.skill);return;
        }
        if(session.manipulation!=null) {
            if(!abort && session.manipulation.perform(state)) AbilityManager.completeUse(player,state,session.skill);
            session.manipulation.end();return;
        }
        if (session.magnetic != null) {
            session.magnetic.end(state);
            AbilityManager.completeUse(player, state, session.skill);
            return;
        }
        if (session.thunder != null) {
            if (!abort && perform) {
                session.thunder.perform(state);
                AbilityManager.completeUse(player, state, session.skill);
            }
            session.thunder.end();
            AbilityExecutor.endContext(player, session.skill);
            return;
        }
        if (name.equals("body_intensify")) {
            // MSG_END performs without an additional consume(0, 0)/SkillPerform call.
            // Its reward precedes cooldown calculation; generic finishContext reverses that order.
            boolean performed = !abort && cn.academy.ability.vanilla.electromaster.skill.BodyIntensifyRules.canRelease(session.ticks);
            if (performed) {
                AbilityExecutor.executeContext(player, state, session.skill, session.ticks, session.value);
                AbilityManager.completeUse(player, state, session.skill);
            }
            session.intensify.end(performed);
            AbilityExecutor.endContext(player, session.skill);
            return;
        }
        float exp = name.equals("flashing") || name.equals("mark_teleport") || name.equals("penetrate_teleport")
                || name.equals("vec_accel") || name.equals("ground_shock") || name.equals("dir_blast")
                ? session.initialExperience : state.experience(session.skill.id());
        boolean didPerform = false;
        boolean effectiveAction = true;
        // Scatter Bomb fires every accumulated ball whenever its context terminates,
        // including key-abort or CP exhaustion (but never after the caster dies).
        if (name.equals("scatter_bomb") && session.ticks >= 20 && player.isAlive()) perform = true;

        if (!abort && session.mode == Mode.HOLD && !perform) {
            perform = canRelease(name, session.ticks, exp);
            if (name.equals("vec_accel") && exp <= .5f && !hasGroundBelow(player)) perform = false;
        } else if (!abort && session.mode == Mode.CONTINUOUS) {
            perform = name.equals("scatter_bomb") && session.ticks >= 20;
        }

        if (perform) {
            float[] finalCost = releaseCost(name, exp, session.ticks, session.value);
            boolean validFinal = true;
            net.minecraft.world.phys.Vec3 teleportDestination = null;
            if (name.equals("penetrate_teleport")) {
                session.value = (float) AbilityExecutor.penetrateAllowedRange(state, session.skill, session.value);
                var selection = AbilityExecutor.selectPenetratingDestination(player.serverLevel(), player, session.value);
                teleportDestination = selection.position();
                validFinal = selection.available();
                finalCost[0] = (float) (player.position().distanceTo(teleportDestination) * lerp(14, 9, exp));
            } else if (name.equals("mark_teleport")) {
                session.value = (float) AbilityExecutor.markAllowedRange(state, session.skill, session.ticks);
                teleportDestination = AbilityExecutor.markDestination(player.serverLevel(), player, session.value);
                double distance = player.position().distanceTo(teleportDestination);
                validFinal = distance >= 3;
                finalCost[0] = (float) (distance * lerp(12, 4, exp));
            } else if (name.equals("flesh_ripping")) {
                validFinal = AbilityExecutor.hasFleshTarget(player, state, session.skill);
            } else if (name.equals("ground_shock")) {
                validFinal = player.onGround();
            } else if (name.equals("blood_retro")) {
                validFinal = AbilityExecutor.hasBloodRetroTarget(player);
            }
            boolean forced = name.equals("penetrate_teleport") || name.equals("mark_teleport")
                    || name.equals("flesh_ripping");
            if (validFinal && (forced ? state.consumeForce(session.skill, finalCost[0], finalCost[1], player.isCreative())
                    : state.consumeRaw(session.skill, finalCost[0], finalCost[1], player.isCreative()))) {
                if (name.equals("dir_shock")) effectiveAction = AbilityExecutor.hasDirectedShockTarget(player);
                if (teleportDestination != null)
                    AbilityExecutor.executeSelectedTeleport(player, state, session.skill, teleportDestination);
                else AbilityExecutor.executeContext(player, state, session.skill, session.ticks, session.value);
                didPerform = true;
            }
        }

        int cooldown = name.equals("dir_shock") && !effectiveAction ? 0
                : cooldown(name, exp, session.ticks, didPerform);
        float gain = didPerform ? experienceGain(name, session.ticks) : continuousExperience(name, session.ticks, exp);
        if (cooldown > 0 || gain > 0) state.finishContext(session.skill, cooldown, gain);
        if (session.charging != null) session.charging.end();
        AbilityExecutor.endContext(player, session.skill);
        if (didPerform || session.mode == Mode.CONTINUOUS || session.mode == Mode.TOGGLE)
            AbilityManager.completeUse(player, state, session.skill);
    }

    private static void performInstant(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        if (!AbilityExecutor.validate(player, skill)) {
            AbilityManager.deny(player, Component.translatable("ac.ability.missing_reagent", skill.displayName()));
            return;
        }
        if (!state.canStartContext(skill)) {
            denyUse(player, state, skill);
            return;
        }
        if (cn.academy.ability.vanilla.electromaster.skill.ElectricAttackContext.handles(skill)) {
            var context = new cn.academy.ability.vanilla.electromaster.skill.ElectricAttackContext(player, state, skill);
            if (!state.consumeRaw(skill, context.cpCost(), context.overloadCost(), player.isCreative())) {
                AbilityManager.deny(player, Component.translatable("ac.ability.insufficient_cp"));
                return;
            }
            state.finishContext(skill, context.perform(), 0);
            AbilityManager.completeUse(player, state, skill);
            return;
        }
        if (skill.name().equals("mine_detect")) {
            if (new cn.academy.ability.vanilla.electromaster.skill.MineDetectContext(player, state, skill).perform())
                AbilityManager.completeUse(player, state, skill);
            else AbilityManager.deny(player, Component.translatable("ac.ability.insufficient_cp"));
            return;
        }
        if (skill.name().equals("electron_bomb")) {
            cn.academy.ability.vanilla.meltdowner.skill.ElectronBomb.perform(player,state);
            return;
        }
        if(skill.name().equals("ray_barrage")) {
            if(!cn.academy.ability.vanilla.meltdowner.skill.RayBarrage.perform(player,state,skill))
                AbilityManager.deny(player,Component.translatable("ac.ability.insufficient_cp"));
            return;
        }
        float exp = state.experience(skill.id());
        float[] cost = switch (skill.name()) {
            case "ray_barrage" -> cost(lerp(450, 380, exp), lerp(300, 140, exp));
            default -> cost(skill.cpCost(), skill.overload());
        };
        if (!state.consumeRaw(skill, cost[0], cost[1], player.isCreative())) {
            AbilityManager.deny(player, Component.translatable("ac.ability.insufficient_cp"));
            return;
        }
        AbilityExecutor.execute(player, state, skill);
        int cooldown = switch (skill.name()) {
            case "ray_barrage" -> Math.round(lerp(100, 40, exp));
            default -> state.cooldownFor(skill);
        };
        float gain = switch (skill.name()) {
            case "ray_barrage" -> .005f;
            default -> .003f;
        };
        state.finishContext(skill, cooldown, gain);
        AbilityManager.completeUse(player, state, skill);
    }

    private static Mode mode(String name) {
        return switch (name) {
            case "arc_gen", "mine_detect", "thunder_bolt", "electron_bomb", "ray_barrage" -> Mode.INSTANT;
            case "charging", "mag_movement", "scatter_bomb", "light_shield",
                    "mine_ray_basic", "mine_ray_expert", "mine_ray_luck" -> Mode.CONTINUOUS;
            case "flashing", "vec_deviation", "vec_reflection", "storm_wing", "electron_missile" -> Mode.TOGGLE;
            default -> Mode.HOLD;
        };
    }

    private static float[] initialCost(String name, float exp) {
        return switch (name) {
            case "charging" -> cost(0, lerp(65, 48, exp));
            case "scatter_bomb" -> cost(0, lerp(80, 60, exp));
            case "light_shield" -> cost(0, lerp(110, 60, exp));
            case "body_intensify" -> cost(0, lerp(200, 120, exp));
            case "thunder_clap" -> cost(0, lerp(390, 252, exp));
            case "meltdowner" -> cost(0, lerp(200, 170, exp));
            case "mine_ray_basic" -> cost(0, lerp(200, 150, exp));
            case "mine_ray_expert" -> cost(0, lerp(300, 200, exp));
            case "mine_ray_luck" -> cost(0, lerp(350, 300, exp));
            case "electron_missile" -> cost(0, 200);
            case "vec_deviation" -> cost(0, lerp(80, 50, exp));
            case "vec_reflection" -> cost(0, 0);
            case "plasma_cannon" -> cost(0, lerp(500, 400, exp));
            case "flashing" -> cost(lerp(80, 60, exp), lerp(250, 180, exp));
            default -> cost(0, 0);
        };
    }

    private static float tickCp(String name, float exp, int ticks) {
        return switch (name) {
            case "scatter_bomb" -> ticks <= 80 ? lerp(3, 6, exp) : 0;
            case "light_shield" -> lerp(9, 4, exp);
            case "body_intensify" -> cn.academy.ability.vanilla.electromaster.skill.BodyIntensifyRules.tickConsumption(ticks, exp);
            case "thunder_clap" -> ticks <= 40 ? lerp(18, 25, exp) : 0;
            case "meltdowner" -> lerp(10, 15, exp);
            case "mine_ray_basic" -> lerp(12, 7, exp);
            case "mine_ray_expert" -> lerp(25, 15, exp);
            case "mine_ray_luck" -> lerp(50, 35, exp);
            case "electron_missile" -> lerp(12, 5, exp);
            case "vec_deviation" -> lerp(18, 7.5f, exp);
            case "vec_reflection" -> lerp(15, 11, exp);
            case "plasma_cannon" -> ticks < Math.ceil(cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.chargeTime(exp)) ? lerp(18, 25, exp) : 0;
            case "storm_wing" -> ticks > Math.round(lerp(70, 30, exp)) ? lerp(40, 25, exp) : 0;
            default -> 0;
        };
    }

    private static float tickOverload(String name, float exp, int ticks) {
        return switch (name) {
            case "vec_reflection" -> lerp(100, 50, exp) / 20;
            case "vec_deviation" -> lerp(.5f, .2f, exp);
            case "storm_wing" -> ticks > Math.round(lerp(70, 30, exp)) ? lerp(10, 7, exp) : 0;
            default -> 0;
        };
    }

    private static float[] releaseCost(String name, float exp, int ticks, float value) {
        return switch (name) {
            case "threatening_teleport" -> cost(lerp(35, 100, exp), lerp(18, 10, exp));
            case "penetrate_teleport" -> cost(value * lerp(14, 9, exp), lerp(80, 50, exp));
            case "mark_teleport" -> cost(Math.min((ticks + 1) * 2, lerp(25, 60, exp)) * lerp(12, 4, exp), lerp(40, 20, exp));
            case "flesh_ripping" -> cost(lerp(130, 270, exp), lerp(60, 50, exp));
            case "shift_tp" -> cost(lerp(260, 320, exp), lerp(40, 30, exp));
            case "jet_engine" -> cost(lerp(60, 50, exp), lerp(170, 140, exp));
            case "dir_shock" -> cost(lerp(50, 100, exp), lerp(18, 12, exp));
            case "ground_shock" -> cost(lerp(80, 150, exp), lerp(15, 10, exp));
            case "vec_accel" -> cost(lerp(240, 160, exp), lerp(30, 15, exp));
            case "dir_blast" -> cost(lerp(160, 200, exp), lerp(50, 30, exp));
            case "blood_retro" -> cost(lerp(280, 350, exp), lerp(55, 40, exp));
            default -> cost(0, 0);
        };
    }

    private static boolean canRelease(String name, int ticks, float exp) {
        return switch (name) {
            case "thunder_clap" -> false; // The 1.12.2 context fires only at tick 60 or on CP exhaustion.
            case "meltdowner" -> ticks >= 20 && ticks <= 100;
            case "dir_shock", "dir_blast" -> ticks > 6 && ticks < 50;
            case "ground_shock" -> ticks >= 0;
            case "plasma_cannon" -> ticks >= Math.ceil(cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.chargeTime(exp));
            default -> true;
        };
    }

    private static int lifetime(String name, float exp) {
        return switch (name) {
            case "scatter_bomb" -> 200;
            case "light_shield" -> Math.round(lerp(120, 180, exp));
            case "body_intensify", "meltdowner" -> 100;
            case "thunder_clap" -> 60;
            case "electron_missile" -> Math.round(lerp(80, 200, exp));
            case "dir_shock", "dir_blast" -> 200;
            case "blood_retro" -> 0;
            case "flashing" -> (int) lerp(60, 150, exp) + 2; // Original checks ticks > max_time before incrementing.
            default -> 0;
        };
    }

    private static boolean autoPerforms(String name, int ticks, float exp) {
        return name.equals("thunder_clap") && ticks >= 40 || name.equals("blood_retro") && ticks >= 30
               ;
    }

    private static int cooldown(String name, float exp, int ticks, boolean performed) {
        return switch (name) {
            case "light_shield" -> Math.round(lerp(2 * ticks, ticks, exp));
            case "mine_ray_basic" -> Math.round(lerp(40, 20, exp));
            case "mine_ray_expert", "mine_ray_luck" -> Math.round(lerp(60, 30, exp));
            case "electron_missile" -> Math.round(lerp(700, 400, exp));
            case "storm_wing" -> Math.round(lerp(30, 10, exp));
            case "flashing" -> (int) lerp(900, 400, exp);
            default -> performed ? switch (name) {
                case "thunder_clap" -> Math.round(ticks * lerp(10, 6, exp));
                case "meltdowner" -> Math.round(lerp(.8f, 1.2f, (Math.min(ticks, 40) - 20) / 20f) * 20 * lerp(15, 7, exp));
                case "threatening_teleport" -> Math.round(lerp(30, 15, exp));
                case "penetrate_teleport" -> (int) lerp(50, 30, exp);
                case "mark_teleport" -> (int) lerp(30, 0, exp);
                case "flesh_ripping" -> Math.round(lerp(90, 40, exp));
                case "shift_tp" -> Math.round(lerp(100, 60, exp));
                case "jet_engine" -> Math.round(lerp(60, 30, exp));
                case "dir_shock" -> Math.round(lerp(60, 20, exp));
                case "ground_shock" -> Math.round(lerp(80, 40, exp));
                case "vec_accel" -> Math.round(lerp(80, 50, exp));
                case "dir_blast" -> Math.round(lerp(80, 50, exp));
                case "blood_retro" -> Math.round(lerp(90, 40, exp));
                case "plasma_cannon" -> Math.round(lerp(1000, 600, exp));
                default -> 0;
            } : 0;
        };
    }

    private static float experienceGain(String name, int ticks) {
        return switch (name) {
            case "thunder_clap" -> .003f;
            case "meltdowner" -> .002f * Math.max(.8f, Math.min(1.2f, .8f + (Math.min(ticks, 40) - 20) / 50f));
            case "flesh_ripping" -> .005f;
            case "threatening_teleport", "penetrate_teleport", "mark_teleport", "shift_tp" -> 0;
            case "jet_engine" -> .004f;
            case "dir_shock" -> 0;
            case "ground_shock" -> 0;
            case "vec_accel", "blood_retro" -> .002f;
            case "dir_blast" -> 0;
            case "plasma_cannon" -> .008f;
            case "scatter_bomb" -> 0;
            default -> .003f;
        };
    }

    private static float continuousExperience(String name, int ticks, float exp) {
        return switch (name) {
            case "mag_movement" -> 0;
            case "storm_wing" -> Math.max(0, ticks - Math.round(lerp(70, 30, exp))) * .00005f;
            default -> 0;
        };
    }

    /** Like old alive.add before MSG_MADEALIVE, with disposal after that callback returns. */
    private static boolean startSession(ServerPlayer player, AbilityState state, Session session,
                                        java.util.function.BooleanSupplier startup) {
        session.starting = true;
        ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(session.slot, session);
        StartupKey key = new StartupKey(player.getUUID(), session.slot);
        STARTING_SLOTS.add(key);
        boolean succeeded = false;
        try {
            succeeded = startup.getAsBoolean();
        } finally {
            session.starting = false;
            STARTING_SLOTS.remove(key);
            if (!succeeded || session.endRequested || !isCurrent(player, session)) {
                terminate(player, state, session, !succeeded || session.abortRequested || (!session.endRequested && !isCurrent(player, session)),
                        succeeded && session.performRequested);
                cleanup(player);
            }
        }
        return isCurrent(player, session);
    }

    private static boolean isCurrent(ServerPlayer player, Session session) {
        var sessions = ACTIVE.get(player.getUUID());
        return !session.terminated && sessions != null && sessions.get(session.slot) == session;
    }

    private static void syncSession(ServerPlayer player, AbilityState state, Session session) {
        if (session.starting || !isCurrent(player, session)) return;
        float exp = state.experience(session.skill.id());
        int target = chargeTarget(session.skill.name(), exp);
        int contextState = target > 0 && session.ticks < target
                ? AbilityContextSyncPayload.CHARGING : AbilityContextSyncPayload.ACTIVE;
        if(session.plasma!=null){target=session.plasma.chargeTicks();contextState=session.plasma.active()?AbilityContextSyncPayload.ACTIVE:AbilityContextSyncPayload.CHARGING;}
        if(session.stormWing!=null)contextState=session.stormWing.active()?AbilityContextSyncPayload.ACTIVE:AbilityContextSyncPayload.CHARGING;
        PacketDistributor.sendToPlayer(player, new AbilityContextSyncPayload(session.slot, session.skill.id(),
                session.ticks, target, contextState, session.vectorImpulse!=null && session.skill.name().equals("vec_accel")
                        ?session.vectorImpulse.consumptionHint():session.skill.name().equals("dir_blast") && state.level()>=5
                        ?200:consumptionHint(session.skill.name(), session.initialExperience)));
    }

    /** Original IConsumptionProvider values are captured when these three contexts are created. */
    private static float consumptionHint(String skill, float experience) {
        return switch (skill) {
            case "vec_accel" -> lerp(240, 160, experience);
            case "ground_shock" -> lerp(80, 150, experience);
            case "dir_blast" -> lerp(160, 200, experience);
            default -> 0;
        };
    }

    private static int chargeTarget(String name, float exp) {
        return switch (name) {
            case "body_intensify" -> 40;
            case "thunder_clap" -> 40;
            case "meltdowner" -> 40;
            case "dir_shock", "dir_blast" -> 6;
            case "ground_shock" -> 0;
            case "vec_accel" -> 0;
            case "blood_retro" -> 0;
            case "plasma_cannon" -> (int)Math.ceil(cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.chargeTime(exp));
            case "storm_wing" -> Math.round(lerp(70, 30, exp));
            default -> 0;
        };
    }

    private static boolean hasGroundBelow(ServerPlayer player) {
        net.minecraft.world.phys.Vec3 start = player.position();
        net.minecraft.world.phys.HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                start, start.add(0, -2, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    public static void updateMovementInput(ServerPlayer player, int bits) {
        updateMovementInput(player, bits, true);
    }

    public static void updateMovementInput(ServerPlayer player, int bits, boolean enabled) {
        if ((bits & 15) == 0) MOVEMENT_INPUT.remove(player.getUUID());
        else MOVEMENT_INPUT.put(player.getUUID(), bits & 15);
        MOVEMENT_ENABLED.put(player.getUUID(), enabled);
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions == null) return;
        AbilityState state = AbilityState.load(player);
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!isCurrent(player, session)) continue;
            if (!session.skill.name().equals("flashing")) continue;
            if(session.flashing==null || session.flashing.request>0)continue;
            if (!enabled || !state.canUse()) {
                session.flashingInput.suspend(bits);
                continue;
            }
            int released = session.flashingInput.update(bits);
            if (released != 0 && session.flashing.perform(state,released)) {
                AbilityManager.completeUse(player, state, session.skill);
                cn.academy.network.ACNetwork.sync(player, state);
            }
        }
    }

    public static void abortMovementInput(ServerPlayer player) {
        updateMovementInput(player, MOVEMENT_INPUT.getOrDefault(player.getUUID(), 0), false);
    }

    public static void flashingInput(ServerPlayer player, cn.academy.network.FlashingInputPayload payload) {
        if(payload.request()<0 || payload.direction()<0 || payload.direction()>4)return;
        var sessions=ACTIVE.get(player.getUUID());if(sessions==null)return;
        var session=sessions.get(AbilityContextSyncPayload.FLASHING_CONTEXT);
        if(session==null || session.flashing==null || session.flashing.request!=payload.request())return;
        var state=AbilityState.load(player);
        if(payload.direction()==0) {
            sessions.remove(session.slot, session);terminate(player,state,session,true,false);cleanup(player);
        } else if(session.flashing.perform(state,payload.direction()))AbilityManager.completeUse(player,state,session.skill);
        state.save(player);cn.academy.network.ACNetwork.sync(player,state);
    }

    static int movementDirection(ServerPlayer player) {
        if (!MOVEMENT_ENABLED.getOrDefault(player.getUUID(), true)) return 0;
        int bits = MOVEMENT_INPUT.getOrDefault(player.getUUID(), 0);
        if ((bits & 1) != 0) return 1;
        if ((bits & 2) != 0) return 2;
        if ((bits & 4) != 0) return 3;
        if ((bits & 8) != 0) return 4;
        return 0;
    }

    private static void denyUse(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        Component reason = state.interfered() ? Component.translatable("ac.ability.interfered")
                : !state.active() ? Component.translatable("ac.ability.not_activated")
                : state.overloadLocked() ? Component.translatable("ac.ability.overload_locked")
                : state.cooldown(skill.id()) > 0
                ? Component.translatable("ac.ability.cooldown", state.cooldown(skill.id()) / 20f)
                : Component.translatable("ac.ability.insufficient_cp");
        AbilityManager.deny(player, reason);
    }

    private static float[] cost(float cp, float overload) { return new float[]{cp, overload}; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }
    private static void cleanup(ServerPlayer player) {
        Map<Integer, Session> sessions = ACTIVE.get(player.getUUID());
        if (sessions != null && sessions.isEmpty()) ACTIVE.remove(player.getUUID());
    }

    private AbilityContextManager() { }
}
