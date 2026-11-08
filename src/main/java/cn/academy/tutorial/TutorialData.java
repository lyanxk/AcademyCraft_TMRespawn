package cn.academy.tutorial;

import cn.academy.ability.AbilityState;
import cn.academy.config.ACConfig;
import cn.academy.event.TutorialActivatedEvent;
import cn.academy.network.ACNetwork;
import cn.academy.network.TutorialActivatedPayload;
import cn.academy.registry.ACItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** TutorialData's persistent event conditions and TickScheduler timeline, stored with the player's snapshot. */
public final class TutorialData {
    private BitSet savedConditions = new BitSet();
    private final Set<String> activated = new LinkedHashSet<>(), legacyUnlocked = new LinkedHashSet<>();
    private boolean tutorialAcquired, dirty;
    private int misakaId = -1;
    // Keep TickScheduler's float arithmetic, including its initial 0.15f rounding behavior.
    private float conditionCounter = 3 / 20f, giftCounter = 10 / 20f;

    public static TutorialData get(Player player) { return AbilityState.load(player).tutorialData(); }
    public int getMisakaID() { return misakaId; }
    public boolean tutorialAcquired() { return tutorialAcquired; }
    public boolean isCondActivate(int index) { return index >= 0 && savedConditions.get(index); }
    public boolean setCondActivate(int index) {
        if (savedConditions.get(index)) return false;
        savedConditions.set(index);
        dirty = true;
        return true;
    }
    public boolean legacyUnlocked(String id) { return legacyUnlocked.contains(id); }
    public boolean wasActivated(String id) { return activated.contains(id); }
    public boolean unlock(String id) { legacyUnlocked.add(id); return activated.add(id); }
    public boolean unlocked(String id) {
        var tutorial = TutorialRegistry.find(id);
        return activated.contains(id) || tutorial != null && tutorial.isDefaultInstalled();
    }
    public Set<String> unlocked() {
        var result = new LinkedHashSet<>(activated);
        for (var tutorial : TutorialRegistry.enumeration()) if (tutorial.isDefaultInstalled()) result.add(tutorial.id);
        return Set.copyOf(result);
    }
    public void ensureMisakaId(Player player) {
        if (!player.level().isClientSide && (misakaId < 1000 || misakaId >= 19000))
            misakaId = 1000 + player.getRandom().nextInt(18000);
    }
    public void markAcquired(int id) {
        tutorialAcquired = true;
        misakaId = Math.clamp(id, 1000, 18999);
    }

    public void tick(ServerPlayer player) {
        boolean sync = false;
        conditionCounter -= .05f;
        if (conditionCounter <= 0) {
            conditionCounter += 3 / 20f;
            if (dirty) {
                dirty = false;
                for (var tutorial : TutorialRegistry.enumeration()) {
                    if (!tutorial.isDefaultInstalled() && !activated.contains(tutorial.id) && tutorial.isActivated(player)) {
                        activated.add(tutorial.id);
                        NeoForge.EVENT_BUS.post(new TutorialActivatedEvent(player, tutorial));
                        PacketDistributor.sendToPlayer(player, new TutorialActivatedPayload(tutorial.id));
                    }
                }
                sync = true;
            }
        }
        giftCounter -= .05f;
        if (giftCounter <= 0) {
            giftCounter += 10 / 20f;
            if (ACConfig.GIVE_MISAKA_TERMINAL.get() && !tutorialAcquired) {
                player.serverLevel().addFreshEntity(new ItemEntity(player.serverLevel(), player.getX(),
                        player.getY() + 1, player.getZ(), new ItemStack(ACItems.TUTORIAL.get())));
                tutorialAcquired = true;
                sync = true;
            }
        }
        if (sync) ACNetwork.sync(player, AbilityState.load(player));
    }

    public void read(CompoundTag tag) {
        tutorialAcquired = tag.getBoolean("TutorialGiven");
        misakaId = tag.contains("MisakaId") ? tag.getInt("MisakaId") : -1;
        activated.clear();
        legacyUnlocked.clear();
        for (var value : tag.getList("Tutorials", Tag.TAG_STRING)) activated.add(value.getAsString());
        for (var value : tag.getList("LegacyTutorials", Tag.TAG_STRING)) legacyUnlocked.add(value.getAsString());
        if (!tag.contains("TutorialConditionVersion")) legacyUnlocked.addAll(activated);
        savedConditions = BitSet.valueOf(tag.getLongArray("TutorialConditions"));
        dirty = tag.getBoolean("TutorialDirty");
    }
    public void write(CompoundTag tag) {
        tag.putBoolean("TutorialGiven", tutorialAcquired);
        tag.putInt("MisakaId", misakaId);
        tag.putInt("TutorialConditionVersion", 1);
        tag.putLongArray("TutorialConditions", savedConditions.toLongArray());
        tag.putBoolean("TutorialDirty", dirty);
        tag.put("Tutorials", strings(activated));
        tag.put("LegacyTutorials", strings(legacyUnlocked));
    }
    private static ListTag strings(Set<String> values) {
        var list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }
}
