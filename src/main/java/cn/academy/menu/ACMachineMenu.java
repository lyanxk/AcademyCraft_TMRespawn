package cn.academy.menu;

import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.registry.ACMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ACMachineMenu extends AbstractContainerMenu {
    // Legacy TechUI drew its background left of GuiContainer's slot origin.
    // Both now share leftPos; restore the inset for items, highlights and hit testing together.
    private static final int SLOT_X_OFFSET = 2;
    private static final int MACHINE_SLOTS = 5;
    private final ACMachineBlockEntity machine;
    private final ContainerData data;
    private boolean inventoryPage = true;
    private static final java.util.concurrent.atomic.AtomicInteger WIRELESS_REQUEST_IDS = new java.util.concurrent.atomic.AtomicInteger();
    private int wirelessRequestId;
    private cn.academy.network.WirelessPageSnapshotPayload wirelessSnapshot;
    private java.util.Set<net.minecraft.core.BlockPos> wirelessAuthorizedTargets = java.util.Set.of();

    public int nextWirelessRequest() { return wirelessRequestId = WIRELESS_REQUEST_IDS.incrementAndGet(); }
    public cn.academy.network.WirelessPageSnapshotPayload wirelessSnapshot() { return wirelessSnapshot; }
    public boolean acceptWirelessSnapshot(cn.academy.network.WirelessPageSnapshotPayload snapshot) {
        if (machine == null || snapshot.containerId() != containerId || snapshot.requestId() != wirelessRequestId
                || !snapshot.pos().equals(machine.getBlockPos())) return false;
        wirelessSnapshot = snapshot;
        return true;
    }
    public void authorizeWirelessTargets(java.util.List<cn.academy.network.WirelessPageSnapshotPayload.Target> targets) {
        wirelessAuthorizedTargets = targets.stream().map(cn.academy.network.WirelessPageSnapshotPayload.Target::pos)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public boolean isWirelessTargetAuthorized(net.minecraft.core.BlockPos target) { return wirelessAuthorizedTargets.contains(target); }

    public ACMachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory,
                inventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof ACMachineBlockEntity found ? found : null);
    }

    public ACMachineMenu(int containerId, Inventory inventory, ACMachineBlockEntity machine) {
        super(ACMenus.MACHINE.get(), containerId);
        this.machine = machine;
        ItemStackHandler handler = machine == null ? new ItemStackHandler(MACHINE_SLOTS) : machine.items;
        this.data = machine == null ? new SimpleContainerData(10) : createData(machine);

        int[][] layout = machineLayout(machine == null ? null : machine.kind());
        for (int index = 0; index < MACHINE_SLOTS; index++) {
            final int slotIndex = index;
            addSlot(new SlotItemHandler(handler, index, layout[index][0] + SLOT_X_OFFSET, layout[index][1]) {
                @Override public boolean isActive() { return inventoryPage && layout[slotIndex][0] >= 0; }
                @Override public boolean mayPickup(Player player) { return isActive() && super.mayPickup(player); }
                @Override public boolean mayPlace(ItemStack stack) {
                    if (!isActive()) return false;
                    return machine != null && machine.acceptsGuiItem(slotIndex, stack) && super.mayPlace(stack);
                }
            });
        }

        // ContainAbilityInterferer exposes only its battery and the nine hotbar slots.
        if (machine == null || machine.kind() != cn.academy.block.MachineKind.ABILITY_INTERFERER)
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 6 + SLOT_X_OFFSET + column * 18, 105 + row * 18) {
                    @Override public boolean isActive() { return inventoryPage; }
                });
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 6 + SLOT_X_OFFSET + column * 18, 163) {
                @Override public boolean isActive() { return inventoryPage; }
            });
        }
        addDataSlots(data);
    }

    private static int[][] machineLayout(cn.academy.block.MachineKind kind) {
        int[][] hidden = {{-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
        if (kind == null) return hidden;
        return switch (kind) {
            case IMAG_FUSOR -> new int[][]{{13, 49}, {42, 80}, {143, 49}, {13, 10}, {143, 10}};
            case METAL_FORMER -> new int[][]{{13, 49}, {42, 80}, {143, 49}, {-100, -100}, {-100, -100}};
            case MATRIX -> new int[][]{{78, 11}, {53, 60}, {104, 60}, {78, 36}, {-100, -100}};
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED ->
                    new int[][]{{42, 10}, {42, 80}, {-100, -100}, {-100, -100}, {-100, -100}};
            case PHASE_GENERATOR ->
                    new int[][]{{42, 80}, {-100, -100}, {-100, -100}, {45, 12}, {112, 51}};
            case SOLAR_GENERATOR ->
                    new int[][]{{42, 81}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
            case WIND_BASE ->
                    new int[][]{{42, 80}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
            case WIND_GENERATOR ->
                    new int[][]{{78, 9}, {-100, -100}, {-100, -100}, {-100, -100}, {-100, -100}};
            case ABILITY_INTERFERER, DEVELOPER_NORMAL, DEVELOPER_ADVANCED ->
                    new int[][]{{-100, -100}, {139, 25}, {-100, -100}, {-100, -100}, {-100, -100}};
            case FE_INPUT, FE_OUTPUT, EU_INPUT, EU_OUTPUT -> hidden;
            case CAT_ENGINE, WIND_PILLAR -> hidden;
        };
    }

    private static ContainerData createData(ACMachineBlockEntity machine) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> machine.energy.getEnergyStored() & 0xFFFF;
                    case 1 -> machine.energy.getEnergyStored() >>> 16;
                    case 2 -> machine.energy.getMaxEnergyStored() & 0xFFFF;
                    case 3 -> machine.energy.getMaxEnergyStored() >>> 16;
                    case 4 -> machine.progress();
                    case 5 -> machine.maxProgress();
                    case 6 -> machine.mode();
                    case 7 -> machine.phaseLiquid();
                    case 8 -> machine.wirelessLoad();
                    case 9 -> machine.wirelessCapacity();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 10; }
        };
    }

    public ACMachineBlockEntity machine() { return machine; }
    public int energy() { return (data.get(1) << 16) | (data.get(0) & 0xFFFF); }
    public int maxEnergy() { return (data.get(3) << 16) | (data.get(2) & 0xFFFF); }
    public int progress() {
        return machine != null && machine.kind().isProcessor() && machine.getLevel() != null && machine.getLevel().isClientSide
                ? machine.progress() : data.get(4);
    }
    public int maxProgress() { return data.get(5); }
    public int mode() { return data.get(6); }
    public int phaseLiquid() { return data.get(7); }
    public int wirelessLoad() { return data.get(8); }
    public int wirelessCapacity() { return data.get(9); }
    public void setInventoryPage(boolean inventoryPage) { this.inventoryPage = inventoryPage; }
    public boolean inventoryPage() { return inventoryPage; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.isActive() || !slot.mayPickup(player) || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < MACHINE_SLOTS) {
            // TechUIContainer maps hotbar 0..8, then inventory rows from bottom to top.
            // CleanContainer visits each target once (empty slots before later partial stacks).
            int hotbar = slots.size() - 9;
            for (int target = hotbar; target < slots.size() && !source.isEmpty(); target++)
                moveToSlot(source, target);
            for (int row = hotbar - 9; row >= MACHINE_SLOTS && !source.isEmpty(); row -= 9)
                for (int column = 0; column < 9 && !source.isEmpty(); column++)
                    moveToSlot(source, row + column);
        } else {
            for (int target : quickMoveTargets(source)) {
                if (source.isEmpty()) break;
                moveToSlot(source, target);
            }
        }
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return copy;
    }

    private void moveToSlot(ItemStack source, int target) {
        Slot slot = slots.get(target);
        if (slot.isActive() && slot.mayPlace(source)) moveItemStackTo(source, target, target + 1, false);
    }

    private int[] quickMoveTargets(ItemStack source) {
        if (machine == null) return new int[0];
        boolean energy = cn.academy.energy.api.IFItemManager.instance.isSupported(source);
        boolean liquid = source.is(cn.academy.registry.ACItems.MATTER_UNIT.get())
                && cn.academy.item.MatterUnitItem.isFilled(source);
        return switch (machine.kind()) {
            case IMAG_FUSOR -> liquid ? new int[]{3} : energy ? new int[]{1} : new int[]{0};
            case METAL_FORMER -> cn.academy.support.EnergyItemHelper.isSupported(source) ? new int[]{1} : new int[]{0};
            case PHASE_GENERATOR -> energy ? new int[]{0} : liquid ? new int[]{3} : new int[0];
            case MATRIX -> source.is(cn.academy.registry.ACItems.MAT_CORE.get())
                    ? new int[]{3} : new int[]{0, 1, 2};
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> new int[]{0, 1};
            case FE_INPUT, FE_OUTPUT, EU_INPUT, EU_OUTPUT -> new int[0];
            case SOLAR_GENERATOR, WIND_BASE, WIND_GENERATOR -> new int[]{0};
            case ABILITY_INTERFERER, DEVELOPER_NORMAL, DEVELOPER_ADVANCED -> new int[]{1};
            case CAT_ENGINE, WIND_PILLAR -> new int[0];
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && !machine.isRemoved()
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }
}
