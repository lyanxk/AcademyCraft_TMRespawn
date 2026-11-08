package cn.academy.energy;

import cn.academy.AcademyCraft;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.energy.api.WirelessHelper;
import cn.academy.energy.api.block.*;
import cn.academy.energy.impl.WirelessNet;
import cn.academy.event.energy.LinkNodeEvent;
import cn.academy.event.energy.LinkUserEvent;
import cn.academy.menu.ACMachineMenu;
import cn.academy.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Original WirelessNetDelegate query/action/rebuild flow using modern typed payloads. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WirelessPageService {
    private record Pending(ServerPlayer player, ResourceKey<Level> dimension, WirelessPageRequestPayload request) {}
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
    private record DeveloperAuthorization(ResourceKey<Level> dimension, net.minecraft.core.BlockPos pos,
                                          int session, Set<net.minecraft.core.BlockPos> targets) {}
    private static final Map<UUID, DeveloperAuthorization> DEVELOPERS = new HashMap<>();

    private static ACMachineBlockEntity source(ServerPlayer player, WirelessPageRequestPayload request) {
        if (request.containerId() >= 0) {
            var menu = menu(player, request);
            return menu == null ? null : menu.machine();
        }
        // Developers and bridges use Screens rather than containers. Negative ids identify a page instance.
        if (player.isRemoved() || !player.isAlive() || player.containerMenu != player.inventoryMenu
                || player.distanceToSqr(request.pos().getCenter()) > 64
                || !(player.serverLevel().getBlockEntity(request.pos()) instanceof ACMachineBlockEntity machine)) return null;
        return switch (machine.kind()) {
            case DEVELOPER_NORMAL, DEVELOPER_ADVANCED, FE_INPUT, FE_OUTPUT -> machine;
            default -> null;
        };
    }

    private static ACMachineMenu menu(ServerPlayer player, WirelessPageRequestPayload request) {
        if (player.isRemoved() || !(player.containerMenu instanceof ACMachineMenu menu)
                || menu.containerId != request.containerId() || !menu.stillValid(player)) return null;
        var machine = menu.machine();
        return machine.getLevel() == player.level() && machine.getBlockPos().equals(request.pos())
                && (machine instanceof IWirelessNode || machine instanceof IWirelessUser) ? menu : null;
    }
    public static boolean request(ServerPlayer player, WirelessPageRequestPayload request) {
        if (source(player, request) == null) return false;
        PENDING.put(player.getUUID(), new Pending(player, player.level().dimension(), request));
        return true;
    }
    public static int action(ServerPlayer player, WirelessPageActionPayload payload) {
        var request = new WirelessPageRequestPayload(payload.containerId(), payload.requestId(), payload.pos());
        var machine = source(player, request);
        if (machine == null) return 3;
        var menu = payload.containerId() >= 0 ? menu(player, request) : null;
        var authorization = DEVELOPERS.get(player.getUUID());
        if (menu == null && (authorization == null || authorization.session != payload.containerId()
                || !authorization.dimension.equals(player.level().dimension()) || !authorization.pos.equals(payload.pos()))) return 3;
        int result = 3;
        if (payload.target().isEmpty()) result = machine.configureNetwork(player, "", "");
        else if (menu != null ? menu.isWirelessTargetAuthorized(payload.target().get())
                : authorization.targets.contains(payload.target().get())) {
            var target = player.serverLevel().getBlockEntity(payload.target().get());
            if (machine instanceof IWirelessNode node && target instanceof IWirelessMatrix matrix)
                result = NeoForge.EVENT_BUS.post(new LinkNodeEvent(node, matrix, payload.password())).result();
            else if (machine instanceof IWirelessUser user && target instanceof IWirelessNode node)
                result = NeoForge.EVENT_BUS.post(new LinkUserEvent(user, node, payload.password())).result();
        }
        if (payload.containerId() >= 0) player.displayClientMessage(Component.translatable(switch (result) {
            case 0 -> "ac.frequency.configured";
            case 1 -> "ac.frequency.permission_denied";
            case 2 -> "ac.frequency.authentication_failed";
            default -> "ac.frequency.invalid";
        }), false);
        request(player, request);
        return result;
    }
    private static WirelessPageSnapshotPayload.Target target(IWirelessNode node) {
        var block = (BlockEntity) node;
        boolean encrypted = block instanceof ACMachineBlockEntity machine ? machine.nodeEncrypted() : !node.getPassword().isEmpty();
        return new WirelessPageSnapshotPayload.Target(block.getBlockPos(), node.getNodeName(), encrypted);
    }
    private static WirelessPageSnapshotPayload.Target target(ServerPlayer player, WirelessNet net) {
        var matrix = net.getMatrix();
        if (matrix == null) return null;
        var pos = ((BlockEntity) matrix).getBlockPos();
        return new WirelessPageSnapshotPayload.Target(pos, net.getSSID(), MatrixNetworks.get(player.serverLevel()).encrypted(pos));
    }
    private static WirelessPageSnapshotPayload snapshot(ServerPlayer player, ACMachineBlockEntity machine, WirelessPageRequestPayload request) {
        WirelessPageSnapshotPayload.Target linked = null;
        var available = new ArrayList<WirelessPageSnapshotPayload.Target>();
        if (machine instanceof IWirelessNode node) {
            var connected = WirelessHelper.getWirelessNet(node);
            if (connected != null) linked = target(player, connected);
            var pos = machine.getBlockPos();
            for (var net : WirelessHelper.getNetInRange(player.serverLevel(), pos.getX(), pos.getY(), pos.getZ(), node.getRange(), 20)) {
                if (net == connected) continue;
                var candidate = target(player, net); if (candidate != null) available.add(candidate);
            }
        } else if (machine instanceof IWirelessUser user) {
            var connected = WirelessHelper.getNodeConn(user);
            if (connected != null && connected.getNode() != null) linked = target(connected.getNode());
            for (var node : WirelessHelper.getNodesInRange(player.serverLevel(), machine.getBlockPos())) {
                if (WirelessHelper.getNodeConn(node) != connected) available.add(target(node));
            }
        }
        return new WirelessPageSnapshotPayload(request.containerId(), request.requestId(), machine.getBlockPos(), Optional.ofNullable(linked), available);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterServerTick(ServerTickEvent.Post event) {
        // NodeConn/MatrixNetworks have applied deferred removals at NORMAL priority before rebuilding.
        for (var pending : List.copyOf(PENDING.values())) {
            if (pending.player.server != event.getServer()) continue;
            PENDING.remove(pending.player.getUUID());
            if (!pending.player.level().dimension().equals(pending.dimension)) continue;
            var machine = source(pending.player, pending.request);
            if (machine == null) continue;
            var response = snapshot(pending.player, machine, pending.request);
            if (pending.request.containerId() >= 0) menu(pending.player, pending.request).authorizeWirelessTargets(response.available());
            else DEVELOPERS.put(pending.player.getUUID(), new DeveloperAuthorization(pending.dimension,
                    pending.request.pos(), pending.request.containerId(), response.available().stream()
                    .map(WirelessPageSnapshotPayload.Target::pos).collect(java.util.stream.Collectors.toSet())));
            PacketDistributor.sendToPlayer(pending.player, response);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PENDING.values().removeIf(pending -> pending.player.server == event.getServer());
        DEVELOPERS.clear();
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        DEVELOPERS.remove(event.getEntity().getUUID());
    }
    private WirelessPageService() {}
}
