package cn.academy.energy.api;

import cn.academy.data.GameplayRules;

import cn.academy.data.GameplayData;

import cn.academy.energy.api.block.*;
import cn.academy.energy.impl.NodeConn;
import cn.academy.energy.impl.WiWorldData;
import cn.academy.energy.impl.WirelessNet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import java.util.function.Predicate;

/** Public query entry points and selection rules from the original WirelessHelper/WorldUtils. */
public final class WirelessHelper {
    public static WirelessNet getWirelessNet(IWirelessMatrix matrix) {
        return WiWorldData.get(((BlockEntity) matrix).getLevel()).getNetwork(matrix);
    }
    public static WirelessNet getWirelessNet(IWirelessNode node) {
        return WiWorldData.get(((BlockEntity) node).getLevel()).getNetwork(node);
    }
    public static boolean isNodeLinked(IWirelessNode node) { return getWirelessNet(node) != null; }
    public static boolean isMatrixActive(IWirelessMatrix matrix) { return getWirelessNet(matrix) != null; }
    public static Collection<WirelessNet> getNetInRange(Level world, int x, int y, int z, double range, int max) {
        return WiWorldData.get(world).rangeSearch(x, y, z, range, max);
    }
    public static NodeConn getNodeConn(IWirelessNode node) {
        return WiWorldData.get(((BlockEntity) node).getLevel()).getNodeConnection(node);
    }
    public static NodeConn getNodeConn(IWirelessUser user) {
        return WiWorldData.get(((BlockEntity) user).getLevel()).getNodeConnection(user);
    }
    public static boolean isReceiverLinked(IWirelessReceiver receiver) { return getNodeConn(receiver) != null; }
    public static boolean isGeneratorLinked(IWirelessGenerator generator) { return getNodeConn(generator) != null; }
    public static List<IWirelessNode> getNodesInRange(Level world, BlockPos pos) {
        return findWirelessTiles(world, pos, discoveryRange(), GameplayData.integer("wireless", "candidate_limit"), block -> {
            if (!(block instanceof IWirelessNode node)) return false;
            var conn = getNodeConn(node);
            return pos.distSqr(block.getBlockPos()) <= node.getRange() * node.getRange() && conn.getLoad() < conn.getCapacity();
        }).stream().map(block -> (IWirelessNode) block).toList();
    }
    /** WorldUtils x/y/z scan order, including the candidate cap before network filtering. */
    public static List<BlockEntity> findWirelessTiles(Level world, BlockPos center, double range, int max,
                                                     Predicate<BlockEntity> predicate) {
        int minX = (int) Math.floor(center.getX() - range), maxX = (int) Math.ceil(center.getX() + range);
        int minY = (int) Math.floor(center.getY() - range), maxY = (int) Math.ceil(center.getY() + range);
        int minZ = (int) Math.floor(center.getZ() - range), maxZ = (int) Math.ceil(center.getZ() + range);
        var result = new ArrayList<BlockEntity>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            var pos = new BlockPos(x, y, z);
            var block = world.getBlockEntity(pos);
            if (block != null && predicate.test(block) && center.distSqr(pos) <= range * range) {
                result.add(block);
                if (result.size() == max) return result;
            }
        }
        return result;
    }
    private static double discoveryRange() {
        double range = GameplayData.number("wireless", "node_search_minimum_range");
        for (var kind : java.util.List.of(cn.academy.block.MachineKind.NODE_BASIC, cn.academy.block.MachineKind.NODE_STANDARD, cn.academy.block.MachineKind.NODE_ADVANCED))
            range = Math.max(range, GameplayData.number(GameplayRules.machine(kind), "range"));
        return range;
    }
    private WirelessHelper() {}
}
