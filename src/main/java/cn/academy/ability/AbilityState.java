package cn.academy.ability;

import cn.academy.config.ACConfig;
import cn.academy.registry.ACAttachments;
import cn.academy.event.ability.CalcEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-authoritative replacement for LambdaLib2's AbilityData, CPData and PresetData. */
public final class AbilityState {
    public static final String ROOT_KEY = "academy:ability";

    private String category = "";
    private int level;
    private float expAddedThisLevel;
    private Player owner;
    private float rawCpCapacity = Float.NaN;
    private float rawOverloadCapacity = Float.NaN;
    private float levelFiveCpCapacity = Float.NaN;
    private float cp;
    private float overload;
    private float bonusCp;
    private float bonusOverload;
    private boolean active;
    private boolean overloadLocked;
    private boolean terminalInstalled;
    private final cn.academy.tutorial.TutorialData tutorialData = new cn.academy.tutorial.TutorialData();
    private boolean interfered;
    /** CPData's runtime source map is deliberately not serialized or copied on respawn. */
    private final Map<String, IInterfSource> interferenceSources = new HashMap<>();
    @FunctionalInterface public interface IInterfSource { boolean interfering(); }
    private boolean attackPlayers = true;
    private boolean destroyBlocks = true;
    private boolean coinFlip = true;
    private boolean mouseWheelTeleport = true;
    private int teleportCount;
    private final Set<String> learned = new LinkedHashSet<>();
    private final Map<String, Float> experience = new HashMap<>();
    private final Map<String, Boolean> enhanced = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private final Map<String, Integer> cooldownMaxima = new HashMap<>();


    private final Set<String> apps = new HashSet<>();
    private BlockPos teleportMark;
    private String teleportMarkDimension = "";
    private final List<TeleportLocation> teleportLocations = new ArrayList<>();

    public static AbilityState load(Player player) {
        if (!player.hasData(ACAttachments.ABILITY))
            replace(player, player.getPersistentData().getCompound(ROOT_KEY));
        AbilityState state = player.getData(ACAttachments.ABILITY);
        if (state.owner != player) state.bind(player);
        return state;
    }

    public static void replace(Player player, CompoundTag tag) {
        replace(player, tag, true);
    }

