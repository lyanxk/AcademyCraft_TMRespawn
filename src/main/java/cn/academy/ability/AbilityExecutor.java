package cn.academy.ability;

import cn.academy.config.ACConfig;
import cn.academy.ability.vanilla.teleporter.TeleporterGeometry;
import net.minecraft.world.level.portal.DimensionTransition;
import cn.academy.advancement.ACAdvancements;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACSounds;
import cn.academy.registry.ACParticles;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.entity.ACElectronBallEntity;
import cn.academy.network.VisualEffectPayload;
import cn.academy.network.ContextSoundPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Server implementations for every controllable skill shipped by the original mod. */
public final class AbilityExecutor {
    private record JetDash(Vec3 start, Vec3 target, Vec3 velocity, float exp, float oldWalkSpeed, int ticks) {
        JetDash next() { return new JetDash(start, target, velocity, exp, oldWalkSpeed, ticks + 1); }
    }
    private static final Map<UUID, JetDash> JET_DASHES = new HashMap<>();
    private record PlasmaFlight(Vec3 position, Vec3 destination, float exp, int ticks) { }
    private static final Map<UUID, PlasmaFlight> PLASMA_FLIGHTS = new HashMap<>();

    public static void clearTransientDefenses(ServerPlayer player) {
        player.getPersistentData().remove("academy:light_shield_until");
        player.getPersistentData().remove("academy:light_shield_absorb");
        player.getPersistentData().remove("academy:vec_deviation_until");
        player.getPersistentData().remove("academy:vec_reflection_until");
        stopJetDash(player);
        PLASMA_FLIGHTS.remove(player.getUUID());
    }

    public static void tickTransient(ServerPlayer player, AbilityState state) {
        JetDash dash = JET_DASHES.get(player.getUUID());
        if (dash != null) tickJetDash(player, state, dash);
        PlasmaFlight plasma = PLASMA_FLIGHTS.get(player.getUUID());
        if (plasma != null) tickPlasmaFlight(player, plasma);
    }

