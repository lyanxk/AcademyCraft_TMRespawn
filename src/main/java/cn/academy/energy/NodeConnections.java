package cn.academy.energy;

import cn.academy.AcademyCraft;
import cn.academy.energy.impl.WirelessChunks;
import cn.academy.energy.api.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import cn.academy.block.entity.ACMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * The node/user half of the original WiWorldData + NodeConn. Matrix membership is independent.
 * Coordinate membership lives in dimension SavedData, including members in unloaded chunks.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NodeConnections extends SavedData {
    private static final String ID = "academy_node_connections";
    private ServerLevel level;
    private final Map<Long, Connection> connections = new LinkedHashMap<>();
    private final Map<Long, Connection> lookup = new HashMap<>();
    private final Set<Connection> records = new LinkedHashSet<>();
    private final Set<Connection> cleanupNextTick = new LinkedHashSet<>();

    // Original WiWorldData always writes, including the validity filter in NodeConn.toNBT.
    @Override public boolean isDirty() { return true; }

    public static NodeConnections get(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(new Factory<>(NodeConnections::new, NodeConnections::load), ID);
        data.level = level; return data;
    }

    public static NodeConnections load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new NodeConnections();
        for (Tag entry : tag.getList("Connections", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry;
            long pos = saved.getLong("Node");
            var connection = result.new Connection(pos);
            for (long user : saved.getLongArray("Generators")) connection.generators.add(user);
            for (long user : saved.getLongArray("Receivers")) connection.receivers.add(user);
            for (long user : saved.getLongArray("PendingRemoval")) connection.pendingRemoval.add(user);
            result.connections.put(pos, connection);
            result.records.add(connection);
            connection.generators.forEach(user -> result.lookup.put(user, connection));
            connection.receivers.forEach(user -> result.lookup.put(user, connection));
        }
        // Retargeting can leave a deferred member in the old connection until the next tick.
        if (tag.contains("Lookup", Tag.TAG_LIST)) result.lookup.clear();
        for (Tag entry : tag.getList("Lookup", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry;
            var connection = result.connections.get(saved.getLong("Node"));
            long user = saved.getLong("User");
            if (connection != null && connection.allMembers().contains(user)) result.lookup.put(user, connection);
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        var savedMembers = new IdentityHashMap<Connection, Set<Long>>();
        for (Connection connection : List.copyOf(records)) {
            if (connection.disposed) continue;
            var generators = persistedMembers(connection.generators, IWirelessGenerator.class);
            var receivers = persistedMembers(connection.receivers, IWirelessReceiver.class);
            var members = new HashSet<>(generators); members.addAll(receivers);
            savedMembers.put(connection, members);
            var saved = new CompoundTag();
            saved.putLong("Node", connection.node);
            saved.putLongArray("Generators", generators);
            saved.putLongArray("Receivers", receivers);
            saved.putLongArray("PendingRemoval", connection.pendingRemoval.stream().filter(members::contains).toList());
            list.add(saved);
        }
        tag.put("Connections", list);
        var lookupTag = new ListTag();
        lookup.forEach((user, connection) -> {
            if (!savedMembers.getOrDefault(connection, Set.of()).contains(user)) return;
            var saved = new CompoundTag();
            saved.putLong("User", user);
            saved.putLong("Node", connection.node);
            lookupTag.add(saved);
        });
        tag.put("Lookup", lookupTag);
        return tag;
    }

    private List<Long> persistedMembers(List<Long> members, Class<?> role) {
        return List.copyOf(members).stream().filter(pos -> level == null
                || !WirelessChunks.isGenerated(level, BlockPos.of(pos))
                || role.isInstance(WirelessChunks.getGenerated(level, pos))).toList();
    }

    public static boolean isNode(BlockEntity machine) { return machine instanceof IWirelessNode; }
    public static boolean isGenerator(BlockEntity machine) { return machine instanceof IWirelessGenerator; }
    public static boolean isUser(BlockEntity machine) {
        return machine instanceof IWirelessGenerator || machine instanceof IWirelessReceiver;
    }
    public int load(BlockPos node) {
        var connection = connections.get(node.asLong());
        return connection == null || connection.disposed ? 0 : connection.getLoad();
    }
    public @Nullable BlockPos linkedNode(BlockPos user) {
        var connection = lookup.get(user.asLong());
        return connection == null || connection.disposed ? null : BlockPos.of(connection.node);
    }

    /** Import only pre-.22 coordinate-backed saves; never resurrect a disconnected migrated user. */
    public void importLegacy(ACMachineBlockEntity user, BlockPos node) {
        if (isUser(user) && !lookup.containsKey(user.getBlockPos().asLong())) add(user, node.asLong());
    }

    /** Original LinkUserEvent rules: optional password, node range, shared receiver/generator capacity. */
    public int link(ACMachineBlockEntity user, ACMachineBlockEntity node, String password, boolean authenticate) {
        return user instanceof IWirelessUser wirelessUser && node instanceof IWirelessNode wirelessNode
                ? link(wirelessUser, wirelessNode, password, authenticate) : 3;
    }
    public int link(IWirelessUser user, IWirelessNode node, String password, boolean authenticate) {
        BlockEntity userBlock = (BlockEntity) user, nodeBlock = (BlockEntity) node;
        if (!(userBlock.getLevel() instanceof ServerLevel level) || userBlock.getLevel() != nodeBlock.getLevel()
                || !isUser(userBlock)) return 3;
        connectionFor(node);
        if (authenticate && !(nodeBlock instanceof ACMachineBlockEntity machine ? machine.authenticateNode(password)
                : Objects.equals(node.getPassword(), password))) return 2;
        if (userBlock.getBlockPos().distSqr(nodeBlock.getBlockPos()) > node.getRange() * node.getRange()
                || load(nodeBlock.getBlockPos()) >= node.getCapacity()) return 3;
        long userPos = userBlock.getBlockPos().asLong();
        var old = lookup.get(userPos);
        if (old != null && old.validate()) {
            if (old == connections.get(nodeBlock.getBlockPos().asLong())) return 0;
            old.pendingRemoval.add(userPos);
        }
        add(userBlock, nodeBlock.getBlockPos().asLong());
        if (userBlock instanceof ACMachineBlockEntity machine)
            machine.updateNodeConnectionSnapshot(nodeBlock.getBlockPos(), MatrixNetworks.get(level).networkNameForNode(nodeBlock.getBlockPos()));
        return 0;
    }

    public List<IWirelessNode> nodesInRange(ServerLevel level, BlockPos pos) {
        return cn.academy.energy.api.WirelessHelper.getNodesInRange(level, pos);
    }

    public cn.academy.energy.impl.NodeConn connectionFor(IWirelessNode node) {
        var block = (BlockEntity) node;
        if (!(block.getLevel() instanceof ServerLevel server)) throw new IllegalStateException("Wireless data is server-only");
        level = server;
        long position = block.getBlockPos().asLong();
        var current = connections.get(position);
        if (current != null && current.validate()) return current;
        var created = new Connection(position); connections.put(position, created); records.add(created); setDirty();
        return created;
    }
    public cn.academy.energy.impl.NodeConn connectionFor(IWirelessUser user) {
        if (user == null) return null;
        if (!(user instanceof IWirelessGenerator) && !(user instanceof IWirelessReceiver))
            throw new IllegalArgumentException("Invalid user type");
        var block = (BlockEntity) user;
        if (!(block.getLevel() instanceof ServerLevel server)) throw new IllegalStateException("Wireless data is server-only");
        level = server;
        var current = lookup.get(block.getBlockPos().asLong());
        return current != null && current.validate() ? current : null;
    }

    private void add(BlockEntity user, long node) {
        var connection = connections.computeIfAbsent(node, position -> {
            var created = new Connection(position); records.add(created); return created;
        });
        long pos = user.getBlockPos().asLong();
        var members = isGenerator(user) ? connection.generators : connection.receivers;
        if (!members.contains(pos)) members.add(pos);
        connection.pendingRemoval.remove(pos);
        lookup.put(pos, connection);
        setDirty();
    }

    public void unlink(BlockPos user) {
        var connection = lookup.get(user.asLong());
        if (connection != null && connection.validate()) {
            connection.pendingRemoval.add(user.asLong());
            setDirty();
        }
    }

    /** Explicit administrative cleanup; normal block removal is detected by validate() on tick. */
    public void removeNode(ServerLevel level, BlockPos node) {
        var connection = connections.get(node.asLong());
        if (connection != null) { connection.dispose(); cleanup(level, connection); }
    }
    private void cleanup(ServerLevel level, Connection connection) {
        records.remove(connection);
        connections.remove(connection.node, connection);
        for (long user : connection.allMembers()) clearLookup(level, connection, user);
        setDirty();
    }

    private void clearLookup(ServerLevel level, Connection connection, long user) {
        if (lookup.get(user) == connection) {
            lookup.remove(user);
            var machine = loadedMachine(level, user);
            if (machine instanceof ACMachineBlockEntity modern && isUser(machine)) modern.updateNodeConnectionSnapshot(null, "");
        }
    }

    private static @Nullable BlockEntity loadedMachine(ServerLevel level, long position) {
        BlockPos pos = BlockPos.of(position);
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity machine = level.getBlockEntity(pos);
        return machine != null && !machine.isRemoved() ? machine : null;
    }

    /** Server END tick, matching WirelessSystem: generators first, then an independent receiver budget. */
    public void tick(ServerLevel level) {
        this.level = level;
        for (Connection connection : List.copyOf(cleanupNextTick)) cleanup(level, connection);
        cleanupNextTick.clear();
        for (Connection connection : List.copyOf(records)) {
            if (connection.disposed) cleanupNextTick.add(connection);
            else tickConnection(level, connection);
        }
    }
    private void tickConnection(ServerLevel level, Connection connection) {
        // Original public NodeConn.tick validates, but does not return merely because disposed is true.
        connection.validate();
        if (!WirelessChunks.isGenerated(level, BlockPos.of(connection.node))) return;
        var node = WirelessChunks.getGenerated(level, connection.node);
        if (!(node instanceof IWirelessNode)) return;
        transfer(level, connection, (IWirelessNode) node, true);
        transfer(level, connection, (IWirelessNode) node, false);
        if (!connection.pendingRemoval.isEmpty()) {
            for (long user : connection.pendingRemoval) clearLookup(level, connection, user);
            connection.generators.removeAll(connection.pendingRemoval);
            connection.receivers.removeAll(connection.pendingRemoval);
            connection.pendingRemoval.clear();
            setDirty();
        }
    }

    private void transfer(ServerLevel level, Connection connection, IWirelessNode node, boolean generators) {
        var members = generators ? connection.generators : connection.receivers;
        Collections.shuffle(members);
        double remaining = node.getBandwidth();
        for (long pos : members) {
            if (remaining == 0) break;
            if (!WirelessChunks.isGenerated(level, BlockPos.of(pos))) continue;
            var user = WirelessChunks.getGenerated(level, pos);
            if (user == null || !isUser(user) || isGenerator(user) != generators) {
                connection.pendingRemoval.add(pos);
                continue;
            }
            double current = node.getEnergy();
            if (generators) {
                var generator = (IWirelessGenerator) user;
                double required = Math.min(remaining, Math.min(generator.getBandwidth(), node.getMaxEnergy() - current));
                double amount = Math.min(required, generator.getProvidedEnergy(required));
                node.setEnergy(current + amount);
                remaining -= amount;
            } else {
                var receiver = (IWirelessReceiver) user;
                double give = Math.min(current, Math.min(remaining, receiver.getBandwidth()));
                give = Math.min(receiver.getRequiredEnergy(), give);
                give -= receiver.injectEnergy(give);
                node.setEnergy(current - give);
                remaining -= give;
            }
        }
    }

    @SubscribeEvent
    public static void afterServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            MatrixNetworks.get(level).tick(level);
            get(level).tick(level);
        }
    }

    private final class Connection extends cn.academy.energy.impl.NodeConn {
        final long node;
        boolean disposed;
        final List<Long> generators = new ArrayList<>(), receivers = new ArrayList<>();
        final Set<Long> pendingRemoval = new HashSet<>();
        Connection(long node) { this.node = node; }
        @Override public void dispose() { disposed = true; setDirty(); }
        @Override public boolean isDisposed() { return disposed; }
        @Override public void tick() { if (level != null) tickConnection(level, this); }
        @Override public IWirelessNode getNode() {
            // Original VNNode has ignoreChunk=true for explicit access.
            var block = level == null ? null : level.getBlockEntity(BlockPos.of(node));
            return block instanceof IWirelessNode role ? role : null;
        }
        @Override public int getLoad() { return generators.size() + receivers.size(); }
        @Override public int getCapacity() { var role = getNode(); return role == null ? Integer.MAX_VALUE : role.getCapacity(); }
        boolean validate() {
            if (!disposed && level != null && WirelessChunks.isGenerated(level, BlockPos.of(node))
                    && (!(WirelessChunks.getGenerated(level, node) instanceof IWirelessNode) || allMembers().isEmpty())) dispose();
            return !disposed;
        }
        List<Long> allMembers() {
            var members = new ArrayList<>(generators);
            members.addAll(receivers);
            return members;
        }
    }
}
