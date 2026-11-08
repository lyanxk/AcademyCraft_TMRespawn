package cn.academy.energy;

import cn.academy.energy.api.block.*;
import cn.academy.energy.impl.WirelessChunks;
import net.minecraft.world.level.block.entity.BlockEntity;
import cn.academy.block.entity.ACMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Dimension-local WirelessNet state: network identity, member coordinates and the original 2000 buffer. */
public final class MatrixNetworks extends SavedData {
    private ServerLevel level;
    private cn.academy.energy.impl.WiWorldData wirelessData;
    private final Map<Long, Network> networks = new LinkedHashMap<>();
    private final Map<Long, Network> lookup = new HashMap<>();
    private final Set<Network> cleanupNextTick = new LinkedHashSet<>();

    // Original WiWorldData always writes, including the validity filter in WirelessNet.toNBT.
    @Override public boolean isDirty() { return true; }
    public static MatrixNetworks get(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(new Factory<>(MatrixNetworks::new, MatrixNetworks::load), "academy_matrix_networks");
        data.level = level;
        return data;
    }
    public static MatrixNetworks load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new MatrixNetworks();
        for (Tag entry : tag.getList("Networks", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry;
            var network = result.new Network(saved.getLong("Matrix"));
            network.initialized = saved.getBoolean("Initialized");
            network.ssid = saved.getString("SSID"); network.hash = saved.getString("PasswordHash");
            network.password = saved.contains("PasswordPlain") ? saved.getString("PasswordPlain")
                    : network.hash.isEmpty() || network.hash.equals(WirelessPasswords.hash("")) ? "" : null;
            network.encrypted = saved.getBoolean("Encrypted");
            network.buffer = saved.getDouble("Buffer");
            for (long node : saved.getLongArray("Nodes")) {
                network.nodes.add(node); result.lookup.put(node, network);
            }
            for (long node : saved.getLongArray("PendingRemoval")) network.pendingRemoval.add(node);
            result.networks.put(network.matrix, network);
        }
        if (tag.contains("Lookup", Tag.TAG_LIST)) result.lookup.clear();
        for (Tag entry : tag.getList("Lookup", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry;
            var network = result.networks.get(saved.getLong("Matrix"));
            long node = saved.getLong("Node");
            if (network != null && network.nodes.contains(node)) result.lookup.put(node, network);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        var savedMembers = new IdentityHashMap<Network, Set<Long>>();
        for (Network network : List.copyOf(networks.values())) {
            if (network.disposed) continue;
            var saved = new CompoundTag();
            saved.putLong("Matrix", network.matrix); saved.putBoolean("Initialized", network.initialized);
            saved.putString("SSID", network.ssid); saved.putString("PasswordHash", network.hash);
            if (network.password != null) saved.putString("PasswordPlain", network.password);
            saved.putBoolean("Encrypted", network.encrypted); saved.putDouble("Buffer", network.buffer);
            var members = List.copyOf(network.nodes).stream().filter(pos -> level == null
                    || !WirelessChunks.isGenerated(level, BlockPos.of(pos))
                    || WirelessChunks.getGenerated(level, pos) instanceof IWirelessNode).toList();
            savedMembers.put(network, new HashSet<>(members));
            saved.putLongArray("Nodes", members);
            saved.putLongArray("PendingRemoval", network.pendingRemoval.stream().filter(members::contains).toList());
            list.add(saved);
        }
        tag.put("Networks", list);
        var lookupTag = new ListTag();
        lookup.forEach((node, network) -> {
            if (!savedMembers.getOrDefault(network, Set.of()).contains(node)) return;
            var saved = new CompoundTag(); saved.putLong("Node", node); saved.putLong("Matrix", network.matrix); lookupTag.add(saved);
        });
        tag.put("Lookup", lookupTag);
        return tag;
    }
    public boolean initialized(BlockPos matrix) {
        var network = networks.get(matrix.asLong()); return network != null && network.initialized && !network.disposed;
    }
    public int load(BlockPos matrix) {
        var network = networks.get(matrix.asLong()); return network == null || network.disposed ? 0 : network.nodes.size();
    }
    public double buffer(BlockPos matrix) {
        var network = networks.get(matrix.asLong()); return network == null ? 0 : network.buffer;
    }
    public boolean encrypted(BlockPos matrix) {
        var network = networks.get(matrix.asLong()); return network != null && network.encrypted;
    }
    public @Nullable BlockPos linkedMatrix(BlockPos node) {
        var network = lookup.get(node.asLong()); return network == null || network.disposed ? null : BlockPos.of(network.matrix);
    }
    public boolean authenticate(BlockPos matrix, String hash) {
        var network = networks.get(matrix.asLong()); return network != null && network.initialized && !network.disposed && network.hash.equals(hash);
    }
    /** Rename/password edits retain membership and buffer, including empty or duplicate SSIDs. */
    public void configure(ACMachineBlockEntity matrix, String ssid, String hash, boolean encrypted) {
        if (matrix instanceof IWirelessMatrix role) configure(role, ssid, hash, encrypted);
    }
    public void configure(IWirelessMatrix matrix, String ssid, String hash, boolean encrypted) {
        BlockEntity block = (BlockEntity) matrix;
        var network = networks.computeIfAbsent(block.getBlockPos().asLong(), Network::new);
        if (!network.hash.equals(hash)) network.password = hash.equals(WirelessPasswords.hash("")) ? "" : null;
        network.initialized = true; network.ssid = ssid; network.hash = hash; network.encrypted = encrypted;
        mirror(block);
        if (block.getLevel() instanceof ServerLevel level) for (long pos : network.nodes) {
            var node = loaded(level, pos); if (node instanceof IWirelessNode) mirror(node);
        }
        setDirty();
    }
    /** Original CreateNetworkEvent destroys an existing network at this matrix before creating a new one. */
    public void create(IWirelessMatrix matrix, String ssid, String hash, boolean encrypted) {
        BlockEntity block = (BlockEntity) matrix;
        if (!(block.getLevel() instanceof ServerLevel level)) return;
        var old = networks.get(block.getBlockPos().asLong());
        boolean oldDisposed = old != null && old.disposed;
        remove(level, block.getBlockPos());
        // WiWorldData.createNetwork cleans up a replaced network without calling dispose().
        if (old != null) old.disposed = oldDisposed;
        configure(matrix, ssid, hash, encrypted);
    }
    public boolean changePassword(IWirelessMatrix matrix, String hash, boolean encrypted) {
        var network = networks.get(((BlockEntity) matrix).getBlockPos().asLong());
        if (network == null || !network.initialized || !network.validate()) return false;
        configure(matrix, network.ssid, hash, encrypted);
        return true;
    }
    public boolean rename(IWirelessMatrix matrix, String ssid) {
        var network = networks.get(((BlockEntity) matrix).getBlockPos().asLong());
        if (network == null || !network.initialized || !network.validate()) return false;
        configure(matrix, ssid, network.hash, network.encrypted);
        return true;
    }
    public String networkNameForNode(BlockPos node) {
        var network = lookup.get(node.asLong());
        return network == null || network.disposed ? "" : network.ssid;
    }
    public void importNode(ACMachineBlockEntity node, BlockPos matrix, String ssid) {
        long nodePos = node.getBlockPos().asLong();
        if (lookup.containsKey(nodePos)) return;
        var network = networks.computeIfAbsent(matrix.asLong(), Network::new);
        if (!network.initialized) network.ssid = ssid;
        network.nodes.add(nodePos); lookup.put(nodePos, network); setDirty();
    }
    public int link(ACMachineBlockEntity node, ACMachineBlockEntity matrix, String passwordHash) {
        return node instanceof IWirelessNode nodeRole && matrix instanceof IWirelessMatrix matrixRole
                ? link(nodeRole, matrixRole, passwordHash) : 3;
    }
    public int link(IWirelessNode node, IWirelessMatrix matrix, String passwordHash) {
        BlockEntity nodeBlock = (BlockEntity) node, matrixBlock = (BlockEntity) matrix;
        if (!(nodeBlock.getLevel() instanceof ServerLevel) || nodeBlock.getLevel() != matrixBlock.getLevel()) return 3;
        var network = networks.get(matrixBlock.getBlockPos().asLong());
        if (network == null || !network.initialized || !network.validate() || !network.hash.equals(passwordHash)) return 2;
        if (network.nodes.size() >= matrix.getCapacity()
                || nodeBlock.getBlockPos().distSqr(matrixBlock.getBlockPos()) > matrix.getRange() * matrix.getRange()) return 3;
        long pos = nodeBlock.getBlockPos().asLong();
        var old = lookup.get(pos);
        if (old != null && old.validate()) {
            if (old == network) return 0;
            old.pendingRemoval.add(pos);
        }
        if (!network.nodes.contains(pos)) network.nodes.add(pos);
        network.pendingRemoval.remove(pos); lookup.put(pos, network);
        mirror(nodeBlock); setDirty(); return 0;
    }
    public void unlink(BlockPos node) {
        var network = lookup.get(node.asLong());
        if (network != null && network.validate()) { network.pendingRemoval.add(node.asLong()); setDirty(); }
    }
    /** DestroyNetworkEvent invalidates now; WiWorldData removes the record on a later tick. */
    public void dispose(ServerLevel level, BlockPos matrix) {
        this.level = level;
        var network = networks.get(matrix.asLong());
        if (network != null && network.validate()) { network.disposed = true; setDirty(); }
    }
    /** Immediate cleanup for CreateNetworkEvent replacement and explicit administration. */
    public void remove(ServerLevel level, BlockPos matrix) {
        var network = networks.get(matrix.asLong());
        if (network == null) return;
        network.disposed = true;
        cleanup(level, network);
    }
    private void cleanup(ServerLevel level, Network network) {
        networks.remove(network.matrix, network);
        for (long node : network.nodes) clearLookup(level, network, node);
        var machine = loaded(level, network.matrix);
        if (machine instanceof IWirelessMatrix) mirror(machine);
        setDirty();
    }
    private void clearLookup(ServerLevel level, Network network, long node) {
        if (lookup.get(node) == network) {
            lookup.remove(node);
            var machine = loaded(level, node);
            if (machine != null && NodeConnections.isNode(machine)) mirror(machine);
        }
    }
    public void mirror(BlockEntity block) {
        if (!(block instanceof ACMachineBlockEntity machine)) return;
        boolean matrix = block instanceof IWirelessMatrix;
        var network = matrix ? networks.get(machine.getBlockPos().asLong()) : lookup.get(machine.getBlockPos().asLong());
        if (network != null && network.disposed) network = null;
        machine.updateMatrixNetworkSnapshot(matrix || network == null ? null : BlockPos.of(network.matrix),
                network != null && network.initialized, network == null ? "" : network.ssid,
                matrix && network != null ? network.hash : "", matrix && network != null && network.encrypted);
    }
    private static @Nullable BlockEntity loaded(ServerLevel level, long position) {
        BlockPos pos = BlockPos.of(position);
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity machine = level.getBlockEntity(pos);
        return machine != null && !machine.isRemoved() ? machine : null;
    }
    public void tick(ServerLevel level) {
        this.level = level;
        for (Network network : List.copyOf(cleanupNextTick)) cleanup(level, network);
        cleanupNextTick.clear();
        for (Network network : List.copyOf(networks.values())) {
            if (network.disposed) { cleanupNextTick.add(network); continue; }
            if (!WirelessChunks.isGenerated(level, BlockPos.of(network.matrix))) continue;
            var matrixBlock = WirelessChunks.getGenerated(level, network.matrix);
            if (!(matrixBlock instanceof IWirelessMatrix)) { network.disposed = true; setDirty(); continue; }
            if (!network.initialized) continue;
            IWirelessMatrix matrix = (IWirelessMatrix) matrixBlock;
            Collections.shuffle(network.nodes);
            double sum = 0, maxSum = 0;
            for (long pos : network.nodes) {
                if (!WirelessChunks.isGenerated(level, BlockPos.of(pos))) continue;
                var node = WirelessChunks.getGenerated(level, pos);
                if (node == null || !NodeConnections.isNode(node)) network.pendingRemoval.add(pos);
                else { sum += ((IWirelessNode) node).getEnergy(); maxSum += ((IWirelessNode) node).getMaxEnergy(); }
            }
            if (!network.pendingRemoval.isEmpty()) {
                for (long node : network.pendingRemoval) clearLookup(level, network, node);
                network.nodes.removeAll(network.pendingRemoval); network.pendingRemoval.clear(); setDirty();
            }
            if (maxSum == 0) continue;
            double percentage = sum / maxSum, remaining = matrix.getBandwidth();
            for (long pos : network.nodes) {
                var nodeBlock = WirelessChunks.getGenerated(level, pos);
                if (!(nodeBlock instanceof IWirelessNode node)) continue;
                var step = MatrixBalance.step(node.getEnergy(), node.getMaxEnergy() * percentage,
                        node.getBandwidth(), remaining, network.buffer);
                if (step.delta() != 0) {
                    node.setEnergy(node.getEnergy() + step.delta());
                    network.buffer = step.buffer(); setDirty();
                }
                remaining = step.remaining();
                if (remaining == 0) break;
            }
        }
    }
    public cn.academy.energy.impl.WiWorldData wirelessData(ServerLevel level) {
        this.level = level;
        if (wirelessData == null) wirelessData = new cn.academy.energy.impl.WiWorldData(level);
        return wirelessData;
    }
    public static MatrixNetworks getIfPresent(ServerLevel level) {
        var data = level.getDataStorage().get(new Factory<>(MatrixNetworks::new, MatrixNetworks::load), "academy_matrix_networks");
        if (data != null) data.level = level;
        return data;
    }
    public cn.academy.energy.impl.WirelessNet network(ServerLevel level, BlockPos position, boolean matrix) {
        this.level = level;
        var network = matrix ? networks.get(position.asLong()) : lookup.get(position.asLong());
        return network != null && network.initialized && network.validate() ? network : null;
    }
    public void rememberPassword(IWirelessMatrix matrix, String password) {
        var network = networks.get(((BlockEntity) matrix).getBlockPos().asLong());
        if (network != null && network.hash.equals(WirelessPasswords.hash(password))) {
            network.password = password; setDirty();
        }
    }
    private final class Network extends cn.academy.energy.impl.WirelessNet {
        final long matrix;
        boolean initialized, encrypted, disposed;
        String ssid = "", hash = "", password = "";
        double buffer;
        final List<Long> nodes = new ArrayList<>();
        final Set<Long> pendingRemoval = new HashSet<>();
        Network(long matrix) { this.matrix = matrix; }
        @Override public String getSSID() { return ssid; }
        @Override public void setSSID(String value) { ssid = value; updateLive(); }
        @Override public String getPassword() {
            if (password == null) throw new IllegalStateException("Legacy network only stores a password hash; reset its password before reading plaintext");
            return password;
        }
        @Override public boolean resetPassword(String value) {
            password = value; hash = WirelessPasswords.hash(value); encrypted = !value.isEmpty();
            updateLive(); return true;
        }
        @Override public boolean isDisposed() { return disposed; }
        @Override public int getLoad() { return nodes.size(); }
        @Override public int getCapacity() { var block = getMatrix(); return block == null ? 0 : block.getCapacity(); }
        @Override public IWirelessMatrix getMatrix() {
            // Original VWMatrix has ignoreChunk=true: an explicit accessor may request its chunk.
            var block = level == null ? null : level.getBlockEntity(BlockPos.of(matrix));
            return block instanceof IWirelessMatrix role ? role : null;
        }
        boolean validate() {
            if (level != null && WirelessChunks.isGenerated(level, BlockPos.of(matrix))
                    && !(WirelessChunks.getGenerated(level, matrix) instanceof IWirelessMatrix)) {
                disposed = true; setDirty();
            }
            return !disposed;
        }
        private void updateLive() {
            if (networks.get(matrix) != this || disposed) return;
            setDirty();
            if (level != null) {
                var block = loaded(level, matrix); if (block != null) mirror(block);
                for (long position : nodes) { var node = loaded(level, position); if (node != null) mirror(node); }
            }
        }
    }
}
