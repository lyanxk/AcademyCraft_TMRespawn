package cn.academy.energy.impl;

import cn.academy.energy.MatrixNetworks;
import cn.academy.energy.NodeConnections;
import cn.academy.energy.api.WirelessHelper;
import cn.academy.energy.api.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Dimension-local original query API over the two authoritative SavedData stores. */
public final class WiWorldData {
    private final ServerLevel world;
    public WiWorldData(ServerLevel world) { this.world = world; }
    public static WiWorldData get(Level world) {
        if (!(world instanceof ServerLevel server)) throw new IllegalStateException("Not allowed to create WiWorldData in client");
        return MatrixNetworks.get(server).wirelessData(server);
    }
    public static WiWorldData getNonCreate(Level world) {
        if (!(world instanceof ServerLevel server)) return null;
        var data = MatrixNetworks.getIfPresent(server);
        return data == null ? null : data.wirelessData(server);
    }
    public WirelessNet getNetwork(IWirelessMatrix matrix) {
        return MatrixNetworks.get(world).network(world, ((BlockEntity) matrix).getBlockPos(), true);
    }
    public WirelessNet getNetwork(IWirelessNode node) {
        return MatrixNetworks.get(world).network(world, ((BlockEntity) node).getBlockPos(), false);
    }
    public NodeConn getNodeConnection(IWirelessNode node) { return NodeConnections.get(world).connectionFor(node); }
    public NodeConn getNodeConnection(IWirelessUser user) { return NodeConnections.get(world).connectionFor(user); }
    public Collection<WirelessNet> rangeSearch(int x, int y, int z, double range, int max) {
        var origin = new BlockPos(x, y, z);
        var candidates = WirelessHelper.findWirelessTiles(world, origin, range, max,
                block -> block instanceof IWirelessMatrix || block instanceof IWirelessNode);
        var result = new LinkedHashSet<WirelessNet>();
        for (var block : candidates) {
            var net = block instanceof IWirelessMatrix matrix ? getNetwork(matrix) : getNetwork((IWirelessNode) block);
            var matrix = net == null ? null : net.getMatrix();
            if (matrix != null && origin.distSqr(((BlockEntity) matrix).getBlockPos()) <= matrix.getRange() * matrix.getRange()
                    && net.getLoad() < net.getCapacity()) {
                result.add(net);
                if (result.size() >= max) return result;
            }
        }
        return result;
    }
    public void tick() { MatrixNetworks.get(world).tick(world); NodeConnections.get(world).tick(world); }
}