    public static void replace(Player player, CompoundTag tag, boolean notifyPresets) {
        AbilityState previous = player.hasData(ACAttachments.ABILITY) ? player.getData(ACAttachments.ABILITY) : null;
        AbilityState state = fromTag(tag);
        state.bind(player);
        player.getPersistentData().put(ROOT_KEY, state.toTag());
        // Ability snapshots also carry CP every few ticks. Only category or learned-skill changes rebuild controls.
        if (notifyPresets && player.level().isClientSide && (previous == null || !state.category.equals(previous.category) || !state.learned.equals(previous.learned)))
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.PresetUpdateEvent(player));
    }

    private void bind(Player player) {
        boolean changedOwner = owner != player;
        owner = player;
        player.setData(ACAttachments.ABILITY, this);
        tutorialData.ensureMisakaId(player);
        if (changedOwner && !player.level().isClientSide) recalculateCapacities(false);
    }

    public void save(Player player) {
        bind(player);
        player.getPersistentData().put(ROOT_KEY, toTag());
    }

    public static AbilityState fromTag(CompoundTag tag) {
        AbilityState state = new AbilityState();
        state.category = tag.getString("Category");
        state.level = tag.getInt("Level");
        state.rawCpCapacity = tag.contains("RawMaxCP") ? tag.getFloat("RawMaxCP") : Float.NaN;
        state.rawOverloadCapacity = tag.contains("RawMaxOverload") ? tag.getFloat("RawMaxOverload") : Float.NaN;
        state.levelFiveCpCapacity = tag.contains("LevelFiveCP") ? tag.getFloat("LevelFiveCP") : Float.NaN;
        state.expAddedThisLevel = tag.contains("LevelExperience") ? tag.getFloat("LevelExperience")
                : tag.getFloat("LevelProgress") * state.levelExperienceThreshold();
        state.cp = tag.getFloat("CP");
        state.overload = tag.getFloat("Overload");
        state.bonusCp = tag.getFloat("BonusCP");
        state.bonusOverload = tag.getFloat("BonusOverload");
        state.active = tag.getBoolean("Active");
        state.overloadLocked = tag.getBoolean("OverloadLocked");
        state.terminalInstalled = tag.getBoolean("TerminalInstalled");
        state.tutorialData.read(tag);
        state.interfered = tag.getBoolean("Interfered");
        state.attackPlayers = !tag.contains("AttackPlayers") || tag.getBoolean("AttackPlayers");
        state.destroyBlocks = !tag.contains("DestroyBlocks") || tag.getBoolean("DestroyBlocks");
        state.coinFlip = !tag.contains("CoinFlip") || tag.getBoolean("CoinFlip");
        state.mouseWheelTeleport = !tag.contains("MouseWheelTeleport") || tag.getBoolean("MouseWheelTeleport");
        state.teleportCount = Math.max(0, tag.getInt("TeleportCount"));

        ListTag learnedTag = tag.getList("Learned", Tag.TAG_STRING);
        for (Tag value : learnedTag) state.learned.add(value.getAsString());

        CompoundTag expTag = tag.getCompound("Experience");
        for (String key : expTag.getAllKeys()) state.experience.put(key, expTag.getFloat(key));
        CompoundTag enhancedTag = tag.getCompound("EnhancedSkills");
        for (String key : enhancedTag.getAllKeys()) state.enhanced.put(key, enhancedTag.getBoolean(key));

        CompoundTag cooldownTag = tag.getCompound("Cooldowns");
        CompoundTag maxima = tag.getCompound("CooldownMaxima");
        for (String key : cooldownTag.getAllKeys()) {
            int left = Math.max(0, cooldownTag.getInt(key));
            if (left == 0) continue;
            state.cooldowns.put(key, left);
            state.cooldownMaxima.put(key, Math.max(left, maxima.getInt(key)));
        }

        ListTag appsTag = tag.getList("Apps", Tag.TAG_STRING);
        for (Tag value : appsTag) state.apps.add(value.getAsString());

        if (tag.contains("TeleportMark", Tag.TAG_COMPOUND)) {
            CompoundTag mark = tag.getCompound("TeleportMark");
            state.teleportMark = new BlockPos(mark.getInt("X"), mark.getInt("Y"), mark.getInt("Z"));
            state.teleportMarkDimension = mark.getString("Dimension");
        }
        ListTag locations = tag.getList("TeleportLocations", Tag.TAG_COMPOUND);
        for (Tag entry : locations) {
            CompoundTag location = (CompoundTag) entry;
            state.teleportLocations.add(new TeleportLocation(location.getString("Name"), location.getString("Dimension"),
                    location.getDouble("X"), location.getDouble("Y"), location.getDouble("Z")));
        }
        state.normalize();
        return state;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Category", category);
        tag.putInt("Level", level);
        tag.putFloat("LevelProgress", levelProgress());
        tag.putFloat("LevelExperience", expAddedThisLevel);
        ensureCapacities();
        tag.putFloat("RawMaxCP", rawCpCapacity);
        tag.putFloat("RawMaxOverload", rawOverloadCapacity);
        tag.putFloat("LevelFiveCP", levelFiveCpCapacity);
        tag.putFloat("CP", cp);
        tag.putFloat("Overload", overload);
        tag.putFloat("BonusCP", bonusCp);
        tag.putFloat("BonusOverload", bonusOverload);
        tag.putBoolean("Active", active);
        tag.putBoolean("OverloadLocked", overloadLocked);
        tag.putBoolean("TerminalInstalled", terminalInstalled);
        tutorialData.write(tag);
        tag.putBoolean("Interfered", interfered);
        tag.putBoolean("AttackPlayers", attackPlayers);
        tag.putBoolean("DestroyBlocks", destroyBlocks);
        tag.putBoolean("CoinFlip", coinFlip);
        tag.putBoolean("MouseWheelTeleport", mouseWheelTeleport);
        tag.putInt("TeleportCount", teleportCount);

        ListTag learnedTag = new ListTag();
        learned.forEach(value -> learnedTag.add(StringTag.valueOf(value)));
        tag.put("Learned", learnedTag);

        CompoundTag expTag = new CompoundTag();
        experience.forEach(expTag::putFloat);
        tag.put("Experience", expTag);
        CompoundTag enhancedTag = new CompoundTag();
        enhanced.forEach(enhancedTag::putBoolean);
        tag.put("EnhancedSkills", enhancedTag);

        CompoundTag cooldownTag = new CompoundTag();
        cooldowns.forEach(cooldownTag::putInt);
        tag.put("Cooldowns", cooldownTag);
        CompoundTag cooldownMaximumTag = new CompoundTag();
        cooldownMaxima.forEach(cooldownMaximumTag::putInt);
        tag.put("CooldownMaxima", cooldownMaximumTag);

        ListTag appsTag = new ListTag();
        apps.forEach(value -> appsTag.add(StringTag.valueOf(value)));
        tag.put("Apps", appsTag);

        if (teleportMark != null) {
            CompoundTag mark = new CompoundTag();
            mark.putInt("X", teleportMark.getX());
            mark.putInt("Y", teleportMark.getY());
            mark.putInt("Z", teleportMark.getZ());
            mark.putString("Dimension", teleportMarkDimension);
            tag.put("TeleportMark", mark);
        }
        ListTag locations = new ListTag();
        for (TeleportLocation value : teleportLocations) {
            CompoundTag location = new CompoundTag();
            location.putString("Name", value.name());
            location.putString("Dimension", value.dimension());
            location.putFloat("X", (float)value.x());
            location.putFloat("Y", (float)value.y());
            location.putFloat("Z", (float)value.z());
            locations.add(location);
        }
        tag.put("TeleportLocations", locations);
        return tag;
    }

    private void normalize() {
        if (AbilityRegistry.category(category) == null) {
            category = "";
            level = 0;
            active = false;
            learned.clear();
            experience.clear();
            enhanced.clear();
        } else {
            level = Math.max(1, Math.min(5, level));
        }
        expAddedThisLevel = Float.isFinite(expAddedThisLevel) ? Math.max(0, expAddedThisLevel) : 0;
        bonusCp = clamp(bonusCp, 0, hasCategory() ? maxBonusCp() : 0);
        bonusOverload = clamp(bonusOverload, 0, hasCategory() ? maxBonusOverload() : 0);
        cp = clamp(cp, 0, maxCp());
        overload = clamp(overload, 0, maxOverload());
        if (hasCategory() && overload >= maxOverload()) overloadLocked = true;
    }

    public boolean hasCategory() { return !category.isEmpty(); }
    public String category() { return category; }
    public int level() { return level; }
    public float levelProgress() {
        if (!hasCategory()) return 0;
        float threshold = levelExperienceThreshold();
        return threshold == 0 ? 1 : Math.min(1, expAddedThisLevel / threshold);
    }

    private float levelExperienceThreshold() {
        AbilityCategory value = AbilityRegistry.category(category);
        if (value == null) return 0;
        long count = value.enabledSkills().stream().filter(skill -> skill.controllable() && skill.level() == level).count();
        return count * (level == 4 ? 1.333f : .666f);
    }
    public float cp() { return cp; }
    public float overload() { return overload; }
    public float maxCp() {
        if (!hasCategory()) return 0;
        ensureCapacities();
        return rawCpCapacity + bonusCp;
    }

    private float rawMaxCpAtLevel(int targetLevel) {
        ensureCapacities();
        return targetLevel == 5 ? levelFiveCpCapacity : calculateInitialCp(targetLevel);
    }

    public float maxOverload() {
        if (!hasCategory()) return 0;
        ensureCapacities();
        return rawOverloadCapacity + bonusOverload;
    }

    private void ensureCapacities() {
        if (!Float.isFinite(rawCpCapacity) || !Float.isFinite(rawOverloadCapacity)
                || !Float.isFinite(levelFiveCpCapacity)) recalculateCapacities(false);
    }

    private float calculateInitialCp(int targetLevel) {
        float value = ACConfig.INITIAL_CP[targetLevel].get().floatValue();
        if (owner != null) value = CalcEvent.calc(new CalcEvent.MaxCP(owner, value));
        else {
            if (learned.contains(category + ".brain_course")) value += 1000;
            if (learned.contains(category + ".brain_course_advanced")) value += 1500;
        }
        return Float.isFinite(value) ? Math.max(1, value) : 1;
    }

    public void recalculateCapacities(boolean refill) {
        int currentLevel = Math.max(0, Math.min(5, level));
        rawCpCapacity = calculateInitialCp(currentLevel);
        levelFiveCpCapacity = currentLevel == 5 ? rawCpCapacity : calculateInitialCp(5);
        float overloadValue = ACConfig.INITIAL_OVERLOAD[currentLevel].get().floatValue();
        if (owner != null) overloadValue = CalcEvent.calc(new CalcEvent.MaxOverload(owner, overloadValue));
        else if (learned.contains(category + ".brain_course_advanced")) overloadValue += 100;
        rawOverloadCapacity = Float.isFinite(overloadValue) ? Math.max(1, overloadValue) : 1;
        if (!hasCategory()) return;
        cp = refill ? rawCpCapacity + bonusCp : Math.min(cp, rawCpCapacity + bonusCp);
        overload = refill ? 0 : Math.min(overload, rawOverloadCapacity + bonusOverload);
    }

    private float maxBonusCp() { return ACConfig.ADDITIONAL_CP[level].get().floatValue(); }
    private float maxBonusOverload() { return ACConfig.ADDITIONAL_OVERLOAD[level].get().floatValue(); }
    public boolean active() { return active; }
    public boolean overloadLocked() { return overloadLocked; }
    /** The full-overload indication ends as recovery starts; the lock lasts until zero. */
    public boolean overloaded() { return overloadLocked && overload >= maxOverload(); }
    public boolean interfered() { return interfered; }
    public boolean terminalInstalled() { return terminalInstalled; }
    // Retained only for reading/round-tripping pre-parity port saves. Gameplay settings now
    // come from ACConfig (server policy) and ACHudConfig (local preferences), as in 1.12.2.
    public boolean attackPlayers() { return attackPlayers; }
    public boolean destroyBlocks() { return destroyBlocks; }
    public boolean coinFlip() { return coinFlip; }
    public boolean mouseWheelTeleport() { return mouseWheelTeleport; }
    public Set<String> learned() { return Set.copyOf(learned); }
    public Set<String> apps() { return Set.copyOf(apps); }
    public cn.academy.tutorial.TutorialData tutorialData() { return tutorialData; }
    public Set<String> tutorials() { return tutorialData.unlocked(); }
    public boolean unlockTutorial(String id) { return tutorialData.unlock(id); }
    public boolean tutorialUnlocked(String id) { return tutorialData.unlocked(id); }
    public static final int SKILL_CHANNELS = 32;

    /** Stable category indices keep running skills independent of the wheel selection. */
    public String controlSkill(int channel) {
        var skills = AbilityRegistry.category(category);
        if (skills == null || channel < 0 || channel >= skills.skills().size()) return "";
        var skill = skills.skills().get(channel);
        return skill.controllable() && skill.isEnabled() && learned.contains(skill.id()) ? skill.id() : "";
    }

    public List<Integer> controllableSlots() {
        return java.util.stream.IntStream.range(0, SKILL_CHANNELS)
                .filter(slot -> !controlSkill(slot).isEmpty()).boxed().toList();
    }
    public float experience(String skillId) {
        if (skillId.equals("meltdowner.rad_intensify") && learned.contains(skillId))
            return clamp(maxCp() / rawMaxCpAtLevel(5), 0, 1);
        if (skillId.equals("teleporter.shift_tp")
                && AbilityPipeline.isAllWorldDisableBreakBlock(owner == null ? null : owner.level())) return 1;
        return experience.getOrDefault(skillId, 0f);
    }
    public int cooldown(String skillId) { return cooldowns.getOrDefault(skillId, 0); }
    /** Upgrade resets retrain the enhanced effect, while retaining mastery for skill prerequisites. */
    public float experienceForLearning(String skillId) {
        if (!learned.contains(skillId)) return 0;
        if (enhanced.getOrDefault(skillId, false)) return 1;
        return experience(skillId);
    }
    public int cooldownMaximum(String skillId) { return cooldownMaxima.getOrDefault(skillId, 0); }
    public BlockPos teleportMark() { return teleportMark; }
    public String teleportMarkDimension() { return teleportMarkDimension; }
    public List<TeleportLocation> teleportLocations() { return List.copyOf(teleportLocations); }
    public int teleportCount() { return teleportCount; }
    public int recordTeleport() { return ++teleportCount; }

    public void setCategory(String id) {
        AbilityCategory newCategory = AbilityRegistry.category(id);
        if (newCategory == null) throw new IllegalArgumentException("Unknown category: " + id);
        if (category.equals(id)) return;
        category = id;
        if (level == 0) level = 1;
        learned.clear();
        experience.clear();
        enhanced.clear();
        if (owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            AbilityContextManager.categoryChanged(serverPlayer, this);
        clearCooldowns();
        recalculateCapacities(true);
        postBothSides(null);
    }

    public void clearCategory() {
        if (!hasCategory()) return;
        category = "";
        level = 0;
        cp = overload = 0;
        overloadLocked = false;
        learned.clear();
        experience.clear();
        enhanced.clear();
        if (owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            AbilityContextManager.categoryChanged(serverPlayer, this);
        clearCooldowns();
        recalculateCapacities(true);
        setActive(false);
        postBothSides(null);
    }

    public void setLevel(int newLevel) {
        if (!hasCategory()) return;
        int next = Math.max(1, Math.min(5, newLevel));
        if (next == level) return;
        int previousLevel=level;
        level = next;
        for(var skill:AbilityRegistry.category(category).skills())if(skill.crossesEnhancement(previousLevel,next)) {
            experience.remove(skill.id());
            enhanced.put(skill.id(),true);
            if (serverOwned()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new cn.academy.event.ability.SkillExpChangedEvent(owner,skill));
        }
        expAddedThisLevel = 0;
        // Original CPData.LevelChangeEvent resets capacity trained at the previous level.
        bonusCp = bonusOverload = 0;
        recalculateCapacities(true);
        overload = 0;
        overloadLocked = false;
        if (serverOwned()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new cn.academy.event.ability.LevelChangeEvent(owner));
    }

    public void setActive(boolean value) {
        boolean next = value && hasCategory();
        if (active == next) return;
        active = next;
        if (!active && owner instanceof net.minecraft.server.level.ServerPlayer player)
            AbilityContextManager.controlsDisabled(player, this);
        postLifecycle(active ? new cn.academy.event.ability.AbilityActivateEvent(owner)
                        : new cn.academy.event.ability.AbilityDeactivateEvent(owner),
                active ? cn.academy.network.AbilityLifecyclePayload.ACTIVATED
                        : cn.academy.network.AbilityLifecyclePayload.DEACTIVATED, "");
    }

    public void recoverAll() {
        if (!hasCategory()) return;
        cp = maxCp();
        overload = 0;
        overloadLocked = false;
    }

    /** Original /aim fullcp calls CPData.setCP and setOverload, not recoverAll. */
    public void restoreCommandResources() {
        if (!hasCategory()) return;
        cp = Math.max(0, maxCp() - bonusCp); // Original setCP clamps to base maxCP, excluding addMaxCP.
        overload = 0;
    }

    public boolean hasInterfSource(String name) { return interferenceSources.containsKey(name); }
    public void addInterf(String name, IInterfSource source) {
        checkInterferenceSide();
        interferenceSources.put(java.util.Objects.requireNonNull(name), java.util.Objects.requireNonNull(source));
    }
    public void removeInterf() { checkInterferenceSide(); interferenceSources.clear(); }
    public void removeInterf(String name) { checkInterferenceSide(); interferenceSources.remove(name); }
    private void checkInterferenceSide() {
        if (owner != null && owner.level().isClientSide) throw new IllegalStateException("Interference sources are server-owned");
    }
    /** CPData evaluates sources every server tick, but only while the player has an ability category. */
    public void tickInterference() {
        checkInterferenceSide();
        if (!hasCategory()) return;
        interferenceSources.entrySet().removeIf(entry -> !entry.getValue().interfering());
        setInterfered(!interferenceSources.isEmpty());
    }

    public void setInterfered(boolean value) {
        if (interfered == value) return;
        interfered = value;
        // CPData keeps activation separate from temporary interference.
        if (value && owner instanceof net.minecraft.server.level.ServerPlayer player)
            AbilityContextManager.interferenceStarted(player, this);
    }

    public boolean canUse() {
        return hasCategory() && active && !interfered && !overloadLocked && overload < maxOverload();
    }

    public boolean canStartContext(AbilitySkill skill) {
        return skill != null && skill.isEnabled() && canUse() && learned.contains(skill.id()) && level >= skill.level()
                && cooldown(skill.id()) <= 0;
    }

    /** Per-tick/context consumption used by the restored 1.12.2 key context lifecycle. */
    public boolean consumeRaw(AbilitySkill skill, float cpCost, float overloadCost, boolean creative) {
        if (!canStartContext(skill)) return false;
        return consumeActiveContext(skill, cpCost, overloadCost, creative);
    }

    /** Original AbilityContext.consume checks resources, not activation/cooldown a second time.
     * Only an already-owned server context may call this after validating its input message. */
    public boolean consumeActiveContext(AbilitySkill skill, float cpCost, float overloadCost, boolean creative) {
        return performConsumption(cpCost * skill.getCPConsumeSpeed(), overloadCost * skill.getOverloadConsumeSpeed(),
                creative, false);
    }

    /** AbilityContext.consumeWithForce bypasses per-skill multipliers in the original implementation. */
    public boolean consumeForce(AbilitySkill skill, float cpCost, float overloadCost, boolean creative) {
        if (!canStartContext(skill)) return false;
        return performConsumption(cpCost, overloadCost, creative, true);
    }

    private boolean performConsumption(float cpCost, float overloadCost, boolean creative, boolean force) {
        if (owner != null) {
            var event = new cn.academy.event.ability.CalcEvent.SkillPerform(owner, overloadCost, cpCost);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            cpCost = event.cp;
            overloadCost = event.overload;
        }
        if (!Float.isFinite(cpCost) || !Float.isFinite(overloadCost)) return false;
        cpCost = Math.max(0, cpCost);
        overloadCost = Math.max(0, overloadCost);
        if (!creative && !force && cp < cpCost) return false;
        if (!creative) {
            cp = Math.max(0, cp - cpCost);
            overload = Math.min(maxOverload(), overload + overloadCost);
            if (overload >= maxOverload()) {
                overloadLocked = true;
                if (owner != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new cn.academy.event.ability.OverloadEvent(owner));
            }
        }
        addCapacityGrowth(cpCost, overloadCost);
        return true;
    }

    public void finishContext(AbilitySkill skill, int cooldownTicks, float experienceGain) {
        if (skill == null) return;
        setCooldown(skill, cooldownTicks);
        if (experienceGain > 0) addExperience(skill, experienceGain);
    }

    public boolean consume(AbilitySkill skill, boolean creative) {
        if (!consumeRaw(skill, skill.cpCost(), skill.overload(), creative)) return false;
        setCooldown(skill, cooldownFor(skill));
        float experienceGain = switch (skill.name()) {
            case "mine_detect" -> .008f;
            case "body_intensify", "thunder_clap" -> .01f;
            case "mag_movement", "railgun" -> .005f;
            case "mine_ray_basic", "mine_ray_expert", "mine_ray_luck" -> .0005f;
            default -> .003f;
        };
        addExperience(skill, experienceGain);
        return true;
    }

    /** LocationTeleport.perform force-consumes; GUI eligibility is a separate snapshot. */
    public boolean consumeLocationTeleport(AbilitySkill skill, double distance, boolean crossDimension, boolean creative) {
        float cpCost=cn.academy.ability.vanilla.teleporter.LocationTeleportRules.cpCost(experience(skill.id()),distance,crossDimension);
        return performConsumption(cpCost,240,creative,true);
    }

    public void finishLocationTeleport(AbilitySkill skill,float distance) {
        addExperience(skill,distance>=200?.03f:.015f);
        setCooldown(skill,cn.academy.ability.vanilla.teleporter.LocationTeleportRules.cooldown(experience(skill.id())));
    }

    private void addCapacityGrowth(float cpCost, float overloadCost) {
        // CPData.performInternal grows capacity even in creative; addMaxOverload caps each increment at 10.
        bonusCp = Math.min(maxBonusCp(), bonusCp + cpCost * ACConfig.CP_CAPACITY_GROWTH.get().floatValue());
        bonusOverload = Math.min(maxBonusOverload(), bonusOverload
                + Math.min(10, overloadCost * ACConfig.OVERLOAD_CAPACITY_GROWTH.get().floatValue()));
    }

    public int cooldownFor(AbilitySkill skill) {
        float exp = experience(skill.id());
        if(skill.name().equals("blood_retro") && level>=5)return 40-5*(int)(exp*4);
        int[] range = switch (skill.name()) {
            case "mag_manip" -> level>=4?new int[]{40,40}:new int[]{60, 40};
            case "mine_detect" -> new int[]{900, 400};
            case "body_intensify" -> new int[]{900, 600};
            case "thunder_bolt" -> level>=5?new int[]{50,40}:new int[]{120, 50};
            case "railgun" -> level>=5?new int[]{160,80}:new int[]{300, 160};
            case "electron_bomb" -> new int[]{20, 10};
            case "light_shield" -> new int[]{800, 400};
            case "meltdowner" -> new int[]{300, 140};
            case "mine_ray_basic" -> new int[]{40, 20};
            case "mine_ray_expert", "mine_ray_luck" -> new int[]{60, 30};
            case "ray_barrage" -> new int[]{100, 40};
            case "jet_engine" -> new int[]{60, 30};
            case "electron_missile" -> new int[]{700, 400};
            case "threatening_teleport" -> new int[]{30, 15};
            case "penetrate_teleport" -> new int[]{50, 30};
            case "mark_teleport" -> new int[]{30, 0};
            case "flesh_ripping" -> new int[]{90, 40};
            case "shift_tp" -> new int[]{100, 60};
            case "dir_shock" -> level>=5?new int[]{20, 20}:new int[]{60, 20};
            case "ground_shock" -> new int[]{80, 40};
            case "vec_accel" -> level>=4?new int[]{50, 10}:new int[]{80, 50};
            case "dir_blast" -> level>=5?new int[]{50, 50}:new int[]{80, 50};
            case "storm_wing" -> new int[]{30, 10};
            case "blood_retro" -> new int[]{90, 40};
            case "plasma_cannon" -> new int[]{1000, 600};
            default -> null;
        };
        return range == null ? skill.cooldownTicks() : Math.max(0, Math.round(range[0] + (range[1] - range[0]) * exp));
    }

    public void tick() {
        tickCooldowns();
        if (!hasCategory()) return;

        {
            // Legacy CPData uses its raw maximum (level + courses), excluding capacity gained from use.
            float rawMaxCp = maxCp() - bonusCp;
            float ratio = rawMaxCp <= 0 ? 0 : cp / rawMaxCp;
            float recovery = (float) (0.0015 * rawMaxCp * (1 + ratio) * ACConfig.CP_RECOVERY_SCALE.get());
            float factor = owner == null ? (learned.contains(category + ".mind_course") ? 1.2f : 1)
                    : CalcEvent.calc(new CalcEvent.CPRecoverSpeed(owner, 1));
            recovery *= Float.isFinite(factor) ? Math.max(0, factor) : 0;
            cp = Math.min(maxCp(), cp + recovery);
        }

        {
            float rawMaxOverload = maxOverload() - bonusOverload;
            float recovery = (float) (Math.max(.002 * rawMaxOverload, .007 * rawMaxOverload * (1 - overload / rawMaxOverload / 4))
                    * ACConfig.OVERLOAD_RECOVERY_SCALE.get());
            float factor = owner == null ? 1 : CalcEvent.calc(new CalcEvent.OverloadRecoverSpeed(owner, 1));
            recovery *= Float.isFinite(factor) ? Math.max(0, factor) : 0;
            overload = Math.max(0, overload - recovery);
            if (overload <= 0) overloadLocked = false;
        }
    }

    public boolean canLearn(AbilitySkill skill) {
        if (skill == null || !skill.isEnabled() || !hasCategory() || !skill.category().equals(category) || learned.contains(skill.id()) || skill.level() > level) return false;
        int requiredLevelSkill = skill.requiredAnySkillLevel();
        if (requiredLevelSkill > 0 && AbilityRegistry.category(category).skills().stream()
                .noneMatch(candidate -> candidate.level() == requiredLevelSkill
                        && learned.contains(candidate.id()))) return false;
        for (AbilitySkill.Requirement requirement : skill.requirements()) {
            String id = category + "." + requirement.skillId();
            AbilitySkill dependency = AbilityRegistry.skill(id);
            if (dependency != null && !dependency.isEnabled()) continue;
            if (!learned.contains(id) || experienceForLearning(id) < requirement.experience()) return false;
        }
        return true;
    }

    public boolean learn(AbilitySkill skill) {
        if (skill == null || !skill.category().equals(category)) return false;
        if (!learned.add(skill.id())) return false;
        experience.putIfAbsent(skill.id(), 0f);
        recalculateCapacities(true);
        postBothSides(skill);
        return true;
    }

    public boolean unlearn(AbilitySkill skill) {
        // AbilityData.setSkillLearnState(false) changes only the learned bit. Relearning restores
        // its previous proficiency and binding; canStartContext still rejects unlearned skills.
        return skill != null && skill.category().equals(category) && learned.remove(skill.id());
    }

    public boolean setExperience(AbilitySkill skill, float value) {
        if (skill == null || !learned.contains(skill.id()) || !Float.isFinite(value)) return false;
        experience.put(skill.id(), clamp(value, 0, 1));
        if (serverOwned()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new cn.academy.event.ability.SkillExpChangedEvent(owner, skill));
        return true;
    }

    public void clearCooldowns() {
        cooldowns.clear();
        cooldownMaxima.clear();
    }

    private void setCooldown(AbilitySkill skill, int ticks) {
        if (ticks <= 0) return;
        // CooldownData.doSet never shortens a running cooldown or its original maximum.
        cooldowns.merge(skill.id(), ticks, Math::max);
        cooldownMaxima.merge(skill.id(), ticks, Math::max);
    }

    /** Client prediction decrements only cooldowns; resource consumption remains server-owned. */
    public void tickCooldowns() {
        cooldowns.replaceAll((key, value) -> Math.max(0, value - 1));
        cooldowns.values().removeIf(value -> value <= 0);
        cooldownMaxima.keySet().retainAll(cooldowns.keySet());
    }

    public void maxOutLevelProgress() {
        if (hasCategory()) expAddedThisLevel = 100;
    }

    public AbilitySkill learnNext(int maximumLevel) {
        AbilityCategory abilityCategory = AbilityRegistry.category(category);
        if (abilityCategory == null) return null;
        for (AbilitySkill skill : abilityCategory.skills()) {
            if (skill.level() <= maximumLevel && canLearn(skill)) {
                learn(skill);
                return skill;
            }
        }
        return null;
    }

    public void learnAll() {
        AbilityCategory abilityCategory = AbilityRegistry.category(category);
        if (abilityCategory == null) return;
        for (AbilitySkill skill : abilityCategory.skills()) {
            learned.add(skill.id());
            experience.putIfAbsent(skill.id(), 0f);
        }
        // AbilityData.learnAllSkills sets the bitset directly without per-skill learn events.
        recalculateCapacities(true);
    }

    public void addExperience(AbilitySkill skill, float amount) {
        if (skill != null) addRawExperience(skill, amount * skill.getExpIncrSpeed());
    }

    /** AbilityData.addSkillExp: used directly by original passive awards, without a skill-context multiplier. */
    public void addRawExperience(AbilitySkill skill, float amount) {
        if (skill == null || !skill.category().equals(category) || !Float.isFinite(amount) || amount <= 0) return;
        learn(skill);
        experience.compute(skill.id(), (key, old) -> Math.min(1, (old == null ? 0 : old) + amount));
        expAddedThisLevel += amount * ACConfig.PROGRESSION_SCALE.get().floatValue() * ACConfig.categoryProgression(category);
        if (serverOwned()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.SkillExpChangedEvent(owner, skill));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.ability.SkillExpAddedEvent(owner, skill, amount));
        }
    }

    private boolean serverOwned() { return owner != null && !owner.level().isClientSide; }

    private void postBothSides(AbilitySkill learnedSkill) {
        boolean learning = learnedSkill != null;
        postLifecycle(learning
                ? new cn.academy.event.ability.SkillLearnEvent(owner, learnedSkill)
                : new cn.academy.event.ability.CategoryChangeEvent(owner), learning
                ? cn.academy.network.AbilityLifecyclePayload.SKILL_LEARNED
                : cn.academy.network.AbilityLifecyclePayload.CATEGORY_CHANGED,
                learning ? learnedSkill.id() : "");
    }

    private void postLifecycle(cn.academy.event.ability.AbilityEvent event, int kind, String skill) {
        if (!(owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new cn.academy.network.AbilityLifecyclePayload(kind, skill, toTag()));
    }

    private void autoLearnPassives() {
        AbilityCategory abilityCategory = AbilityRegistry.category(category);
        if (abilityCategory == null) return;
        boolean changed;
        do {
            changed = false;
            for (AbilitySkill skill : abilityCategory.skills()) {
                if (!skill.controllable() && canLearn(skill)) changed |= learn(skill);
            }
        } while (changed);
    }

    public void installTerminal() { terminalInstalled = true; }
    public boolean tutorialGiven() { return tutorialData.tutorialAcquired(); }
    public int misakaId() { return tutorialData.getMisakaID(); }
    public void markTutorialGiven(int id) {
        tutorialData.markAcquired(id);
    }

    public boolean setting(String name) {
        return switch (name) {
            case "attack_players" -> attackPlayers;
            case "destroy_blocks" -> destroyBlocks;
            case "coin_flip" -> coinFlip;
            case "mouse_wheel_teleport" -> mouseWheelTeleport;
            default -> false;
        };
    }

    /** Per-player terrain preferences plus legacy metadata; never changes server policy. */
    public boolean setSetting(String name, boolean value) {
        switch (name) {
            case "attack_players" -> attackPlayers = value;
            case "destroy_blocks" -> destroyBlocks = value;
            case "coin_flip" -> coinFlip = value;
            case "mouse_wheel_teleport" -> mouseWheelTeleport = value;
            default -> { return false; }
        }
        return true;
    }
    public boolean installApp(String id) { return apps.add(id); }

    public void setTeleportMark(Level level, BlockPos pos) {
        teleportMark = pos.immutable();
        teleportMarkDimension = level.dimension().location().toString();
    }

    public void clearTeleportMark() {
        teleportMark = null;
        teleportMarkDimension = "";
    }

    public boolean addTeleportLocation(String name, Level level, double x, double y, double z) {
        String cleanName = name == null ? "" : name;
        int limit = cn.academy.ability.vanilla.teleporter.LocationTeleportRules.NAME_LENGTH;
        if (cleanName.length() > limit) cleanName = cleanName.substring(0, limit);
        teleportLocations.add(new TeleportLocation(cleanName, level.dimension().location().toString(), x, y, z));
        return true;
    }

    public boolean removeTeleportLocation(int index) {
        if (index < 0 || index >= teleportLocations.size()) return false;
        teleportLocations.remove(index);
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(category, level, levelProgress(), cp, maxCp(), overload, maxOverload(), active,
                interfered, Set.copyOf(learned), Map.copyOf(experience), terminalInstalled, Set.copyOf(apps),
                attackPlayers, destroyBlocks, coinFlip, mouseWheelTeleport);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TeleportLocation(String name, String dimension, double x, double y, double z) {
        public TeleportLocation { x=(float)x;y=(float)y;z=(float)z; }
    }

    public record Snapshot(String category, int level, float levelProgress, float cp, float maxCp,
                           float overload, float maxOverload, boolean active, boolean interfered,
                           Set<String> learned, Map<String, Float> experience,
                           boolean terminalInstalled, Set<String> apps, boolean attackPlayers, boolean destroyBlocks,
                           boolean coinFlip, boolean mouseWheelTeleport) {}
}
