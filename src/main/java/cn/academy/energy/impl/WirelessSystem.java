package cn.academy.energy.impl;

import cn.academy.AcademyCraft;
import cn.academy.energy.MatrixNetworks;
import cn.academy.energy.NodeConnections;
import cn.academy.energy.WirelessPasswords;
import cn.academy.energy.api.block.IWirelessUser;
import cn.academy.event.energy.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Original wireless event operations; earlier listeners can veto cancelable operations before mutation. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WirelessSystem {
    public static final WirelessSystem INSTANCE = new WirelessSystem();
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void create(CreateNetworkEvent event) {
        if (!(event.getWorld() instanceof ServerLevel level)) { event.fail(3); return; }
        MatrixNetworks.get(level).create(event.mat, event.ssid, WirelessPasswords.hash(event.pwd), !event.pwd.isEmpty());
        MatrixNetworks.get(level).rememberPassword(event.mat, event.pwd);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void destroy(DestroyNetworkEvent event) {
        if (event.getWorld() instanceof ServerLevel level) MatrixNetworks.get(level).dispose(level, event.getTileEntity().getBlockPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void changePassword(ChangePassEvent event) {
        if (!(event.getWorld() instanceof ServerLevel level)) { event.fail(3); return; }
        var data = MatrixNetworks.get(level);
        if (!data.changePassword(event.mat, WirelessPasswords.hash(event.pwd), !event.pwd.isEmpty())) event.fail(3);
        else data.rememberPassword(event.mat, event.pwd);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void linkNode(LinkNodeEvent event) {
        if (!(event.getWorld() instanceof ServerLevel level)) { event.fail(3); return; }
        int result = MatrixNetworks.get(level).link(event.node, event.matrix, WirelessPasswords.hash(event.pwd));
        if (result != 0) event.fail(result);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void unlinkNode(UnlinkNodeEvent event) {
        if (event.getWorld() instanceof ServerLevel level) MatrixNetworks.get(level).unlink(event.getTileEntity().getBlockPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void linkUser(LinkUserEvent event) {
        if (!(event.getWorld() instanceof ServerLevel level)) { event.fail(3); return; }
        int result = NodeConnections.get(level).link((IWirelessUser) event.tile, event.node, event.password, event.needAuth);
        if (result != 0) event.fail(result);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void unlinkUser(UnlinkUserEvent event) {
        if (event.getWorld() instanceof ServerLevel level) NodeConnections.get(level).unlink(event.getTileEntity().getBlockPos());
    }
    private WirelessSystem() {}
}