    private static void tickJetDash(ServerPlayer player, AbilityState state, JetDash dash) {
        JetDash nextDash = dash.next();
        int tick = nextDash.ticks();
        if (tick > 15 || !player.isAlive()) {
            stopJetDash(player);
            return;
        }
        if (player.isPassenger()) player.stopRiding();
        Vec3 from = player.position();
        Vec3 to = dash.start().add(dash.target().subtract(dash.start()).scale(tick / 8f));
        AABB swept = new AABB(from, to).inflate(.8);
        AbilitySkill skill = AbilityRegistry.skill("meltdowner.jet_engine");
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, swept,
                entity -> validTarget(player, entity) && entity.getBoundingBox().inflate(.4).clip(from, to).isPresent())) {
            if (skill != null) meltdownerDamage(player, state, skill, target, lerp(7, 20, dash.exp()));
        }
        player.setPos(to.x, to.y, to.z);
        player.setDeltaMovement(dash.velocity());
        player.hurtMarked = true;
        player.resetFallDistance();
        player.getAbilities().setWalkingSpeed(.07f);
        if (tick % 3 == 0) player.onUpdateAbilities();
        player.serverLevel().sendParticles(ACParticles.MELTDOWNER.get(), player.getX(), player.getY() + .8,
                player.getZ(), 10, .3, .4, .3, .025);
        JET_DASHES.put(player.getUUID(), nextDash);
    }

    private static void tickPlasmaFlight(ServerPlayer player, PlasmaFlight flight) {
        if (!player.isAlive()) {
            PLASMA_FLIGHTS.remove(player.getUUID());
            return;
        }
        Vec3 delta = flight.destination().subtract(flight.position());
        Vec3 next = delta.length() < 1 ? flight.position() : flight.position().add(delta.normalize());
        int ticks = flight.ticks() + 1;
        BlockHitResult collision = player.serverLevel().clip(new ClipContext(flight.position(), next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        VisualEffectPayload visual = new VisualEffectPayload("plasma", next.x, next.y, next.z,
                next.x, next.y, next.z, 1.2f, 0xE0C8E8FF, 3);
        broadcastVisual(player.serverLevel(), player, visual);
        if (collision.getType() == HitResult.Type.BLOCK || ticks >= 240
                || next.distanceTo(flight.destination()) < 1.5) {
            explodePlasma(player, flight.destination(), flight.exp());
            PLASMA_FLIGHTS.remove(player.getUUID());
        } else {
            PLASMA_FLIGHTS.put(player.getUUID(), new PlasmaFlight(next, flight.destination(), flight.exp(), ticks));
        }
    }

    private static Vec3 plasmaChargePosition(ServerPlayer player) {
        return player.getPersistentData().contains("academy:plasma_charge_x")
                ? new Vec3(player.getPersistentData().getDouble("academy:plasma_charge_x"),
                player.getPersistentData().getDouble("academy:plasma_charge_y"),
                player.getPersistentData().getDouble("academy:plasma_charge_z"))
                : cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.chargePosition(player);
    }

    private static void startPlasmaFlight(ServerPlayer player, float exp) {
        Vec3 destination = traceLocation(player.serverLevel(), player, 100);
        PLASMA_FLIGHTS.put(player.getUUID(), new PlasmaFlight(plasmaChargePosition(player), destination, exp, 0));
    }

    private static void explodePlasma(ServerPlayer player, Vec3 destination, float exp) {
        AbilitySkill skill = AbilityRegistry.skill("vecmanip.plasma_cannon");
        ServerLevel level = player.serverLevel();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(destination, destination).inflate(10), entity -> entity == player || validTarget(player, entity))) {
            AbilityContext.of(player, AbilityRegistry.skill("vecmanip.plasma_cannon"))
                    .attack(target, cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.damage(target, exp));
            target.invulnerableTime = 0;
        }
        level.explode(player, destination.x, destination.y, destination.z, cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.explosionPower(exp),
                canDestroy(player, skill) ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        level.sendParticles(ACParticles.VECTOR.get(), destination.x, destination.y, destination.z,
                100, 5, 5, 5, .15);
    }

    private static void startJetDash(ServerPlayer player, float exp) {
        stopJetDash(player);
        Vec3 start = player.position();
        BlockHitResult hit = blockTrace(player.serverLevel(), player, 12);
        Vec3 target = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation()
                : player.getEyePosition().add(player.getLookAngle().scale(12));
        Vec3 velocity = target.subtract(start).scale(1d / 8d);
        JET_DASHES.put(player.getUUID(), new JetDash(start, target, velocity, exp,
                player.getAbilities().getWalkingSpeed(), 0));
    }

    private static void stopJetDash(ServerPlayer player) {
        JetDash dash = JET_DASHES.remove(player.getUUID());
        if (dash == null) return;
        player.getAbilities().setWalkingSpeed(dash.oldWalkSpeed());
        player.onUpdateAbilities();
    }

    public static boolean performLocationTeleport(ServerPlayer player, AbilityState state, int index) {
        AbilitySkill skill = AbilityRegistry.skill("teleporter.location_teleport");
        if (skill == null || index < 0 || index >= state.teleportLocations().size()
                || !state.learned().contains(skill.id())) return false;
        AbilityState.TeleportLocation location = state.teleportLocations().get(index);
        ResourceLocation dimensionId = ResourceLocation.tryParse(location.dimension());
        if (dimensionId == null) return false;
        ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel destinationLevel = player.server.getLevel(dimension);
        if (destinationLevel == null) return false;
        boolean crossDimension = player.level() != destinationLevel;
        Vec3 destination=new Vec3(location.x(),location.y(),location.z());
        float consumptionDistance=(float)Math.sqrt(player.position().distanceToSqr(destination));
        if(!state.consumeLocationTeleport(skill,consumptionDistance,crossDimension,player.isCreative()))return false;
        Vec3 origin = player.position();
        record Companion(Entity entity, Vec3 destination) {}
        var companions = TeleporterGeometry.locationCompanions(player).stream()
                .map(entity -> new Companion(entity, destination.add(entity.position().subtract(origin)))).toList();
        player.stopRiding();
        if (crossDimension) {
            if (player.changeDimension(new DimensionTransition(destinationLevel, destination, player.getDeltaMovement(),
                    player.getYRot(), player.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET
                            .then(DimensionTransition.PLAY_PORTAL_SOUND))) == null) return false;
        } else player.teleportTo(destination.x, destination.y, destination.z);
        // Each selected companion keeps its pre-travel offset. Let native transitions create
        // replacement entities; never reposition the removed source-world instance.
        for (var companion : companions) companion.entity().stopRiding();
        for (var companion : companions) {
            Entity entity = companion.entity();
            if (entity.isRemoved()) continue;
            Vec3 target = companion.destination();
            if (entity.level() != destinationLevel) {
                entity.changeDimension(new DimensionTransition(destinationLevel, target, entity.getDeltaMovement(),
                        entity.getYRot(), entity.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET));
            } else entity.teleportTo(target.x, target.y, target.z);
        }
        state.finishLocationTeleport(skill, consumptionDistance);
        recordTeleport(player, state);
        ACAdvancements.grantLegacySkillUse(player, "teleporter", "location_teleport");
        return true;
    }

    public static boolean validate(ServerPlayer player, AbilitySkill skill) {
        if (skill.name().equals("railgun")) return hasRailgunChargeItem(player);
        if (skill.name().equals("threatening_teleport")) return !player.getMainHandItem().isEmpty();
        if (skill.name().equals("shift_tp")) return cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext.validHand(player);
        if (skill.name().equals("mag_movement")) return cn.academy.ability.vanilla.electromaster.skill.MagneticTarget.trace(player, 25, AbilityState.load(player).experience(skill.id())) != null;
        return true;
    }

    public static boolean hasRailgunChargeItem(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.is(Items.IRON_INGOT) || held.is(Items.IRON_BLOCK);
    }

    private static ACThrownItemEntity targetedThrownItem(ServerLevel level, ServerPlayer player,
                                                           double range, Item item) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        BlockHitResult blockHit = blockTrace(level, player, range);
        double maxDistance = blockHit.getType() == HitResult.Type.BLOCK
                ? start.distanceToSqr(blockHit.getLocation()) : range * range;
        Entity nearest = null;
        double nearestDistance = maxDistance;
        AABB search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1);
        for (Entity entity : level.getEntities(player, search, value -> value.isPickable()
                || value instanceof ACThrownItemEntity)) {
            Optional<Vec3> clipped = entity.getBoundingBox().inflate(.45).clip(start, end);
            if (clipped.isEmpty()) continue;
            double distance = start.distanceToSqr(clipped.get());
            if (distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest instanceof ACThrownItemEntity thrown && thrown.getItem().is(item) && !thrown.isSilbarnHit() ? thrown : null;
    }

    public static void startContext(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        if(cn.academy.ability.vanilla.meltdowner.skill.MineRayContext.handles(skill))return;
        if (skill.name().equals("mag_movement") || skill.name().equals("mag_manip")) return; // Dedicated context owns movement and effects.
        if (skill.name().equals("thunder_clap")) return; // Dedicated client context owns movement, surround and marker.
        if (skill.name().equals("body_intensify")) return; // Dedicated local HUD/loop and fixed-audience release effects.
        if (skill.name().equals("charging")) return; // ChargingContext owns persistent effects and sound.
        if (skill.name().equals("railgun")) return; // Client delegate owns the hand hook and perform request.
        ServerLevel level = player.serverLevel();
        switch (skill.name()) {
            case "meltdowner" -> sound(level, player, ACSounds.get("md.md_charge"));
            case "mine_ray_basic" -> sound(level, player, ACSounds.get("md.mine_basic_startup"));
            case "mine_ray_expert" -> sound(level, player, ACSounds.get("md.mine_expert_startup"));
            case "mine_ray_luck" -> sound(level, player, ACSounds.get("md.mine_luck_startup"));
            case "light_shield" -> sound(level, player, ACSounds.MD_SHIELD.get());
            case "plasma_cannon" -> sound(level, player, ACSounds.get("vecmanip.plasma_cannon"));
            default -> { }
        }
        if (skill.name().equals("electron_missile"))
            level.addFreshEntity(ACElectronBallEntity.createHeld(player, 2));
        if (skill.name().equals("meltdowner"))
            player.getPersistentData().putFloat("academy:meltdowner_walk_speed", player.getAbilities().getWalkingSpeed());
        if (skill.name().equals("blood_retro"))
            player.getPersistentData().putFloat("academy:blood_walk_speed", player.getAbilities().getWalkingSpeed());
        if (skill.name().equals("plasma_cannon")) {
            Vec3 charge = cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.chargePosition(player);
            player.getPersistentData().putDouble("academy:plasma_charge_x", charge.x);
            player.getPersistentData().putDouble("academy:plasma_charge_y", charge.y);
            player.getPersistentData().putDouble("academy:plasma_charge_z", charge.z);
        }
        if (skill.name().equals("storm_wing")) {
            player.getPersistentData().putBoolean("academy:storm_wing_prev_flight", player.getAbilities().mayfly);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
        String loop = contextLoopSound(skill.name());
        if (loop != null) broadcastContextSound(player, loop, true, contextLoopVolume(skill.name()));
        sendSkillVisual(player, skill, player.getEyePosition());
    }

    public static void tickContext(ServerPlayer player, AbilityState state, AbilitySkill skill,
                                   int ticks, int movementDirection, float contextValue) {
        ServerLevel level = player.serverLevel();
        float exp = state.experience(skill.id());
        switch (skill.name()) {
            case "scatter_bomb" -> {
                if (ticks >= 20 && ticks <= 80 && ticks % 10 == 0) {
                    level.addFreshEntity(ACElectronBallEntity.createHeld(player, 1));
                    sendSkillVisual(player, skill, player.getEyePosition());
                }
            }
            case "light_shield" -> {
                player.getPersistentData().putLong("academy:light_shield_until", level.getGameTime() + 3);
                state.addExperience(skill, 1.0e-6f);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(3), e -> validTarget(player, e) && e.invulnerableTime <= 0)) {
                    Vec3 direction = entity.position().subtract(player.position()).normalize();
                    if (direction.dot(player.getLookAngle()) > .5
                            && state.consumeRaw(skill, lerp(50, 30, exp), lerp(5, 3, exp), player.isCreative())) {
                        meltdownerDamage(player, state, skill, entity, lerp(2, 6, exp));
                        state.addExperience(skill, .001f);
                    }
                }
                if (ticks % 6 == 0) sendSkillVisual(player, skill, player.getEyePosition());
            }
            case "electron_missile" -> {
                List<ACElectronBallEntity> balls = missileBalls(player);
                if (ticks % 10 == 0 && balls.size() < 5) {
                    level.addFreshEntity(ACElectronBallEntity.createHeld(player, 2));
                    balls = missileBalls(player);
                }
                if (ticks % 8 == 0 && !balls.isEmpty()) {
                    LivingEntity target = nearestLivingAround(level, player, lerp(5, 13, exp));
                    if (target != null && state.consumeRaw(skill, lerp(60, 25, exp), lerp(9, 4, exp), player.isCreative())) {
                        ACElectronBallEntity ball = balls.get(level.random.nextInt(balls.size()));
                        ball.fireAt(player, target.getEyePosition(), lerp(10, 18, exp));
                        state.addExperience(skill, .001f);
                    }
                }
                if (ticks % 2 == 0) level.sendParticles(ACParticles.MELTDOWNER.get(), player.getX(),
                        player.getY() + .5, player.getZ(), 2, .8, .7, .8, .025);
            }
            case "vec_deviation" -> {
                player.getPersistentData().putLong("academy:vec_deviation_until", level.getGameTime() + 3);
                deviateProjectiles(level, player, state, skill, exp);
                if (ticks % 6 == 0) sendSkillVisual(player, skill, player.getEyePosition());
            }
            case "vec_reflection" -> {
                player.getPersistentData().putLong("academy:vec_reflection_until", level.getGameTime() + 3);
                reflectProjectiles(level, player, state, skill, exp);
                if (ticks % 6 == 0) sendSkillVisual(player, skill, player.getEyePosition());
            }
            case "storm_wing" -> {
                int charge = Math.round(lerp(70, 30, exp));
                if (ticks % 5 == 0) sendSkillVisual(player, skill, player.getEyePosition());
                if (exp < .15f) breakSoftBlocksAround(level, player, skill);
                if (ticks > charge) {
                    Vec3 forward = player.getLookAngle().normalize();
                    Vec3 flat = Vec3.directionFromRotation(0, player.getYRot());
                    Vec3 right = new Vec3(-flat.z, 0, flat.x).normalize();
                    Vec3 input = switch (movementDirection) {
                        case 1 -> forward;
                        case 2 -> forward.scale(-1);
                        case 3 -> right.scale(-1);
                        case 4 -> right;
                        default -> Vec3.ZERO;
                    };
                    Vec3 old = player.getDeltaMovement();
                    if (input.lengthSqr() > .01) {
                        if (player.isPassenger()) player.stopRiding();
                        double speed = (exp < .45f ? .7 : 1.2) * lerp(2, 3, exp);
                        Vec3 desired = input.normalize().scale(speed);
                        player.setDeltaMovement(approach(old.x, desired.x, .16), approach(old.y, desired.y, .16),
                                approach(old.z, desired.z, .16));
                    } else {
                        BlockHitResult ground = level.clip(new ClipContext(player.position().add(0, .5, 0),
                                player.position().add(0, -.3, 0), ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE, player));
                        player.setDeltaMovement(old.x,
                                ground.getType() == HitResult.Type.MISS ? old.y + .078 : .1, old.z);
                    }
                    player.hurtMarked = true;
                    player.resetFallDistance();
                    if (ticks == charge + 1 && exp >= .999f) {
                        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(6), e -> e != player)) {
                            Vec3 push = entity.getEyePosition().subtract(player.position()).normalize()
                                    .scale(.5 + level.random.nextDouble() * .5);
                            entity.setDeltaMovement(push);
                            entity.hurtMarked = true;
                        }
                    }
                }
            }
            case "meltdowner", "plasma_cannon" -> {
                if (ticks % 5 == 0) {
                    if (skill.name().equals("plasma_cannon")) {
                        Vec3 charge = plasmaChargePosition(player);
                        broadcastVisual(level, player, new VisualEffectPayload("plasma", charge.x, charge.y, charge.z,
                                charge.x, charge.y, charge.z, 1.5f, 0xE0C8E8FF, 7));
                    } else sendSkillVisual(player, skill, player.getEyePosition());
                }
                if (skill.name().equals("meltdowner")) {
                    player.getAbilities().setWalkingSpeed(Math.max(.001f, .1f - ticks * .001f));
                    if (ticks % 5 == 0) player.onUpdateAbilities();
                }
                if (skill.name().equals("plasma_cannon") && ticks == Math.ceil(cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext.chargeTime(exp)))
                    sound(level, player, ACSounds.get("vecmanip.plasma_cannon_t"));
            }
            case "blood_retro" -> {
                player.getAbilities().setWalkingSpeed(lerp(.1f, .007f, Math.min(1, ticks / 20f)));
                if (ticks % 5 == 0) player.onUpdateAbilities();
            }
            case "penetrate_teleport" -> {
                if (ticks % 3 == 0) {
                    double allowed = penetrateAllowedRange(state, skill, contextValue);
                    var target = selectPenetratingDestination(level, player, allowed);
                    sendContextMarker(player, target.position().add(0, player.getEyeHeight(), 0),
                            target.available() ? 0xC8D9EDFF : 0xC8FF5A5A);
                }
            }
            case "mark_teleport" -> {
                if (ticks % 3 == 0) sendContextMarker(player,
                        markDestination(level, player, markAllowedRange(state, skill, ticks)), 0xC8D9EDFF);
            }
            case "threatening_teleport" -> {
                if (ticks % 3 == 0) {
                    LivingEntity target = TeleporterGeometry.threateningTarget(player, lerp(8, 15, exp));
                    sendContextMarker(player, target == null ? traceLocation(level, player, lerp(8, 15, exp))
                            : target.position(), target == null ? 0xBABABABA : 0xBAB2232A);
                }
            }
            case "flesh_ripping" -> {
                if (ticks % 3 == 0) {
                    LivingEntity target = targetLiving(level, player, lerp(6, 14, exp));
                    sendContextMarker(player, target == null ? traceLocation(level, player, lerp(6, 14, exp))
                            : target.getEyePosition(), target == null ? 0xC8888888 : 0xC8C83A3A);
                }
            }
            case "shift_tp", "jet_engine" -> {
                if (ticks % 3 == 0) sendContextMarker(player, traceLocation(level, player,
                        skill.name().equals("jet_engine") ? 12 : lerp(25, 35, exp)),
                        0xC878FF9A);
            }
            default -> { }
        }
    }

    public static void endContext(ServerPlayer player, AbilitySkill skill) {
        if(cn.academy.ability.vanilla.meltdowner.skill.MineRayContext.handles(skill))return;
        if (skill.name().equals("thunder_clap")) return;
        if (skill.name().equals("body_intensify")) return;
        if (skill.name().equals("charging")) return; // Original start audience receives the matching end message.
        String loop = contextLoopSound(skill.name());
        if (loop != null) broadcastContextSound(player, loop, false, contextLoopVolume(skill.name()));
        switch (skill.name()) {
            case "light_shield" -> {
                player.getPersistentData().remove("academy:light_shield_until");
                player.getPersistentData().remove("academy:light_shield_absorb");
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            }
            case "vec_deviation" -> player.getPersistentData().remove("academy:vec_deviation_until");
            case "meltdowner" -> {
                player.getAbilities().setWalkingSpeed(player.getPersistentData().contains("academy:meltdowner_walk_speed")
                        ? player.getPersistentData().getFloat("academy:meltdowner_walk_speed") : .1f);
                player.getPersistentData().remove("academy:meltdowner_walk_speed");
                player.onUpdateAbilities();
            }
            case "blood_retro" -> {
                player.getAbilities().setWalkingSpeed(player.getPersistentData().contains("academy:blood_walk_speed")
                        ? player.getPersistentData().getFloat("academy:blood_walk_speed") : .1f);
                player.getPersistentData().remove("academy:blood_walk_speed");
                player.onUpdateAbilities();
            }
            case "vec_reflection" -> player.getPersistentData().remove("academy:vec_reflection_until");
            case "mine_ray_basic", "mine_ray_expert", "mine_ray_luck" -> {
                player.getPersistentData().remove("academy:mine_ray_pos");
                player.getPersistentData().remove("academy:mine_ray_hardness");
            }
            case "scatter_bomb" -> clearScatterBalls(player);
            case "electron_missile" -> clearMissileBalls(player);
            case "plasma_cannon" -> {
                player.getPersistentData().remove("academy:plasma_charge_x");
                player.getPersistentData().remove("academy:plasma_charge_y");
                player.getPersistentData().remove("academy:plasma_charge_z");
            }
            case "storm_wing" -> {
                player.getAbilities().mayfly = player.getPersistentData().getBoolean("academy:storm_wing_prev_flight")
                        || player.isCreative() || player.isSpectator();
                player.getPersistentData().remove("academy:storm_wing_prev_flight");
                player.onUpdateAbilities();
            }
            default -> { }
        }
    }

    public static boolean contextAlive(ServerPlayer player, AbilitySkill skill) {
        if (skill.name().equals("mag_movement"))
            return AbilityContextManager.magneticAlive(player);
        if (skill.name().equals("jet_engine")) {
            AbilityState state = AbilityState.load(player);
            return player.isCreative() || state.cp() >= lerp(170, 140, state.experience(skill.id()));
        }
        if (skill.name().equals("threatening_teleport")) return !player.getMainHandItem().isEmpty();
        if (skill.name().equals("shift_tp")) return cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext.validHand(player);
        if (skill.name().equals("flesh_ripping")) {
            AbilityState state = AbilityState.load(player);
            return player.isCreative() || state.cp() >= lerp(130, 270, state.experience(skill.id()));
        }
        return true;
    }

    public static void execute(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        executeContext(player, state, skill, 0, 0);
    }

    public static void executeContext(ServerPlayer player, AbilityState state, AbilitySkill skill,
                                      int heldTicks, float contextValue) {
        if(cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.handles(skill))return;
        if(skill.name().equals("flashing"))return;
        if(cn.academy.ability.vanilla.meltdowner.skill.MeltdownerContext.handles(skill)
                || cn.academy.ability.vanilla.vecmanip.skill.VectorDefenseContext.handles(skill)
                || cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext.handles(skill)
                || cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext.handles(skill)
                || skill.name().equals("storm_wing") || skill.name().equals("plasma_cannon") || skill.name().equals("flesh_ripping") || skill.name().equals("threatening_teleport") || skill.name().equals("shift_tp")
                || cn.academy.ability.vanilla.meltdowner.skill.ElectronCloudContext.handles(skill)
                || skill.name().equals("ray_barrage"))return;
        if (cn.academy.ability.vanilla.meltdowner.skill.MineRayContext.handles(skill)) return;
        if (skill.name().equals("thunder_clap") || skill.name().equals("mine_detect") || skill.name().equals("mag_movement") || skill.name().equals("mag_manip") || skill.name().equals("electron_bomb")) return; // Dedicated contexts own payment and effect ordering.
        if (skill.name().equals("body_intensify")
                && !cn.academy.ability.vanilla.electromaster.skill.BodyIntensifyRules.canRelease(heldTicks)) return;
        if (skill.name().equals("railgun")) {
            cn.academy.ability.vanilla.electromaster.skill.Railgun.fireItem(player, state);
            return;
        }
        if (cn.academy.ability.vanilla.electromaster.skill.ElectricAttackContext.handles(skill)) {
            new cn.academy.ability.vanilla.electromaster.skill.ElectricAttackContext(player, state, skill).perform();
            return;
        }
        if (skill.name().equals("penetrate_teleport")) {
            var destination = selectPenetratingDestination(player.serverLevel(), player,
                    penetrateAllowedRange(state, skill, contextValue));
            if (destination.available()) executeSelectedTeleport(player, state, skill, destination.position());
            return;
        }
        if (skill.name().equals("mark_teleport")) {
            Vec3 destination = markDestination(player.serverLevel(), player, markAllowedRange(state, skill, heldTicks));
            if (destination.distanceTo(player.position()) >= 3) executeSelectedTeleport(player, state, skill, destination);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 visualStart = player.getEyePosition();
        switch (skill.category()) {
            case "electromaster" -> electromaster(level, player, state, skill, heldTicks);
            case "meltdowner" -> meltdowner(level, player, state, skill, heldTicks);
            case "teleporter" -> teleporter(level, player, state, skill, heldTicks, contextValue);
            case "vecmanip" -> vectorManipulation(level, player, state, skill, heldTicks);
            default -> { }
        }
        sendSkillVisual(player, skill, visualStart);
    }

    private static void electromaster(ServerLevel level, ServerPlayer player, AbilityState state,
                                      AbilitySkill skill, int heldTicks) {
        switch (skill.name()) {
            // Charging is exclusively a continuous ChargingContext; release performs no burst.
            case "charging" -> { }
            case "body_intensify" -> {
                var buffs = cn.academy.ability.vanilla.electromaster.skill.BodyIntensifyRules.roll(
                        heldTicks, state.experience(skill.id()), java.util.concurrent.ThreadLocalRandom.current()::nextDouble);
                if (buffs.count() >= 1) player.addEffect(new MobEffectInstance(MobEffects.JUMP, buffs.duration(), buffs.amplifier(), false, true));
                if (buffs.count() >= 2) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, buffs.duration(), buffs.amplifier(), false, true));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, buffs.hungerDuration(), 2));
                state.addExperience(skill, .01f);
                state.finishContext(skill, cn.academy.ability.vanilla.electromaster.skill.BodyIntensifyRules.cooldown(
                        state.experience(skill.id())), 0);
            }
            default -> { }
        }
    }

    private static void meltdowner(ServerLevel level, ServerPlayer player, AbilityState state,
                                   AbilitySkill skill, int heldTicks) {
        switch (skill.name()) {
            case "scatter_bomb" -> {
                float exp = state.experience(skill.id());
                List<ACElectronBallEntity> balls = scatterBalls(player);
                List<Mob> targets = exp > .5f ? level.getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(5), Entity::isAlive) : List.of();
                int autoCount = exp > .5f ? (int) (balls.size() * exp) : 0;
                for (ACElectronBallEntity ball : balls) {
                    Vec3 destination;
                    if (autoCount > 0 && !targets.isEmpty()) {
                        Mob target = targets.get(level.random.nextInt(targets.size()));
                        destination = target.getEyePosition();
                        autoCount--;
                    } else {
                        Vec3 look = player.getLookAngle();
                        float pitch = (level.random.nextFloat() - .5f) * (float) Math.toRadians(25);
                        float yaw = (level.random.nextFloat() - .5f) * (float) Math.toRadians(25);
                        destination = player.getEyePosition().add(look.scale(15))
                                .add(look.xRot(pitch).yRot(yaw).scale(15));
                    }
                    ball.fireAt(player, destination, lerp(5, 9, exp));
                }
                state.addExperience(skill, .001f * balls.size());
                sound(level, player, ACSounds.MD_BALL.get());
            }
            case "light_shield" -> {
                player.getPersistentData().putLong("academy:light_shield_until", level.getGameTime() + 400);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
                sound(level, player, ACSounds.MD_SHIELD.get());
                sound(level, player, ACSounds.get("md.shield_loop"));
            }
            case "meltdowner" -> {
                int charged = heldTicks <= 0 ? 40 : Math.min(40, heldTicks);
                performMeltdownerRay(level, player, state, skill, charged);
                rayParticles(level, player, 30, ParticleTypes.GLOW);
                sound(level, player, ACSounds.MD_MELTDOWNER.get());
            }
            case "ray_barrage" -> {
                float exp = state.experience(skill.id());
                ACThrownItemEntity silbarn = targetedThrownItem(level, player, 20, ACItems.SILBARN.get());
                Vec3 start = player.getEyePosition();
                if (silbarn != null) {
                    Vec3 origin = silbarn.position();
                    silbarn.breakSilbarn();
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(20), value -> validTarget(player, value))) {
                        Vec3 delta = entity.getEyePosition().subtract(start);
                        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                        float yaw = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
                        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(.001, horizontal)));
                        if (Math.abs(Mth.wrapDegrees(yaw - player.getYRot())) <= 27.5f
                                && Math.abs(Mth.wrapDegrees(pitch - player.getXRot())) <= 55)
                            meltdownerDamage(player, state, skill, entity, lerp(10, 18, exp));
                    }
                    broadcastVisual(level, player, new VisualEffectPayload("meltdowner", start.x, start.y, start.z,
                            origin.x, origin.y, origin.z, .055f, 0xD86EFF87, 10));
                    for (float yawOffset : new float[]{-27.5f, 0, 27.5f}) for (float pitchOffset : new float[]{-40, 0, 40}) {
                        Vec3 end = origin.add(Vec3.directionFromRotation(player.getXRot() + pitchOffset,
                                player.getYRot() + yawOffset).scale(20));
                        broadcastVisual(level, player, new VisualEffectPayload("meltdowner", origin.x, origin.y, origin.z,
                                end.x, end.y, end.z, .045f, 0xC86EFF87, 12));
                    }
                    particles(level, origin, ParticleTypes.GLOW, 70, 2.5);
                } else {
                    LivingEntity target = targetLiving(level, player, 20);
                    Vec3 end = target == null ? traceLocation(level, player, 20) : target.getEyePosition();
                    if (target != null) meltdownerDamage(player, state, skill, target, lerp(25, 60, exp));
                    broadcastVisual(level, player, new VisualEffectPayload("meltdowner", start.x, start.y, start.z,
                            end.x, end.y, end.z, .06f, 0xD86EFF87, 10));
                }
                sound(level, player, ACSounds.MD_MELTDOWNER.get());
            }
            case "jet_engine" -> {
                startJetDash(player, state.experience(skill.id()));
                particles(level, player.position(), ParticleTypes.FLAME, 35, .5);
                sound(level, player, ACSounds.MD_RAY.get());
            }
            case "electron_missile" -> {
                LivingEntity target = targetLiving(level, player, 50);
                Vec3 location = target == null ? traceLocation(level, player, 50) : target.getEyePosition();
                areaMeltdownerDamage(level, player, state, skill, location, 6, 22 + skillPower(state, 4));
                level.explode(player, location.x, location.y, location.z, 3.2f,
                        canDestroy(player, skill) ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
                sound(level, player, ACSounds.MD_BALL.get());
            }
            default -> { }
        }
    }

    private static void teleporter(ServerLevel level, ServerPlayer player, AbilityState state,
                                   AbilitySkill skill, int heldTicks, float contextValue) {
        switch (skill.name()) {
            case "threatening_teleport" -> {
                ItemStack held = player.getMainHandItem();
                if (!held.isEmpty()) {
                    float exp = state.experience(skill.id());
                    double range = lerp(8, 15, exp);
                    LivingEntity target = TeleporterGeometry.threateningTarget(player, range);
                    Vec3 destination = target != null ? target.position().add(0, target.getBbHeight(), 0)
                            : traceLocation(level, player, range);
                    ItemStack transported = held.copyWithCount(1);
                    if (!player.isCreative()) held.shrink(1);
                    if (target != null) {
                        float itemDamage = lerp(3, 6, exp) * (transported.is(ACItems.NEEDLE.get()) ? 1.5f : 1);
                        teleporterDamage(player, state, skill, target, itemDamage, true);
                    }
                    if (target == null || level.random.nextFloat() < .3f) {
                        level.addFreshEntity(new ItemEntity(level, destination.x, destination.y, destination.z, transported));
                    }
                    state.addExperience(skill, target == null ? .0006f : .003f);
                    particles(level, destination, ParticleTypes.PORTAL, 24, .45);
                }
                sound(level, player, ACSounds.TP.get());
            }
            // Movement releases are handled once by executeSelectedTeleport after final cost validation.
            case "penetrate_teleport", "mark_teleport" -> { }
            case "flesh_ripping" -> {
                float exp = state.experience(skill.id());
                LivingEntity target = targetLiving(level, player, lerp(6, 14, exp));
                if (target != null) {
                    teleporterDamage(player, state, skill, target, lerp(5, 12, exp), true);
                    if (level.random.nextFloat() < .05f)
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
                    particles(level, target.position(), ParticleTypes.DAMAGE_INDICATOR, 30, .8);
                    sound(level, player, ACSounds.TP_GUTS.get());
                }
            }
            case "location_teleport" -> {
                if (player.isShiftKeyDown()) {
                    state.setTeleportMark(level, player.blockPosition());
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("ac.teleport.marked"), true);
                } else if (state.teleportMark() != null && state.teleportMarkDimension().equals(level.dimension().location().toString())) {
                    teleport(player, Vec3.atBottomCenterOf(state.teleportMark()));
                } else {
                    BlockPos spawn = player.getRespawnPosition();
                    if (spawn != null && player.getRespawnDimension().equals(level.dimension())) teleport(player, Vec3.atBottomCenterOf(spawn));
                }
                sound(level, player, ACSounds.TP.get());
            }
            case "shift_tp" -> {
                ItemStack held = player.getMainHandItem();
                if (held.getItem() instanceof BlockItem blockItem) {
                    float exp = state.experience(skill.id());
                    double range = lerp(25, 35, exp);
                    BlockHitResult hit = blockTrace(level, player, range);
                    if (hit.getType() == HitResult.Type.MISS) {
                        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(range));
                        BlockPos support = BlockPos.containing(end);
                        hit = new BlockHitResult(end, Direction.UP, support, false);
                    }
                    BlockPlaceContext placeContext = TeleporterGeometry.shiftPlacement(player, hit);
                    BlockPos destination = placeContext.getClickedPos();
                    ItemStack transported = held.copyWithCount(1);
                    boolean placed = canDestroyBlock(player, skill, hit.getBlockPos())
                            && placeContext.canPlace() && blockItem.place(placeContext).consumesAction();
                    if (!placed) {
                        if (!player.isCreative()) held.shrink(1);
                        Vec3 drop = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
                        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, transported));
                    }
                    Vec3 end = Vec3.atCenterOf(destination);
                    int attacked = 0;
                    for (LivingEntity target : TeleporterGeometry.shiftTargets(player, end)) {
                        teleporterDamage(player, state, skill, target, lerp(15, 35, exp), false);
                        attacked++;
                    }
                    state.addExperience(skill, (1 + attacked) * .002f);
                    particles(level, end, ParticleTypes.PORTAL, 32, .6);
                }
                sound(level, player, ACSounds.TP_SHIFT.get());
            }
            default -> { }
        }
    }

    private static void vectorManipulation(ServerLevel level, ServerPlayer player, AbilityState state,
                                           AbilitySkill skill, int heldTicks) {
        switch (skill.name()) {
            case "dir_shock" -> {
                float exp = state.experience(skill.id());
                LivingEntity target = targetLiving(level, player, 3);
                if (target != null) {
                    damage(player, skill, target, lerp(7, 15, exp));
                    Vec3 away = target.position().subtract(player.position()).normalize().scale(.24);
                    target.setDeltaMovement(target.getDeltaMovement().add(away));
                    if (exp >= .25f) {
                        Vec3 delta = player.getEyePosition().subtract(target.getEyePosition());
                        delta = new Vec3(delta.x, delta.y - .6, delta.z).normalize();
                        target.setPos(target.getX(), target.getY() + .1, target.getZ());
                        target.setDeltaMovement(-delta.x * .7, -delta.y * .7, -delta.y * .7);
                    }
                    target.hurtMarked = true;
                    state.addExperience(skill, .0035f);
                    sound(level, player, ACSounds.VEC_SHOCK.get());
                } else state.addExperience(skill, .001f);
            }
            case "ground_shock" -> performGroundShock(level, player, state, skill);
            case "vec_accel" -> {
                Vec3 look = Vec3.directionFromRotation(player.getXRot() - 10, player.getYRot());
                player.setDeltaMovement(look.scale(cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext
                        .accelerationSpeed(state.level()>=4,state.experience(skill.id()))));
                player.hurtMarked = true;
                player.resetFallDistance();
                sound(level, player, ACSounds.VEC_ACCEL.get());
            }
            case "vec_deviation" -> {
                player.getPersistentData().putLong("academy:vec_deviation_until", level.getGameTime() + 160);
                repelProjectiles(level, player, 10, false);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
                sound(level, player, ACSounds.VEC_DEVIATION.get());
            }
            case "dir_blast" -> performDirectedBlast(level, player, state, skill);
            case "storm_wing" -> {
                player.setDeltaMovement(player.getDeltaMovement().add(player.getLookAngle().scale(1.2)).add(0, 1.0, 0));
                player.hurtMarked = true;
                player.resetFallDistance();
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6),
                        entity -> validTarget(player, entity))) target.setDeltaMovement(target.getDeltaMovement().add(0, 1.2, 0));
                particles(level, player.position(), ParticleTypes.CLOUD, 80, 3);
                sound(level, player, ACSounds.VEC_STORM.get());
            }
            case "blood_retro" -> {
                float exp = state.experience(skill.id());
                LivingEntity target = targetLiving(level, player, 2);
                if (target != null && target.getHealth() < target.getMaxHealth()) {
                    cn.academy.ability.vanilla.vecmanip.skill.VectorImpulseContext.attackBloodRetrograde(
                            AbilityContext.of(player, skill), target, exp, state.level()>=5);
                    particles(level, target.getEyePosition(), ParticleTypes.DAMAGE_INDICATOR, 25, .8);
                    sound(level, player, ACSounds.get("vecmanip.blood_retro"));
                }
            }
            case "vec_reflection" -> {
                player.getPersistentData().putLong("academy:vec_reflection_until", level.getGameTime() + 260);
                repelProjectiles(level, player, 14, true);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 260, 2));
                sound(level, player, ACSounds.VEC_REFLECTION.get());
            }
            case "plasma_cannon" -> {
                startPlasmaFlight(player, state.experience(skill.id()));
                sound(level, player, ACSounds.get("vecmanip.plasma_cannon_t"));
                sound(level, player, ACSounds.VEC_PLASMA.get());
            }
            default -> { }
        }
    }

    private static void broadcastVisual(ServerLevel level, ServerPlayer source, VisualEffectPayload payload) {
        for (ServerPlayer viewer : level.players()) if (viewer.distanceToSqr(source) <= 128 * 128)
            PacketDistributor.sendToPlayer(viewer, payload);
    }

    private static void sendContextMarker(ServerPlayer player, Vec3 position, int color) {
        if (position == null) return;
        PacketDistributor.sendToPlayer(player, new VisualEffectPayload("sphere",
                position.x, position.y, position.z, position.x, position.y, position.z,
                .22f, color, 5));
    }

    private static void sendSkillVisual(ServerPlayer player, AbilitySkill skill, Vec3 start) {
        if (skill.name().equals("body_intensify")) return;
        ServerLevel level = player.serverLevel();
        if (skill.name().equals("railgun") || skill.name().equals("charging")) return; // Dedicated skill contexts own these effects.
        if (skill.name().equals("electron_bomb") || skill.name().equals("scatter_bomb")
                || skill.name().equals("electron_missile") || skill.name().equals("ray_barrage")) return;
        boolean moved = player.getEyePosition().distanceToSqr(start) > 1;
        double range = switch (skill.name()) {
            case "plasma_cannon", "thunder_clap" -> 45;
            case "thunder_bolt" -> 32;
            case "electron_missile" -> 50;
            case "meltdowner" -> 38;
            case "mine_ray_basic" -> 10;
            case "mine_ray_expert", "mine_ray_luck", "dir_blast" -> 20;
            default -> 16;
        };
        Vec3 end = moved ? player.getEyePosition() : traceLocation(level, player, range);
        String effect;
        int color;
        float scale;
        int duration;
        switch (skill.name()) {
            case "arc_gen", "thunder_bolt", "thunder_clap" -> {
                effect = "arc"; color = 0xCC77DFFF; scale = .045f; duration = 9;
            }
            case "electron_bomb", "scatter_bomb", "electron_missile" -> {
                effect = "sphere"; color = 0xCC78FF9A; scale = .8f; duration = 16;
            }
            case "vec_accel" -> {
                effect = "sphere"; color = 0xA86FCFFF; scale = .85f; duration = 18;
                end = player.position().add(0, 1, 0);
            }
            case "light_shield" -> { effect = "shield"; color = 0xB878FFB4; scale = 1.25f; duration = 35; end = player.position().add(0, 1, 0); }
            case "meltdowner", "mine_ray_basic", "mine_ray_expert", "mine_ray_luck", "ray_barrage", "jet_engine" -> {
                effect = "meltdowner"; color = 0xD86EFF87; scale = .075f; duration = 12;
            }
            case "threatening_teleport", "penetrate_teleport", "mark_teleport", "flesh_ripping", "shift_tp", "flashing" -> {
                effect = "teleport"; color = 0xC8D9EDFF; scale = .35f; duration = 15;
            }
            case "vec_deviation" -> { effect = "shield"; color = 0xA878D8FF; scale = 1.5f; duration = 28; end = player.position().add(0, 1, 0); }
            case "vec_reflection" -> { effect = "reflection"; color = 0xB8C5E8FF; scale = 1.8f; duration = 32; end = player.position().add(0, 1, 0); }
            case "storm_wing", "ground_shock" -> { effect = "tornado"; color = 0xA8B8E8F5; scale = .45f; duration = 24; end = player.position(); }
            case "plasma_cannon" -> { effect = "plasma"; color = 0xD89C65FF; scale = .11f; duration = 18; }
            default -> { effect = "wave"; color = 0xB86FCEFF; scale = .12f; duration = 14; }
        }
        VisualEffectPayload payload = new VisualEffectPayload(effect,
                start.x, start.y, start.z, end.x, end.y, end.z, scale, color, duration);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(player) <= 128 * 128) PacketDistributor.sendToPlayer(viewer, payload);
        }
        net.minecraft.core.particles.SimpleParticleType sprite = switch (skill.category()) {
            case "electromaster" -> ACParticles.ARC.get();
            case "meltdowner" -> ACParticles.MELTDOWNER.get();
            case "teleporter" -> ACParticles.TELEPORT.get();
            case "vecmanip" -> ACParticles.VECTOR.get();
            default -> null;
        };
        if (sprite != null) level.sendParticles(sprite, end.x, end.y, end.z, 12,
                scale * 2, scale * 2, scale * 2, .025);
    }

    private static List<ACElectronBallEntity> scatterBalls(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(ACElectronBallEntity.class,
                player.getBoundingBox().inflate(4), ball -> ball.isHeldScatterBall() && ball.belongsTo(player));
    }

    private static void clearScatterBalls(ServerPlayer player) {
        for (ACElectronBallEntity ball : scatterBalls(player)) ball.discard();
    }

    private static List<ACElectronBallEntity> missileBalls(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(ACElectronBallEntity.class,
                player.getBoundingBox().inflate(4), ball -> ball.isHeldMissileBall() && ball.belongsTo(player));
    }

    private static void clearMissileBalls(ServerPlayer player) {
        for (ACElectronBallEntity ball : missileBalls(player)) ball.discard();
    }

    private static float vectorDifficulty(Entity entity) {
        String path = entity.getType().builtInRegistryHolder().key().location().getPath();
        return path.contains("snowball") ? .1f : path.contains("potion") ? 1.4f : 1;
    }

    private static boolean vectorExcluded(Entity entity) {
        String path = entity.getType().builtInRegistryHolder().key().location().getPath();
        return entity instanceof LivingEntity || entity instanceof ItemEntity
                || entity instanceof net.minecraft.world.entity.ExperienceOrb
                || path.contains("experience_bottle") || path.equals("xp_bottle");
    }

    private static void deviateProjectiles(ServerLevel level, ServerPlayer player, AbilityState state,
                                           AbilitySkill skill, float exp) {
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(5),
                value -> !vectorExcluded(value)
                        && !value.getPersistentData().getBoolean("academy:vector_affected"))) {
            float difficulty = vectorDifficulty(entity);
            if (!state.consumeForce(skill, lerp(15, 12, exp), 0, player.isCreative())) continue;
            if (entity instanceof net.minecraft.world.entity.projectile.LargeFireball) {
                Vec3 position = entity.position();
                net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
                entity.saveWithoutId(saved);
                int explosionPower = Math.max(1, saved.getByte("ExplosionPower"));
                entity.discard();
                level.explode(null, position.x, position.y, position.z, explosionPower,
                        Level.ExplosionInteraction.MOB);
            } else if (entity instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile) {
                entity.discard();
            } else {
                if (entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)
                    arrow.setBaseDamage(0);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.hurtMarked = true;
                entity.getPersistentData().putBoolean("academy:vector_affected", true);
            }
            state.addExperience(skill, .001f * difficulty);
            sound(level, player, ACSounds.VEC_DEVIATION.get());
        }
    }

    private static void reflectProjectiles(ServerLevel level, ServerPlayer player, AbilityState state,
                                            AbilitySkill skill, float exp) {
        Vec3 lookPosition = traceLocation(level, player, 20);
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(4),
                value -> !vectorExcluded(value)
                        && !value.getPersistentData().getBoolean("academy:vector_affected"))) {
            float difficulty = vectorDifficulty(entity);
            if (!state.consumeRaw(skill, difficulty * lerp(300, 160, exp), 0, player.isCreative())) continue;
            double speed = entity.getDeltaMovement().length();
            Vec3 direction = lookPosition.subtract(entity.position()).normalize();
            if (entity instanceof Projectile projectile) projectile.setOwner(player);
            entity.setDeltaMovement(direction.scale(speed));
            entity.hurtMarked = true;
            entity.getPersistentData().putBoolean("academy:vector_affected", true);
            state.addExperience(skill, .0008f * difficulty);
            sound(level, player, ACSounds.VEC_REFLECTION.get());
        }
    }

    private static void repelProjectiles(ServerLevel level, ServerPlayer player, double radius, boolean reflect) {
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius))) {
            Vec3 away = projectile.position().subtract(player.position()).normalize();
            Vec3 velocity = reflect ? projectile.getDeltaMovement().scale(-1.25) : away.scale(Math.max(.8, projectile.getDeltaMovement().length()));
            projectile.setDeltaMovement(velocity);
            projectile.hurtMarked = true;
            if (reflect) projectile.setOwner(player);
        }
    }

    public static boolean hasBloodRetroTarget(ServerPlayer player) {
        LivingEntity target = targetLiving(player.serverLevel(), player, 2);
        return target != null && target.getHealth() < target.getMaxHealth();
    }

    public static boolean hasDirectedShockTarget(ServerPlayer player) {
        return targetLiving(player.serverLevel(), player, 3) != null;
    }

    public static boolean hasFleshTarget(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        return targetLiving(player.serverLevel(), player, lerp(6, 14, state.experience(skill.id()))) != null;
    }

    private static LivingEntity nearestLivingAround(ServerLevel level, ServerPlayer player, double range) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        entity -> validTarget(player, entity))
                .stream().min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(player))).orElse(null);
    }

    private static LivingEntity targetLiving(ServerLevel level, ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        AABB search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(2);
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> validTarget(player, entity) && player.hasLineOfSight(entity))) {
            Optional<Vec3> clipped = target.getBoundingBox().inflate(.4).clip(start, end);
            if (clipped.isPresent()) {
                double distance = start.distanceToSqr(clipped.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = target;
                }
            }
        }
        return best;
    }

    private static boolean validTarget(ServerPlayer player, LivingEntity entity) {
        return entity.isAlive() && entity != player && (!(entity instanceof ServerPlayer)
                || ACConfig.ATTACK_PLAYERS.get());
    }

    private static void breakSoftBlocksAround(ServerLevel level, ServerPlayer player, AbilitySkill skill) {
        for (int i = 0; i < 40; i++) {
            BlockPos pos = player.blockPosition().offset(level.random.nextInt(21) - 10,
                    level.random.nextInt(21) - 10, level.random.nextInt(21) - 10);
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || hardness < 0 || hardness > .3f || !canDestroyBlock(player, skill, pos)) continue;
            level.playSound(null, pos, state.getSoundType(level, pos, player).getBreakSound(),
                    SoundSource.BLOCKS, .5f, 1);
            level.removeBlock(pos, false);
        }
    }

    private static boolean canDestroy(ServerPlayer player, AbilitySkill skill) {
        return skill.shouldDestroyBlocks() && AbilityPipeline.canBreakBlock(player.level(), skill);
    }

    private static boolean canDestroyBlock(ServerPlayer player, AbilitySkill skill, BlockPos pos) {
        if (!canDestroy(player, skill) || !player.mayUseItemAt(pos, Direction.UP, player.getMainHandItem())) return false;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(player.level(), pos,
                player.level().getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    private static BlockHitResult blockTrace(ServerLevel level, ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static Vec3 traceLocation(ServerLevel level, ServerPlayer player, double range) {
        LivingEntity entity = targetLiving(level, player, range);
        if (entity != null) return entity.getEyePosition();
        BlockHitResult block = blockTrace(level, player, range);
        return block.getType() == HitResult.Type.BLOCK ? block.getLocation()
                : player.getEyePosition().add(player.getLookAngle().scale(range));
    }

    static Vec3 flashingDestination(ServerLevel level, ServerPlayer player, int directionId, double range) {
        return cn.academy.ability.vanilla.teleporter.skill.FlashingContext.destination(player,directionId,range);
    }

    public static double markAllowedRange(AbilityState state, AbilitySkill skill, int ticks) {
        float exp = state.experience(skill.id());
        float cpPerBlock = lerp(12, 4, exp);
        return Math.min((ticks + 1) * 2d, Math.min(lerp(25, 60, exp), state.cp() / cpPerBlock));
    }

    public static Vec3 markDestination(ServerLevel level, ServerPlayer player, double range) {
        return cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.markAtRange(player,range);
    }

    public static double markTravelDistance(ServerPlayer player, double range) {
        return player.position().distanceTo(markDestination(player.serverLevel(), player, range));
    }

    public static double penetrateAllowedRange(AbilityState state, AbilitySkill skill, double selected) {
        float exp = state.experience(skill.id());
        return Math.max(0, Math.min(selected, state.cp() / Math.max(.001f, lerp(14, 9, exp))));
    }

    public static double penetratingTravelDistance(ServerPlayer player, double range) {
        Vec3 destination = penetratingDestination(player.serverLevel(), player, range);
        return destination == null ? 0 : player.position().distanceTo(destination);
    }

    public record PenetratingDestination(Vec3 position, boolean available) {}

    public static PenetratingDestination selectPenetratingDestination(ServerLevel level, ServerPlayer player, double range) {
        var destination=cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.penetrateAtRange(player,range);
        return new PenetratingDestination(destination.position(),destination.available());
    }

    private static Vec3 penetratingDestination(ServerLevel level, ServerPlayer player, double range) {
        var destination = selectPenetratingDestination(level, player, range);
        return destination.available() ? destination.position() : null;
    }

    /** Execute exactly the selection used to calculate the release cost, even when its allowed range is zero. */
    public static void executeSelectedTeleport(ServerPlayer player, AbilityState state, AbilitySkill skill, Vec3 destination) {
        Vec3 start = player.getEyePosition();
        double distance = player.position().distanceTo(destination);
        player.stopRiding();
        teleport(player, destination);
        recordTeleport(player, state);
        state.addExperience(skill, (float) ((skill.name().equals("penetrate_teleport") ? .00014 : .00018) * distance));
        sound(player.serverLevel(), player, ACSounds.TP.get());
        sendSkillVisual(player, skill, start);
    }

    private static void teleportForward(ServerLevel level, ServerPlayer player, double range) {
        teleportAlong(level, player, player.getLookAngle(), range);
    }

    private static void teleportAlong(ServerLevel level, ServerPlayer player, Vec3 direction, double range) {
        Vec3 normalized = direction.normalize();
        Vec3 start = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(start, start.add(normalized.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 destination = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().subtract(normalized.scale(1.2))
                : player.position().add(normalized.scale(range));
        teleport(player, destination);
    }

    private static void teleport(ServerPlayer player, Vec3 destination) {
        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();
        player.serverLevel().sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1, destination.z,
                45, .5, .8, .5, .15);
    }

    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 1.0e-6) return point.distanceTo(start);
        double t = Math.max(0, Math.min(1, point.subtract(start).dot(segment) / lengthSquared));
        return point.distanceTo(start.add(segment.scale(t)));
    }


    private static boolean isOre(BlockState state) {
        String path = state.getBlockHolder().unwrapKey().map(key -> key.location().getPath()).orElse("");
        return path.endsWith("_ore") || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
    }

    private static void recordTeleport(ServerPlayer player, AbilityState state) {
        if (state.recordTeleport() >= 400) ACAdvancements.grant(player, "legacy/teleporter/mastery");
    }

    private static void teleporterDamage(ServerPlayer player, AbilityState state, AbilitySkill skill, LivingEntity target,
                                         float amount, boolean ignoreArmor) {
        cn.academy.ability.vanilla.teleporter.util.TPSkillHelper.attack(
                AbilityContext.of(player, skill), target, amount, ignoreArmor);
    }

    private static void performDirectedBlast(ServerLevel level, ServerPlayer player, AbilityState state,
                                               AbilitySkill skill) {
        float exp = state.experience(skill.id());
        Vec3 center = blockTrace(level, player, 4).getLocation();
        if(state.level()>=5) {
            cn.academy.ability.vanilla.vecmanip.skill.DirectedBlastUpgrade.perform(player,AbilityContext.of(player,skill),
                    BlockPos.containing(Math.round(center.x),Math.round(center.y),Math.round(center.z)),player.getLookAngle(),exp);
            state.addExperience(skill,.0012f);
            return;
        }
        float breakHardness = exp < .25f ? 2.9f : exp < .5f ? 25 : 55;
        BlockPos origin = BlockPos.containing(Math.round(center.x), Math.round(center.y), Math.round(center.z));
        double radius = Math.sqrt(6) + 2 * exp;
        double radiusSquared = 6 + 4 * exp * (Math.sqrt(6) + exp);
        int reach = (int) Math.ceil(radius);
        for (int x = -reach; x <= reach; x++) for (int y = -reach; y <= reach; y++) for (int z = -reach; z <= reach; z++) {
            int distanceSquared = x * x + y * y + z * z;
            if (distanceSquared > radiusSquared) continue;
            BlockPos pos = origin.offset(x, y, z);
            BlockState blockState = level.getBlockState(pos);
            float hardness = blockState.getDestroySpeed(level, pos);
            if (blockState.isAir() || hardness < 0 || hardness > breakHardness || !canDestroyBlock(player, skill, pos)) continue;
            level.removeBlock(pos, false);
        }
        state.addExperience(skill, .0012f);
        particles(level, center, ParticleTypes.CLOUD, 45, 2.2);
        level.playSound(null, BlockPos.containing(center), ACSounds.VEC_GROUNDSHOCK.get(),
                SoundSource.PLAYERS, 2f, 1);
    }

    private static void performGroundShock(ServerLevel level, ServerPlayer player, AbilityState state,
                                             AbilitySkill skill) {
        float exp = state.experience(skill.id());
        double energy = lerp(60, 120, exp);
        int iterations = Math.round(lerp(10, 25, exp));
        Vec3 direction = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
        if (direction.lengthSqr() < .001) direction = new Vec3(0, 0, 1);
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        Vec3[] offsets = {Vec3.ZERO, side, side.scale(-1), side.scale(2), side.scale(-2)};
        double[] probabilities = {1, .7, .7, .3, .3};
        Set<BlockPos> affected = new java.util.HashSet<>();
        Set<UUID> struck = new java.util.HashSet<>();
        BlockPos origin = BlockPos.containing(player.getX(), player.getY() - 1, player.getZ());
        for (int step = 0; step < iterations && energy > 0; step++) {
            BlockPos center = BlockPos.containing(origin.getX() + direction.x * step,
                    origin.getY(), origin.getZ() + direction.z * step);
            for (int index = 0; index < offsets.length; index++) {
                if (level.random.nextDouble() >= probabilities[index]) continue;
                Vec3 offset = offsets[index];
                BlockPos pos = BlockPos.containing(center.getX() + offset.x, center.getY(), center.getZ() + offset.z);
                BlockState blockState = level.getBlockState(pos);
                if (blockState.isAir() || affected.contains(pos)) continue;
                affected.add(pos);
                if (blockState.is(Blocks.STONE)) {
                    energy -= .4;
                } else if (blockState.is(Blocks.GRASS_BLOCK)) {
                    energy -= .2;
                } else energy -= blockState.is(Blocks.FARMLAND) ? .1 : .5;

                AABB hitArea = new AABB(pos).inflate(.25, .2, .25).expandTowards(0, 2, 0);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitArea,
                        entity -> validTarget(player, entity) && struck.add(entity.getUUID()))) {
                    energy -= 1;
                    damage(player, skill, target, lerp(4, 6, exp));
                    target.setDeltaMovement(0,
                            cn.academy.ability.vanilla.vecmanip.skill.GroundShockLaunch.speed(exp), 0);
                    target.hurtMarked = true;
                    state.addExperience(skill, .002f);
                }
            }
        }
        state.addExperience(skill, .001f);
        for (BlockPos pos : affected) level.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 4, .35, .25, .35, .05);
        sound(level, player, ACSounds.VEC_GROUNDSHOCK.get());
    }

    private static void damage(ServerPlayer player, AbilitySkill skill, LivingEntity target, float amount) {
        AbilityContext.of(player, skill).attack(target, amount);
    }

    public static void applyMeltdownerMark(ServerPlayer player, LivingEntity target) {
        cn.academy.ability.vanilla.meltdowner.skill.MDDamageHelper.mark(player,target);
    }

    private static void meltdownerDamage(ServerPlayer player, AbilityState state, AbilitySkill skill, LivingEntity target, float amount) {
        damage(player, skill, target, amount);
        // The original large Meltdowner ray uses plain RangedRayDamage, not MDDamageHelper.
        if(!skill.name().equals("meltdowner"))applyMeltdownerMark(player, target);
    }

    private static void areaMeltdownerDamage(ServerLevel level, ServerPlayer player, AbilityState state, AbilitySkill skill,
                                              Vec3 center, double radius, double amount) {
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> validTarget(player, entity))) {
            double distance = Math.max(0, target.position().distanceTo(center));
            meltdownerDamage(player, state, skill, target,
                    (float) (amount * Math.max(.2, 1 - distance / radius)));
            Vec3 push = target.position().subtract(center).normalize().scale(1.2);
            target.setDeltaMovement(target.getDeltaMovement().add(push));
            target.hurtMarked = true;
        }
    }

    private static void electromasterDamage(ServerPlayer player, AbilitySkill skill, LivingEntity target, float amount) {
        cn.academy.ability.vanilla.electromaster.skill.ElectricAttackContext.attack(player, skill, target, amount);
    }

    private static void performMeltdownerRay(ServerLevel level, ServerPlayer player, AbilityState state,
                                              AbilitySkill skill, int heldTicks) {
        float exp = state.experience(skill.id());
        int charge = Math.min(40, Math.max(20, heldTicks));
        float timeRate = lerp(.8f, 1.2f, (charge - 20) / 20f);
        float radius = lerp(2, 3, exp);
        float startDamage = timeRate * lerp(18, 50, exp);
        Vec3 start = player.position();
        Vec3 direction = player.getLookAngle().normalize();
        AABB search = new AABB(start, start.add(direction.scale(50))).inflate(radius * 1.25);
        List<LivingEntity> targets = new java.util.ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> validTarget(player, entity)));
        targets.sort(java.util.Comparator.comparingDouble(entity ->
                entity.getBoundingBox().getCenter().subtract(start).dot(direction)));
        double beamDistance = 50;
        for (LivingEntity target : targets) {
            Vec3 relative = target.getBoundingBox().getCenter().subtract(start);
            double along = relative.dot(direction);
            if (along < 0 || along > beamDistance) continue;
            double perpendicular = relative.subtract(direction.scale(along)).length();
            if (perpendicular > radius * 1.2) continue;
            float applied = startDamage * lerp(1, .2f, (float) Math.min(50, perpendicular) / 50f);
            if (target instanceof ServerPlayer reflector
                    && AbilityContextManager.hasContext(reflector, "vec_reflection")) {
                reflectBeam(player, skill, reflector, 10, .5f * lerp(20, 50, exp));
                beamDistance = along;
                break;
            }
            meltdownerDamage(player, state, skill, target, applied);
        }
        destroyBeamBlocks(level, player, skill, start, direction, radius,
                timeRate * lerp(300, 700, exp), beamDistance);
    }

    private static void reflectBeam(ServerPlayer original, AbilitySkill skill, ServerPlayer reflector, double range, float damage) {
        LivingEntity target = targetLiving(reflector.serverLevel(), reflector, range);
        if (target != null) damage(original, skill, target, damage);
        Vec3 start = reflector.getEyePosition();
        Vec3 end = target == null ? start.add(reflector.getLookAngle().scale(range)) : target.getEyePosition();
        broadcastVisual(reflector.serverLevel(), original, new VisualEffectPayload("vector",
                start.x, start.y, start.z, end.x, end.y, end.z, .08f, 0xD080E8FF, 12));
    }

    private static void destroyBeamBlocks(ServerLevel level, ServerPlayer player, AbilitySkill skill, Vec3 start, Vec3 direction,
                                          double radius, float totalEnergy, double maxDistance) {
        if (!canDestroy(player, skill)) return;
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < .001) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        Vec3 up = side.cross(direction).normalize();
        java.util.List<Vec3> offsets = new java.util.ArrayList<>();
        for (double a = -radius; a <= radius; a += .9) for (double b = -radius; b <= radius; b += .9)
            if (a * a + b * b <= radius * radius * 1.05) offsets.add(side.scale(a).add(up.scale(b)));
        float lineEnergy = totalEnergy / Math.max(1, offsets.size());
        for (Vec3 offset : offsets) {
            float energy = lineEnergy * (.95f + level.random.nextFloat() * .1f);
            BlockPos previous = null;
            for (double distance = 0; distance <= maxDistance && energy > 0; distance += .9) {
                BlockPos pos = BlockPos.containing(start.add(offset).add(direction.scale(distance)));
                if (pos.equals(previous)) continue;
                previous = pos;
                BlockState blockState = level.getBlockState(pos);
                if (blockState.isAir() || !blockState.getFluidState().isEmpty()) continue;
                float hardness = blockState.getDestroySpeed(level, pos);
                if (hardness < 0 || energy < hardness || !canDestroyBlock(player, skill, pos)) break;
                if (level.random.nextFloat() < .05f)
                    Block.dropResources(blockState, level, pos, level.getBlockEntity(pos), player, ItemStack.EMPTY);
                if (distance < 20 && level.random.nextFloat() < .1f)
                    level.playSound(null, pos, blockState.getSoundType(level, pos, player).getBreakSound(),
                            SoundSource.BLOCKS, .5f, 1f);
                level.removeBlock(pos, false);
                energy -= hardness;
            }
        }
    }

    private static void areaDamage(ServerLevel level, ServerPlayer player, AbilitySkill skill, Vec3 center, double radius, double damage, double knockback) {
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, entity -> validTarget(player, entity))) {
            double distance = Math.max(0, target.position().distanceTo(center));
            float scaledDamage = (float) (damage * Math.max(0, 1 - distance / radius));
            damage(player, skill, target, scaledDamage);
            if (knockback > 0) {
                Vec3 push = target.position().subtract(center).normalize().scale(knockback);
                target.setDeltaMovement(target.getDeltaMovement().add(push));
                target.hurtMarked = true;
            }
        }
    }

    private static float skillPower(AbilityState state, float scale) {
        return state.level() * scale;
    }

    private static void rayParticles(ServerLevel level, ServerPlayer player, double range,
                                     net.minecraft.core.particles.SimpleParticleType particle) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (double distance = 0; distance <= range; distance += .65) {
            Vec3 point = start.add(look.scale(distance));
            level.sendParticles(particle, point.x, point.y, point.z, 1, .02, .02, .02, 0);
        }
    }

    private static void particles(ServerLevel level, Vec3 center,
                                  net.minecraft.core.particles.SimpleParticleType particle, int count, double spread) {
        level.sendParticles(particle, center.x, center.y, center.z, count, spread, spread, spread, .04);
    }

    private static String contextLoopSound(String skill) {
        return switch (skill) {
            case "charging" -> "em.charge_loop";
            case "body_intensify" -> "em.intensify_loop";
            case "mine_ray_basic", "mine_ray_expert", "mine_ray_luck" -> "md.mine_loop";
            case "light_shield" -> "md.shield_loop";
            case "storm_wing" -> "vecmanip.storm_wing";
            default -> null;
        };
    }

    private static float contextLoopVolume(String skill) {
        return switch (skill) {
            case "charging", "mine_ray_basic", "mine_ray_expert", "mine_ray_luck" -> .3f;
            case "light_shield" -> .5f;
            default -> .8f;
        };
    }

    private static void broadcastContextSound(ServerPlayer source, String sound, boolean start, float volume) {
        ContextSoundPayload payload = new ContextSoundPayload(source.getUUID(), sound, start, volume);
        for (ServerPlayer viewer : source.serverLevel().players()) {
            if (viewer.distanceToSqr(source) <= 128 * 128) PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static void sound(ServerLevel level, ServerPlayer player, SoundEvent sound) {
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, .9f + level.random.nextFloat() * .2f);
    }

    private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    private static boolean consumeOne(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static float lerp(float a, float b, float value) {
        float t = Math.max(0, Math.min(1, value));
        return a + (b - a) * t;
    }

    private static double approach(double from, double to, double amount) {
        double delta = to - from;
        return Math.abs(delta) <= amount ? to : from + Math.copySign(amount, delta);
    }

    private AbilityExecutor() {}
}
