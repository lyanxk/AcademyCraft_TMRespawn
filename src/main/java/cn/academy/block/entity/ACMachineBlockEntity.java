package cn.academy.block.entity;

import cn.academy.data.GameplayRules;

import cn.academy.data.GameplayData;

import cn.academy.block.ACMachineBlock;
import cn.academy.block.MachineKind;
import cn.academy.crafting.MachineRecipes;
import cn.academy.support.EnergyItemHelper;
import cn.academy.energy.NodeConnections;
import cn.academy.energy.MatrixNetworks;
import cn.academy.energy.api.block.*;
import cn.academy.event.energy.*;
import net.neoforged.neoforge.common.NeoForge;
import cn.academy.registry.ACBlockEntities;
import cn.academy.registry.ACBlocks;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACFluids;
import cn.academy.menu.ACMachineMenu;
import cn.academy.item.MatrixCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Shared persistent IF/inventory implementation for AcademyCraft's machines and wireless network. */
public class ACMachineBlockEntity extends BlockEntity implements MenuProvider {
    private static final Map<ServerLevel, Set<ACMachineBlockEntity>> LOADED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final MachineKind kind;
    public final TrackedEnergyStorage energy;
    public final ItemStackHandler items;
    private MachineRecipes.Recipe currentRecipe;
    private ItemStack pendingRecipeInput = ItemStack.EMPTY;
    private int pendingRecipeMode;
    private int formerWorkCounter;
    private double fusorWorkProgress;
    private int fusorCheckCooldown = GameplayData.integer("machines/imag_fusor", "recipe_check_interval");
    private int mode;
    private int phaseLiquid;
    private long recipeRevision = -1;
    private int ticks;
    private java.util.UUID developerUser;
    private double lastGeneration;
    private float clientCatRotation;
    private long clientCatLastRender;
    private float clientWindRotation;
    private double clientWindLastRender = -1;
    private boolean windMainComplete, windMainNoObstacle;
    private ACMachineBlockEntity windMain;
    private float windBaseSearchCounter = 10 / 20f;
    public enum WindCompleteness { BASE_ONLY, NO_TOP, COMPLETE, COMPLETE_NOT_WORKING }
    private WindCompleteness windCompleteness = WindCompleteness.BASE_ONLY;
    private String networkId = "";
    private String networkPasswordHash = "";
    private String networkOwner = "";
    private String nodeName = "Unnamed";
    private String nodePasswordHash = "";
    private @Nullable String nodePasswordPlain = "";
    private boolean networkEncrypted;
    private boolean nodeEncrypted;
    private String nodeOwner = "";
    private boolean hasMatrixLink;
    private boolean matrixNetworkMigrated;
    private boolean networkInitialized;
    private long linkedMatrixPos;
    private boolean hasNodeLink;
    private boolean nodeConnectionMigrated;
    private long linkedNodePos;
    private String placerId = "";
    private String placerName = "";
    private boolean interfererEnabled = GameplayData.flag("machines/ability_interferer", "default_enabled");
    private double interfererRange = GameplayData.number("machines/ability_interferer", "minimum_range");
    // TickScheduler stores seconds as floats and pauses this counter while disabled.
    private float interfererCountdown = GameplayData.integer("machines/ability_interferer", "operation_interval_ticks") / 20f;
    private float interfererSyncCountdown = GameplayData.integer("machines/ability_interferer", "sync_interval_ticks") / 20f;
    private final java.util.SortedSet<String> interfererWhitelist = new java.util.TreeSet<>();

    public ACMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.MACHINE.get(), pos, state);
        this.kind = state.getBlock() instanceof ACMachineBlock machine ? machine.kind() : MachineKind.MATRIX;
        this.energy = new TrackedEnergyStorage(kind.capacity(), kind.transfer());
        this.items = new ItemStackHandler(5) {
            @Override
            protected void onContentsChanged(int slot) {
                ACMachineBlockEntity.this.changed();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return isValidAutomationInput(slot, stack);
            }

            @Override public int getSlotLimit(int slot) {
                return kind == MachineKind.MATRIX ? 1 : super.getSlotLimit(slot);
            }
        };
    }

    /** Role-specific block entities preserve the original instanceof IWireless* contract. */
    public static ACMachineBlockEntity create(BlockPos pos, BlockState state) {
        MachineKind kind = state.getBlock() instanceof ACMachineBlock block ? block.kind() : MachineKind.MATRIX;
        // Read legacy EU blocks without loading an adapter or consuming wireless power.
        if (kind == MachineKind.EU_INPUT || kind == MachineKind.EU_OUTPUT) return new ACMachineBlockEntity(pos, state);
        if (kind == MachineKind.MATRIX) return new WirelessMachineRoles.Matrix(pos, state);
        if (kind.isNetworkNode()) return new WirelessMachineRoles.Node(pos, state);
        if (kind.isGenerator() || kind.isBridgeInput()) return new WirelessMachineRoles.Generator(pos, state);
        if (kind == MachineKind.WIND_PILLAR || kind == MachineKind.WIND_GENERATOR) return new ACMachineBlockEntity(pos, state);
        return new WirelessMachineRoles.Receiver(pos, state);
    }

    public String wirelessNodePassword() {
        if (nodePasswordPlain == null) throw new IllegalStateException("This legacy port save contains only a password hash; reset the node password before reading its plaintext API value");
        return nodePasswordPlain;
    }

    public class TrackedEnergyStorage extends EnergyStorage {
        private TrackedEnergyStorage(int capacity, int transfer) {
            super(capacity, transfer, transfer);
        }
        private void refreshLimits() {
            capacity = kind.capacity(); maxReceive = maxExtract = kind.transfer();
            setExactEnergy(exactEnergy());
        }

        private double fraction;
        public void setEnergy(int value) { setExactEnergy(value); }
        public double exactEnergy() { return energy + fraction; }
        public void setExactEnergy(double value) {
            capacity = kind.capacity(); maxReceive = maxExtract = kind.transfer();
            double clamped = Double.isFinite(value) ? Math.max(0, Math.min(capacity, value)) : 0;
            energy = (int) clamped;
            fraction = clamped - energy;
        }
        /** TileReceiverBase.pullEnergy: insufficient supply is still drained before the caller aborts. */
        public double pullInternal(double amount) {
            double pulled = Math.min(Math.max(0, amount), exactEnergy());
            setExactEnergy(exactEnergy() - pulled);
            if (pulled > 0) ACMachineBlockEntity.this.changed();
            return pulled;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            // FE exposes whole units; keep the sub-unit IF remainder without overfilling the tank.
            int room = (int) Math.floor(capacity - exactEnergy());
            int received = super.receiveEnergy(Math.max(0, Math.min(maxReceive, room)), simulate);
            if (!simulate && received > 0) ACMachineBlockEntity.this.changed();
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(Math.max(0, maxExtract), simulate);
            if (!simulate && extracted > 0) ACMachineBlockEntity.this.changed();
            return extracted;
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ACMachineMenu(containerId, inventory, this);
    }

    public @Nullable IEnergyStorage externalEnergy() {
        // Native machines speak IF over the wireless system. Only FE bridges expose FE.
        // EU bridges must use an EU adapter, never pretend that FE is EU.
        if (kind != MachineKind.FE_INPUT && kind != MachineKind.FE_OUTPUT) return null;
        return new cn.academy.support.fe.FEEnergyStorage(energy::exactEnergy, this::setWirelessEnergy,
                kind::capacity, kind == MachineKind.FE_INPUT);
    }

    public MachineKind kind() { return kind; }
    /** Original inventory indices mapped to the shared internal storage layout. */
    private int[] inventorySlots() {
        return switch (kind) {
            case IMAG_FUSOR -> new int[]{0, 2, 3, 1, 4};
            case METAL_FORMER -> new int[]{0, 2, 1};
            case PHASE_GENERATOR -> new int[]{3, 4, 0};
            case MATRIX -> new int[]{0, 1, 2, 3};
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED, DEVELOPER_NORMAL, DEVELOPER_ADVANCED -> new int[]{0, 1};
            case SOLAR_GENERATOR, WIND_BASE, WIND_GENERATOR -> new int[]{0};
            case ABILITY_INTERFERER -> new int[]{1};
            case CAT_ENGINE, WIND_PILLAR, FE_INPUT, FE_OUTPUT, EU_INPUT, EU_OUTPUT -> new int[0];
        };
    }
    public @Nullable net.neoforged.neoforge.items.IItemHandler externalItems(@Nullable Direction side) {
        int[] inventory = inventorySlots();
        if (inventory.length == 0) return null;
        int[] exposed = side != null && kind.isProcessor() ? switch (side) {
            case UP -> kind == MachineKind.IMAG_FUSOR ? new int[]{0, 3} : new int[]{0};
            case DOWN -> kind == MachineKind.IMAG_FUSOR ? new int[]{2, 4, 1} : new int[]{2, 1};
            default -> new int[]{1};
        } : inventory;
        boolean extract = side == null || kind != MachineKind.ABILITY_INTERFERER
                && (!kind.isProcessor() || side == Direction.DOWN);
        return new net.neoforged.neoforge.items.IItemHandler() {
            private int mapped(int slot) {
                if (slot < 0 || slot >= exposed.length) throw new IndexOutOfBoundsException(slot);
                return exposed[slot];
            }
            @Override public int getSlots() { return exposed.length; }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(mapped(slot)); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return items.insertItem(mapped(slot), stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                int index = mapped(slot);
                return extract ? items.extractItem(index, amount, simulate) : ItemStack.EMPTY;
            }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(mapped(slot)); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return items.isItemValid(mapped(slot), stack); }
        };
    }

    public int progress() { return (int) Math.round(workProgress() * maxProgress()); }
    public double workProgress() {
        restoreProcessingRecipe();
        return kind == MachineKind.METAL_FORMER ? isWorkInProgress() ? formerWorkCounter / (double) currentRecipe.duration() : 0
                : kind == MachineKind.IMAG_FUSOR && currentRecipe != null ? fusorWorkProgress : 0;
    }
    public boolean isWorking() { restoreProcessingRecipe(); return currentRecipe != null; }
    public int currentLiquidRequirement() { restoreProcessingRecipe(); return currentRecipe == null ? 0 : currentRecipe.liquid(); }
    public boolean isWorkInProgress() {
        restoreProcessingRecipe();
        if (currentRecipe == null) return false;
        if (kind == MachineKind.METAL_FORMER) return !metalActionBlocked();
        return kind == MachineKind.IMAG_FUSOR && !fusorActionBlocked();
    }
    public int maxProgress() { restoreProcessingRecipe(); return currentRecipe == null ? 0 : currentRecipe.duration(); }
    public int mode() { return mode; }
    public int phaseLiquid() { return phaseLiquid; }
    public double lastGeneration() { return lastGeneration; }
    public float clientCatRotation(float partialTick) {
        long now = (long) (((double) (level == null ? 0 : level.getGameTime()) + partialTick) * 50);
        if (clientCatLastRender != 0) clientCatRotation = (float) ((clientCatRotation + (now - clientCatLastRender) * lastGeneration * .01) % 360);
        clientCatLastRender = now;
        return clientCatRotation;
    }
    public float clientWindRotation(float partialTick) {
        double now = ((double) (level == null ? 0 : level.getGameTime()) + partialTick) / 20.0;
        double delta = clientWindLastRender < 0 ? 0 : now - clientWindLastRender;
        clientWindLastRender = now;
        clientWindRotation += windMainComplete ? 60 * delta : 0;
        return clientWindRotation;
    }
    public boolean isInterfererActive() { return kind == MachineKind.ABILITY_INTERFERER && interfererEnabled; }
    public double interfererRange() { return interfererRange; }
    public java.util.List<String> interfererWhitelist() { return java.util.List.copyOf(interfererWhitelist); }
    public int configureInterferer(ServerPlayer player, boolean enabled, double range, java.util.List<String> whitelist) {
        return configureInterferer(player, enabled, range, whitelist, cn.academy.network.InterfererConfigPayload.ALL);
    }
    public int configureInterferer(ServerPlayer player, boolean enabled, double range, java.util.List<String> whitelist, int field) {
        if (kind != MachineKind.ABILITY_INTERFERER || field < 0 || field > 3
                || ((field == 0 || field == 2) && !Double.isFinite(range))) return 3;
        // Original hSetEnabled/hSetRange/hSetWhitelist operate independently and are not owner-restricted.
        // ACNetwork still requires a nearby player with this machine's open container.
        if (field == 0 || field == 1) interfererEnabled = enabled;
        if (field == 0 || field == 2) {
            interfererRange = Math.clamp(range, GameplayData.number("machines/ability_interferer", "minimum_range"), GameplayData.number("machines/ability_interferer", "maximum_range"));
        }
        if (field == 0 || field == 3) {
            interfererWhitelist.clear();
            whitelist.stream().limit(64)
                    .forEach(value -> interfererWhitelist.add(value.length() > 32 ? value.substring(0, 32) : value));
            // setPlacer adds the owner once. Later whitelist edits can remove that entry in the original.
        }
        changed();
        sync();
        return 0;
    }
    /** Cached CPData result; acquisition is performed by a powered machine pulse, not a proximity query. */
    public static boolean interferesWith(ServerPlayer player) {
        return cn.academy.ability.AbilityState.load(player).interfered();
    }

    private void tickInterferer() {
        if (kind != MachineKind.ABILITY_INTERFERER || !(level instanceof ServerLevel serverLevel)) return;
        if (interfererEnabled) {
            interfererCountdown -= .05f;
            if (interfererCountdown <= 0) {
                double remaining = energy.exactEnergy() - interfererRange * interfererRange * GameplayData.number("machines/ability_interferer", "energy_per_range_squared");
                energy.setExactEnergy(Math.max(0, remaining));
                if (remaining > 0) {
                    var bounds = new net.minecraft.world.phys.AABB(worldPosition.getX() + .5 - interfererRange,
                            worldPosition.getY() + .5 - interfererRange, worldPosition.getZ() + .5 - interfererRange,
                            worldPosition.getX() + .5 + interfererRange, worldPosition.getY() + .5 + interfererRange,
                            worldPosition.getZ() + .5 + interfererRange);
                    String sourceName = "interferer@" + serverLevel.dimension().location() + " " + worldPosition;
                    for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, bounds, p -> !p.isCreative())) {
                        // The original captures this pulse's bounds. It does not consult whitelist_,
                        // recalculate range, or compare the player's world inside IInterfSource.
                        cn.academy.ability.AbilityState.load(player).addInterf(sourceName,
                                () -> legacyContains(bounds, player) && !isRemoved() && !player.isCreative() && interfererEnabled);
                    }
                } else interfererEnabled = false;
                changed();
                interfererCountdown += GameplayData.integer("machines/ability_interferer", "operation_interval_ticks") / 20f;
            }
        }
        interfererSyncCountdown -= .05f;
        if (interfererSyncCountdown <= 0) {
            sync();
            interfererSyncCountdown += GameplayData.integer("machines/ability_interferer", "sync_interval_ticks") / 20f;
        }
    }

    private static boolean legacyContains(net.minecraft.world.phys.AABB bounds, ServerPlayer player) {
        // 1.12.2 AxisAlignedBB.contains uses strict comparisons on both faces, unlike modern AABB.contains.
        return player.getX() > bounds.minX && player.getX() < bounds.maxX
                && player.getY() > bounds.minY && player.getY() < bounds.maxY
                && player.getZ() > bounds.minZ && player.getZ() < bounds.maxZ;
    }
    public String networkId() { return networkId; }
    public boolean networkInitialized() { return networkInitialized; }
    public String nodeName() { return nodeName; }
    public String placerName() { return placerName.isEmpty() ? "-" : placerName; }
    public boolean networkEncrypted() { return networkEncrypted; }
    public boolean nodeEncrypted() { return nodeEncrypted; }
    public @Nullable BlockPos linkedMatrixPos() { return hasMatrixLink ? BlockPos.of(linkedMatrixPos) : null; }
    public @Nullable BlockPos linkedNodePos() { return hasNodeLink ? BlockPos.of(linkedNodePos) : null; }
    public int wirelessLoad() {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        return kind == MachineKind.MATRIX ? MatrixNetworks.get(serverLevel).load(worldPosition)
                : NodeConnections.isNode(this) ? NodeConnections.get(serverLevel).load(worldPosition) : 0;
    }
    public int wirelessCapacity() {
        return switch (kind) {
            case MATRIX -> isMatrixWorking() ? GameplayRules.core(matrixCoreLevel()).get("connections").getAsInt() : 0;
            case NODE_BASIC -> GameplayData.integer(GameplayRules.machine(kind), "connections");
            case NODE_STANDARD -> GameplayData.integer(GameplayRules.machine(kind), "connections");
            case NODE_ADVANCED -> GameplayData.integer(GameplayRules.machine(kind), "connections");
            default -> 0;
        };
    }

    public void setPlacer(LivingEntity placer) {
        if (placer == null || !placerId.isEmpty()) return;
        placerId = placer.getStringUUID();
        placerName = placer.getName().getString();
        if (kind.isNetworkNode() && kind != MachineKind.MATRIX && nodeOwner.isEmpty()) nodeOwner = placerId;
        if (kind == MachineKind.ABILITY_INTERFERER) interfererWhitelist.add(placerName);
        changed();
        sync();
    }

    public int configureNode(ServerPlayer player, String requestedName, String password, boolean updatePassword) {
        if (!kind.isNetworkNode() || kind == MachineKind.MATRIX) return 3;
        if (!nodeOwner.isEmpty() && !nodeOwner.equals(player.getStringUUID()) && !player.hasPermissions(2)) return 1;
        String cleanName = requestedName == null ? "" : requestedName.strip();
        if (cleanName.isEmpty()) cleanName = "Unnamed";
        if (cleanName.length() > 24) cleanName = cleanName.substring(0, 24);
        nodeName = cleanName;
        if (updatePassword) {
            String pass = password == null ? "" : password;
            nodePasswordPlain = pass;
            nodePasswordHash = hashPassword(pass);
            nodeEncrypted = !pass.isEmpty();
        }
        if (nodeOwner.isEmpty()) nodeOwner = player.getStringUUID();
        changed();
        sync();
        return 0;
    }
    public @Nullable IFluidHandler phaseTank() {
        if (kind != MachineKind.IMAG_FUSOR && kind != MachineKind.PHASE_GENERATOR) return null;
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) {
                return tank == 0 && phaseLiquid > 0 ? new FluidStack(GameplayRules.fluid(GameplayData.string(GameplayRules.machine(kind), "fluid")), phaseLiquid) : FluidStack.EMPTY;
            }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? GameplayRules.tankCapacity(kind) : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return tank == 0 && stack.is(GameplayRules.fluid(GameplayData.string(GameplayRules.machine(kind), "fluid")));
            }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (!isFluidValid(0, resource)) return 0;
                int accepted = Math.min(resource.getAmount(), GameplayRules.tankCapacity(kind) - phaseLiquid);
                if (action.execute() && accepted > 0) {
                    phaseLiquid += accepted;
                    changed();
                    sync();
                }
                return accepted;
            }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) {
                return resource.is(GameplayRules.fluid(GameplayData.string(GameplayRules.machine(kind), "fluid"))) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int maxDrain, FluidAction action) {
                int drained = Math.min(Math.max(0, maxDrain), phaseLiquid);
                if (drained <= 0) return FluidStack.EMPTY;
                if (action.execute()) {
                    phaseLiquid -= drained;
                    changed();
                    sync();
                }
                return new FluidStack(GameplayRules.fluid(GameplayData.string(GameplayRules.machine(kind), "fluid")), drained);
            }
        };
    }
    public boolean isWirelessConfigurable() { return this instanceof IWirelessUser || this instanceof IWirelessNode || this instanceof IWirelessMatrix; }
    public boolean isMatrixWorking() {
        return kind == MachineKind.MATRIX && items.getStackInSlot(0).is(GameplayRules.item(GameplayData.string("wireless", "matrix_material")))
                && items.getStackInSlot(1).is(GameplayRules.item(GameplayData.string("wireless", "matrix_material")))
                && items.getStackInSlot(2).is(GameplayRules.item(GameplayData.string("wireless", "matrix_material")))
                && items.getStackInSlot(3).is(GameplayRules.item(GameplayData.string("wireless", "matrix_core")));
    }
    public int matrixTier() { return matrixCoreLevel(); }
    public double wirelessRange() {
        return switch (kind) {
            case MATRIX -> matrixRange();
            case NODE_BASIC -> GameplayData.number(GameplayRules.machine(kind), "range");
            case NODE_STANDARD -> GameplayData.number(GameplayRules.machine(kind), "range");
            case NODE_ADVANCED -> GameplayData.number(GameplayRules.machine(kind), "range");
            default -> 0;
        };
    }
    private int matrixCoreLevel() {
        return kind == MachineKind.MATRIX && items.getStackInSlot(3).is(GameplayRules.item(GameplayData.string("wireless", "matrix_core")))
                ? MatrixCoreItem.level(items.getStackInSlot(3)) : 0;
    }
    private double matrixRange() { return isMatrixWorking() ? GameplayRules.core(matrixCoreLevel()).get("range").getAsDouble() : 0; }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            LOADED.computeIfAbsent(serverLevel, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            initializeMatrixNetwork();
            initializeNodeConnection();
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            Set<ACMachineBlockEntity> machines = LOADED.get(serverLevel);
            if (machines != null) machines.remove(this);
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ACMachineBlockEntity machine) {
        machine.energy.refreshLimits();
        if (machine.kind == MachineKind.IMAG_FUSOR || machine.kind == MachineKind.PHASE_GENERATOR)
            machine.phaseLiquid = Math.min(machine.phaseLiquid, GameplayRules.tankCapacity(machine.kind));
        if (machine.kind == MachineKind.ABILITY_INTERFERER) machine.interfererRange = Math.clamp(machine.interfererRange,
                GameplayData.number("machines/ability_interferer", "minimum_range"), GameplayData.number("machines/ability_interferer", "maximum_range"));
        machine.ticks++;
        if (machine.ticks % 20 == 1) machine.reconcileWirelessLink();
        machine.generate();
        // Original base classes send an immediate snapshot before subclass inventory/work updates.
        machine.syncBeforeMachineWork();
        machine.updateWindState();
        if (machine.kind == MachineKind.WIND_GENERATOR && machine.ticks % 20 == 0)
            machine.sendSnapshot(50, machine.getUpdateTag(level.registryAccess()));
        if (machine.kind.isNetworkNode() && machine.kind != MachineKind.MATRIX && machine.ticks % 10 == 0)
            machine.updateVisualState();
        if (!machine.kind.isProcessor() && !machine.isDeveloper() && machine.kind != MachineKind.ABILITY_INTERFERER) {
            machine.acceptPhaseContainers();
            machine.transferContainedEnergy();
        }
        if (machine.kind == MachineKind.FE_OUTPUT) machine.pushAdjacentFE();
        machine.process();
        machine.updateWorkingLight();
        if (machine.kind.isProcessor()) {
            // Both legacy processors perform work BEFORE accepting liquid containers/battery energy.
            machine.acceptPhaseContainers();
            machine.transferContainedEnergy();
            if (machine.kind == MachineKind.METAL_FORMER && machine.ticks % 10 == 0)
                machine.syncMetalFormer();
        }
        if (machine.kind == MachineKind.ABILITY_INTERFERER) {
            machine.tickInterferer();
            machine.transferContainedEnergy();
        }
        if (!machine.kind.isNetworkNode() && machine.ticks % 5 == 0) machine.updateVisualState();
        if (machine.isDeveloper() && machine.ticks % 20 == 0) machine.syncDeveloperUser(false);
    }

    private void updateWorkingLight() {
        if (kind != MachineKind.IMAG_FUSOR || level == null) return;
        BlockState state = getBlockState();
        boolean working = isWorking();
        if (state.getValue(ACMachineBlock.WORKING) != working)
            level.setBlock(worldPosition, state.setValue(ACMachineBlock.WORKING, working), Block.UPDATE_CLIENTS);
    }

    private void syncBeforeMachineWork() {
        if (level == null) return;
        if (kind == MachineKind.MATRIX) {
            if (ticks % 15 == 0) sendSnapshot(25, getUpdateTag(level.registryAccess()));
        } else if (kind.isNetworkNode()) {
            if (ticks % 10 == 0) sendSnapshot(20, getUpdateTag(level.registryAccess()));
        } else if (this instanceof IWirelessGenerator) {
            if (ticks % 20 == 0) sendSnapshot(20, getUpdateTag(level.registryAccess()));
            if (kind == MachineKind.PHASE_GENERATOR && ticks % 10 == 0)
                sendSnapshot(15, getUpdateTag(level.registryAccess()));
        } else if (this instanceof IWirelessReceiver && ticks % 20 == 0) {
            sendSnapshot(15, getUpdateTag(level.registryAccess()));
        }
    }

    private void updateVisualState() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (state.hasProperty(ACMachineBlock.VISUAL_STAGE)) {
            int visualStage = switch (kind) {
                case IMAG_FUSOR -> isWorkInProgress() ? 1 + (ticks / 5) % 4 : 0;
                case ABILITY_INTERFERER -> interfererEnabled ? 1 : 0;
                case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> (int) Math.min(4, Math.round(
                        4 * energy.exactEnergy() / Math.max(1, energy.getMaxEnergyStored())));
                default -> 0;
            };
            boolean connected = kind.isNetworkNode() && kind != MachineKind.MATRIX && hasMatrixLink;
            BlockState visualState = state.setValue(ACMachineBlock.VISUAL_STAGE, visualStage)
                    .setValue(ACMachineBlock.CONNECTED, connected);
            if (!visualState.equals(state)) level.setBlock(worldPosition, visualState, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ACMachineBlockEntity machine) {
        machine.energy.refreshLimits();
        if (machine.kind == MachineKind.IMAG_FUSOR || machine.kind == MachineKind.PHASE_GENERATOR)
            machine.phaseLiquid = Math.min(machine.phaseLiquid, GameplayRules.tankCapacity(machine.kind));
        if (machine.kind == MachineKind.ABILITY_INTERFERER) machine.interfererRange = Math.clamp(machine.interfererRange,
                GameplayData.number("machines/ability_interferer", "minimum_range"), GameplayData.number("machines/ability_interferer", "maximum_range"));
        machine.ticks++;
        if (machine.kind == MachineKind.METAL_FORMER) machine.restoreProcessingRecipe();
        if (machine.kind == MachineKind.IMAG_FUSOR) {
            // TileImagFusor also predicts an already-synchronized job on the client, but never selects a new one there.
            machine.restoreProcessingRecipe();
            machine.processImagFusor();
            machine.acceptPhaseContainers();
            machine.transferContainedEnergy();
        }
        machine.updateWindState();
        // Solar and wind-base updateChargeOut run on both sides in 1.12.2. Generation stays server-only.
        if (machine.kind == MachineKind.SOLAR_GENERATOR || machine.kind == MachineKind.WIND_BASE)
            machine.transferContainedEnergy();
        cn.academy.client.sound.ACMachineSounds.tick(machine);
    }

    private void generate() {
        if (level == null || level.isClientSide || !kind.isGenerator()) return;
        double required = energy.getMaxEnergyStored() - energy.exactEnergy();
        double generated;
        if (kind == MachineKind.PHASE_GENERATOR) {
            int drained = Math.min(phaseLiquid, (int) Math.min(GameplayData.integer("machines/phase_generator", "fluid_per_tick"), required / GameplayData.number("machines/phase_generator", "energy_per_mb")));
            phaseLiquid -= drained;
            generated = drained * GameplayData.number("machines/phase_generator", "energy_per_mb");
        } else {
            double potential = switch (kind) {
                case SOLAR_GENERATOR -> solarGeneration();
                case WIND_BASE -> simulatedWindGeneration();
                case CAT_ENGINE -> kind.generation();
                default -> 0;
            };
            generated = Math.min(required, potential);
        }
        // Generation is internal addition, not a bandwidth-limited FE receive operation.
        energy.setExactEnergy(energy.exactEnergy() + generated);
        if (generated > 0 || lastGeneration != generated) changed();
        lastGeneration = generated;
    }

    public boolean solarCanGenerate() {
        if (level == null) return false;
        long time = level.getDayTime() % 24000;
        return time >= GameplayData.integer("machines/solar_generator", "day_start") && time <= GameplayData.integer("machines/solar_generator", "day_end") && level.canSeeSky(worldPosition.above());
    }
    public double solarGeneration() { return solarCanGenerate() ? kind.generation() * (level.isRaining() ? GameplayData.number("machines/solar_generator", "rain_multiplier") : 1) : 0; }

    public WindCompleteness windCompleteness() {
        return windCompleteness == WindCompleteness.COMPLETE && !windShouldGenerate()
                ? WindCompleteness.COMPLETE_NOT_WORKING : windCompleteness;
    }
    private boolean windShouldGenerate() {
        return windCompleteness == WindCompleteness.COMPLETE && windMain != null && windMain.windMainNoObstacle
                && windMain.items.getStackInSlot(0).is(ACItems.WINDGEN_FAN.get());
    }
    public double simulatedWindGeneration() {
        return windShouldGenerate() ? GameplayData.number("machines/wind_base", "generation") * (GameplayData.number("machines/wind_base", "minimum_output_multiplier") + (1 - GameplayData.number("machines/wind_base", "minimum_output_multiplier")) * Math.clamp((windMain.worldPosition.getY() - GameplayData.number("machines/wind_base", "minimum_height")) / GameplayData.number("machines/wind_base", "height_span"), 0, 1)) : 0;
    }
    private void updateWindState() {
        if (level == null) return;
        if (kind == MachineKind.WIND_GENERATOR && ticks % 10 == 0) {
            windMainComplete = findWindBase() != null;
            windMainNoObstacle = windMainComplete && windNoObstacle();
        } else if (kind == MachineKind.WIND_BASE) {
            // TickScheduler.every(10): preserve its float decrement/accumulation, after this tick's generation.
            windBaseSearchCounter -= .05f;
            if (windBaseSearchCounter <= 0) {
                updateWindMain();
                windBaseSearchCounter += 10 / 20f;
            }
        }
    }
    private void updateWindMain() {
        int pillars = 0;
        windMain = null;
        for (int distance = 2; distance <= 42; distance++) {
            BlockPos pos = worldPosition.above(distance);
            if (level.getBlockState(pos).is(ACBlocks.WINDGEN_PILLAR.get())) {
                if (++pillars > 40) { windCompleteness = WindCompleteness.NO_TOP; return; }
            } else if (level.getBlockEntity(pos) instanceof ACMachineBlockEntity main && main.kind == MachineKind.WIND_GENERATOR) {
                if (pillars >= GameplayData.integer("machines/wind_base", "required_pillars")) { windMain = main; windCompleteness = WindCompleteness.COMPLETE; }
                else windCompleteness = WindCompleteness.NO_TOP;
                return;
            } else {
                windCompleteness = pillars < 8 ? WindCompleteness.BASE_ONLY : WindCompleteness.NO_TOP;
                return;
            }
        }
        windCompleteness = WindCompleteness.NO_TOP;
    }

    private boolean windNoObstacle() {
        if (level == null || kind != MachineKind.WIND_GENERATOR
                || !getBlockState().hasProperty(ACMachineBlock.FACING)) return false;
        Direction normal = getBlockState().getValue(ACMachineBlock.FACING);
        Direction horizontal = normal.getClockWise();
        BlockPos center = worldPosition.relative(normal);
        for (int side = -7; side <= 7; side++) {
            for (int vertical = -7; vertical <= 7; vertical++) {
                if (side == 0 && vertical == 0) continue;
                BlockPos check = center.relative(horizontal, side).above(vertical);
                if (!level.getBlockState(check).isAir()) return false;
            }
        }
        return true;
    }

    public boolean windNoObstacleForRender() { return windMainNoObstacle; }

    public boolean windStructureCompleteForRender() {
        return kind == MachineKind.WIND_GENERATOR ? windMainComplete
                : kind == MachineKind.WIND_BASE && windCompleteness == WindCompleteness.COMPLETE;
    }

    private @Nullable ACMachineBlockEntity findWindBase() {
        if (level == null) return null;
        int pillars = 0;
        for (int distance = 1; distance <= 42; distance++) {
            BlockPos belowPos = worldPosition.below(distance);
            BlockState below = level.getBlockState(belowPos);
            if (below.is(ACBlocks.WINDGEN_PILLAR.get())) {
                if (++pillars > 40) return null;
            } else if (pillars >= GameplayData.integer("machines/wind_base", "required_pillars") && below.is(ACBlocks.WINDGEN_BASE.get())
                    && level.getBlockEntity(belowPos) instanceof ACMachineBlockEntity base) return base;
            else if (pillars >= GameplayData.integer("machines/wind_base", "required_pillars") && level.getBlockEntity(belowPos) instanceof ACMultiblockPartEntity part
                    && level.getBlockEntity(part.origin()) instanceof ACMachineBlockEntity base
                    && base.kind == MachineKind.WIND_BASE) return base;
            else return null;
        }
        return null;
    }

    private void acceptPhaseContainers() {
        if (kind != MachineKind.IMAG_FUSOR && kind != MachineKind.PHASE_GENERATOR) return;
        ItemStack input = items.getStackInSlot(3);
        ItemStack output = items.getStackInSlot(4);
        if (!input.is(ACItems.MATTER_UNIT.get()) || (!cn.academy.item.MatterUnitItem.isFilled(input) || !cn.academy.item.MatterUnitItem.getMaterial(input).fluid().get().isSame(GameplayRules.fluid(GameplayData.string(GameplayRules.machine(kind), "fluid"))))
                || phaseLiquid + GameplayData.integer("matter", "capacity") > GameplayRules.tankCapacity(kind) || kind == MachineKind.PHASE_GENERATOR && phaseLiquid + GameplayData.integer("matter", "capacity") == GameplayRules.tankCapacity(kind)
                || !output.isEmpty() && (!output.is(ACItems.MATTER_UNIT.get()) || output.getCount() >= output.getMaxStackSize()
                        || kind == MachineKind.PHASE_GENERATOR && cn.academy.item.MatterUnitItem.isFilled(output))) return;
        items.extractItem(3, 1, false);
        ItemStack empty = new ItemStack(ACItems.MATTER_UNIT.get());
        if (output.isEmpty()) items.setStackInSlot(4, empty);
        else output.grow(1);
        phaseLiquid += GameplayData.integer("matter", "capacity");
        changed();
    }

    private void transferContainedEnergy() {
        int[] slots = switch (kind) {
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> new int[]{0, 1};
            case SOLAR_GENERATOR, PHASE_GENERATOR, WIND_BASE -> new int[]{0};
            case IMAG_FUSOR, METAL_FORMER, ABILITY_INTERFERER -> new int[]{1};
            default -> new int[0];
        };
        boolean nativeOnly = kind.isNetworkNode() || kind.isGenerator() || kind == MachineKind.WIND_BASE;
        var nativeManager = cn.academy.energy.api.IFItemManager.instance;
        for (int slot : slots) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!(nativeOnly ? nativeManager.isSupported(stack) : EnergyItemHelper.isSupported(stack))) continue;
            double before = energy.exactEnergy();
            boolean chargeItems = kind.isNetworkNode() ? slot == 1 : kind.isGenerator() || kind == MachineKind.WIND_BASE;
            if (chargeItems) {
                double offered = Math.min(kind.transfer(), before);
                double left = nativeManager.charge(stack, offered, false);
                energy.setExactEnergy(before - (offered - left));
            } else {
                double request = Math.min(kind.transfer(), energy.getMaxEnergyStored() - before);
                double pulled = nativeOnly ? nativeManager.pull(stack, request, false) : EnergyItemHelper.pull(stack, request, false);
                energy.setExactEnergy(before + pulled);
            }
            if (energy.exactEnergy() != before) changed();
        }
    }

    private void process() {
        if (!kind.isProcessor() || level == null) return;
        restoreProcessingRecipe();
        if (kind == MachineKind.METAL_FORMER) processMetalFormer();
        else processImagFusor();
        changed();
        // Periodic snapshots are sent by the base lifecycle and, for the former, after battery transfer.
    }

    private void restoreProcessingRecipe() {
        if (level == null) return;
        long revision = MachineRecipes.revision();
        if (recipeRevision != revision) {
            recipeRevision = revision;
            if (currentRecipe != null) {
                currentRecipe = null; formerWorkCounter = 0; fusorWorkProgress = 0;
            }
        }
        if (pendingRecipeInput.isEmpty()) return;
        currentRecipe = MachineRecipes.find(level, kind == MachineKind.IMAG_FUSOR
                ? MachineRecipes.Kind.IMAG_FUSOR : MachineRecipes.Kind.METAL_FORMER, pendingRecipeMode, pendingRecipeInput);
        pendingRecipeInput = ItemStack.EMPTY;
        if (currentRecipe == null) { formerWorkCounter = 0; fusorWorkProgress = 0; }
    }

    private void processMetalFormer() {
        if (currentRecipe != null) {
            if (!metalActionBlocked() && energy.pullInternal(currentRecipe.energy()) == currentRecipe.energy()) {
                if (++formerWorkCounter >= currentRecipe.duration()) {
                    finishRecipe(currentRecipe);
                    currentRecipe = null;
                    formerWorkCounter = 0;
                }
            } else {
                currentRecipe = null;
                formerWorkCounter = 0;
            }
        } else if (++formerWorkCounter >= GameplayData.integer("machines/metal_former", "recipe_check_interval")) {
            currentRecipe = MachineRecipes.find(level, MachineRecipes.Kind.METAL_FORMER, mode, items.getStackInSlot(0));
            formerWorkCounter = 0;
        }
    }

    private boolean metalActionBlocked() {
        if (currentRecipe == null || currentRecipe.mode().ordinal() != mode || !currentRecipe.accepts(items.getStackInSlot(0))) return true;
        ItemStack output = items.getStackInSlot(2), result = currentRecipe.output();
        return !canOutput(result);
    }

    private void processImagFusor() {
        if (currentRecipe == null && !level.isClientSide && --fusorCheckCooldown <= 0) {
            fusorCheckCooldown = GameplayData.integer("machines/imag_fusor", "recipe_check_interval");
            currentRecipe = MachineRecipes.find(level, MachineRecipes.Kind.IMAG_FUSOR, 0, items.getStackInSlot(0));
            if (currentRecipe != null) fusorWorkProgress = 0;
        }
        if (currentRecipe == null) return;
        ItemStack input = items.getStackInSlot(0), output = items.getStackInSlot(2);
        // Preserve short-circuit order: bad liquid/output can consume energy; a missing input cannot.
        if (input.isEmpty() || !currentRecipe.matchesItem(input)
                || energy.pullInternal(currentRecipe.energy()) != currentRecipe.energy() || phaseLiquid < currentRecipe.liquid()
                || !output.isEmpty() && !ItemStack.isSameItem(output, currentRecipe.output())) {
            currentRecipe = null;
            fusorWorkProgress = 0;
            return;
        }
        if (!fusorActionBlocked()) {
            fusorWorkProgress += 1.0 / currentRecipe.duration();
            if (fusorWorkProgress >= 1) {
                finishRecipe(currentRecipe);
                currentRecipe = null;
                fusorWorkProgress = 0;
                fusorCheckCooldown = 0;
            }
        }
    }

    private boolean fusorActionBlocked() {
        return currentRecipe == null || !GameplayRules.recipeFluid(currentRecipe, kind) || items.getStackInSlot(0).getCount() < currentRecipe.inputCount()
                || phaseLiquid < currentRecipe.liquid() || !canOutput(currentRecipe.output());
    }

    private void finishRecipe(MachineRecipes.Recipe recipe) {
        phaseLiquid -= recipe.liquid();
        if (level != null && level.isClientSide) return;
        items.extractItem(0, recipe.inputCount(), false);
        ItemStack result = recipe.result();
        ItemStack existing = items.getStackInSlot(2);
        if (existing.isEmpty()) items.setStackInSlot(2, result);
        else existing.grow(result.getCount());
        changed();
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = items.getStackInSlot(2);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private boolean isValidAutomationInput(int slot, ItemStack stack) {
        boolean present = java.util.Arrays.stream(inventorySlots()).anyMatch(index -> index == slot);
        if (!present) return false;
        if (kind == MachineKind.MATRIX) return slot < 3 ? stack.is(GameplayRules.item(GameplayData.string("wireless", "matrix_material"))) : stack.is(GameplayRules.item(GameplayData.string("wireless", "matrix_core")));
        if (kind == MachineKind.WIND_GENERATOR) return stack.is(ACItems.WINDGEN_FAN.get());
        // TileInventory accepts arbitrary stacks; GUI Slot classes impose their own restrictions.
        return true;
    }

    public boolean acceptsGuiItem(int slot, ItemStack stack) {
        if (slot == 4) return (kind == MachineKind.IMAG_FUSOR || kind == MachineKind.PHASE_GENERATOR)
                && stack.is(ACItems.MATTER_UNIT.get()) && cn.academy.item.MatterUnitItem.isFilled(stack);
        if (slot == 2 && kind != MachineKind.MATRIX) return false;
        return switch (kind) {
            case IMAG_FUSOR -> slot == 0 && cn.academy.crafting.MachineRecipes.acceptsInput(level,
                    cn.academy.crafting.MachineRecipes.Kind.IMAG_FUSOR, stack)
                    || slot == 1 && cn.academy.energy.api.IFItemManager.instance.isSupported(stack)
                    || slot == 3 && stack.is(ACItems.MATTER_UNIT.get()) && cn.academy.item.MatterUnitItem.isFilled(stack);
            case METAL_FORMER -> slot == 0 && isMetalFormerInput(stack) || slot == 1 && cn.academy.energy.api.IFItemManager.instance.isSupported(stack);
            case WIND_GENERATOR -> slot == 0 && stack.is(ACItems.WINDGEN_FAN.get());
            case WIND_BASE -> slot == 0 && cn.academy.energy.api.IFItemManager.instance.isSupported(stack);
            case PHASE_GENERATOR -> slot == 0 && cn.academy.energy.api.IFItemManager.instance.isSupported(stack)
                    || slot == 3 && stack.is(ACItems.MATTER_UNIT.get()) && cn.academy.item.MatterUnitItem.isFilled(stack);
            case MATRIX -> slot <= 2 && stack.is(GameplayRules.item(GameplayData.string("wireless", "matrix_material")))
                    || slot == 3 && stack.is(GameplayRules.item(GameplayData.string("wireless", "matrix_core")));
            case SOLAR_GENERATOR -> slot == 0 && cn.academy.energy.api.IFItemManager.instance.isSupported(stack);
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> slot < 2 && cn.academy.energy.api.IFItemManager.instance.isSupported(stack);
            case FE_INPUT, FE_OUTPUT, EU_INPUT, EU_OUTPUT -> false;
            case ABILITY_INTERFERER -> slot == 1 && stack.is(ACItems.ENERGY_UNIT.get());
            case DEVELOPER_NORMAL, DEVELOPER_ADVANCED -> slot < 2; // Original unused TileInventory slots accept arbitrary items.
            case CAT_ENGINE, WIND_PILLAR -> false;
        };
    }

    private boolean isMetalFormerInput(ItemStack stack) {
        return cn.academy.crafting.MachineRecipes.acceptsInput(level,
                cn.academy.crafting.MachineRecipes.Kind.METAL_FORMER, stack);
    }

    public ItemStack insert(ItemStack held) {
        if (held.isEmpty()) return held;
        ItemStack remainder = held;
        for (int slot = 0; slot < items.getSlots(); slot++) remainder = items.insertItem(slot, remainder, false);
        return remainder;
    }

    public ItemStack takeOutput() {
        return items.extractItem(2, 64, false);
    }

    public void cycleMode() { cycleMode(1); }

    public void cycleMode(int direction) {
        if (kind == MachineKind.METAL_FORMER) {
            int next = mode + direction;
            mode = next >= 4 ? 0 : next < 0 ? 3 : next;
            // The original retains the current job until the following work tick validates its mode.
            changed();
            syncMetalFormer();
        }
    }

    public boolean fillPhaseLiquid(int amount) {
        if ((kind != MachineKind.IMAG_FUSOR && kind != MachineKind.PHASE_GENERATOR)
                || phaseLiquid + amount > GameplayRules.tankCapacity(kind)) return false;
        phaseLiquid += amount;
        changed();
        sync();
        return true;
    }

    public java.util.UUID developerUser() { return developerUser; }

    /** Transient TileDeveloper.user, deliberately excluded from saved NBT. */
    public void setDeveloperUser(java.util.UUID user) { developerUser = user; }

    public boolean useDeveloper(ServerPlayer player) {
        if (!(level instanceof ServerLevel) || !isDeveloper() || developerUser != null) return false;
        developerUser = player.getUUID();
        syncDeveloperUser(true);
        return true;
    }

    public void releaseDeveloper(Player player) {
        if (level != null && !level.isClientSide && player.getUUID().equals(developerUser)) developerUser = null;
    }

    public static void releaseDevelopers(Player player) {
        // A disconnected or transferred player can no longer send the old Screen's close callback.
        synchronized (LOADED) {
            for (var machines : LOADED.values()) for (var machine : Set.copyOf(machines)) machine.releaseDeveloper(player);
        }
    }

    private boolean isDeveloper() { return kind == MachineKind.DEVELOPER_NORMAL || kind == MachineKind.DEVELOPER_ADVANCED; }

    private void syncDeveloperUser(boolean open) {
        if (!(level instanceof ServerLevel server)) return;
        var payload = new cn.academy.network.DeveloperUserPayload(worldPosition, java.util.Optional.ofNullable(developerUser), open);
        for (ServerPlayer viewer : server.players()) {
            if (viewer.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) <= 100)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    public boolean consumeDevelopmentEnergy(int amount) {
        if (amount < 0 || energy.exactEnergy() < amount) return false;
        energy.setExactEnergy(energy.exactEnergy() - amount);
        changed();
        return true;
    }

    /** TileRFOutput.update: visit all six receivers in enum order, offering the whole buffer. */
    private void pushAdjacentFE() {
        if (level == null) return;
        IEnergyStorage output = externalEnergy();
        for (Direction direction : Direction.values()) {
            int available = output.getEnergyStored();
            if (available <= 0) break;
            BlockPos targetPos = worldPosition.relative(direction);
            if (!level.hasChunkAt(targetPos)) continue;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    targetPos, direction.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int room = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0L, (long) target.getMaxEnergyStored() - target.getEnergyStored()));
            int offered = Math.min(available, room);
            if (offered <= 0) continue;
            int accepted = Math.max(0, Math.min(offered, target.receiveEnergy(offered, false)));
            output.extractEnergy(accepted, false);
        }
    }

    public int wirelessBandwidth() {
        return kind == MachineKind.MATRIX ? (isMatrixWorking() ? GameplayRules.core(matrixCoreLevel()).get("bandwidth").getAsInt() : 0) : kind.transfer();
    }

    /** @return 0 success, 1 permission denied, 2 network/password not found, 3 invalid/capacity full. */
    public int configureNetwork(ServerPlayer player, String requestedId, String password) {
        if (!(level instanceof ServerLevel serverLevel) || !isWirelessConfigurable()) return 3;
        initializeMatrixNetwork();
        initializeNodeConnection();
        String id = requestedId == null ? "" : requestedId.strip();
        if (id.length() > 24) id = id.substring(0, 24);
        if (id.isEmpty()) {
            if (!canConfigureWireless(player)) return 1;
            if (this instanceof IWirelessMatrix matrix) NeoForge.EVENT_BUS.post(new DestroyNetworkEvent(matrix));
            else if (this instanceof IWirelessNode node) NeoForge.EVENT_BUS.post(new UnlinkNodeEvent(node));
            else if (this instanceof IWirelessUser user) NeoForge.EVENT_BUS.post(new UnlinkUserEvent(user));
            else return 3;
            return 0;
        }
        if (kind == MachineKind.MATRIX) return configureMatrix(player, id, password, true);
        String pass = password == null ? "" : password;
        final String checkedId = id;
        Set<ACMachineBlockEntity> machines = LOADED.getOrDefault(serverLevel, Set.of());
        if (NodeConnections.isNode(this)) {
            ACMachineBlockEntity matrix = machines.stream().filter(value -> !value.isRemoved()
                    && value.kind == MachineKind.MATRIX && checkedId.equals(value.networkId)
                    && value.authenticateMatrix(pass) && worldPosition.distSqr(value.worldPosition)
                    <= value.matrixRange() * value.matrixRange()).findFirst().orElse(null);
            return matrix == null ? 2 : linkToMatrix(player, matrix, pass);
        }
        ACMachineBlockEntity node = machines.stream().filter(value -> !value.isRemoved() && NodeConnections.isNode(value)
                && checkedId.equals(value.networkId) && value.authenticateNode(pass)
                && worldPosition.distSqr(value.worldPosition) <= value.wirelessRange() * value.wirelessRange())
                .min(java.util.Comparator.comparingDouble(value -> worldPosition.distSqr(value.worldPosition))).orElse(null);
        return node == null ? 2 : linkToNode(player, node, pass);
    }

    private boolean canConfigureWireless(ServerPlayer player) {
        String owner = kind == MachineKind.MATRIX ? (networkOwner.isEmpty() ? placerId : networkOwner)
                : kind.isNetworkNode() ? (nodeOwner.isEmpty() ? placerId : nodeOwner) : placerId;
        return owner.isEmpty() || owner.equals(player.getStringUUID()) || player.hasPermissions(2);
    }


    private void updateNodeUsersNetwork(Set<ACMachineBlockEntity> machines, String newId) {
        long ownPos = worldPosition.asLong();
        for (ACMachineBlockEntity user : Set.copyOf(machines)) {
            if (user.hasNodeLink && user.linkedNodePos == ownPos) {
                user.networkId = newId;
                user.changed();
                user.sync();
            }
        }
    }

    /** Reconciles coordinate-backed links after either side was unloaded during rename/removal. */
    private void reconcileWirelessLink() {
        initializeMatrixNetwork();
        initializeNodeConnection();
    }

    public int configureMatrix(ServerPlayer player, String ssid, String password, boolean updatePassword) {
        if (!(this instanceof IWirelessMatrix matrix) || !(level instanceof ServerLevel serverLevel)) return 3;
        if (!canConfigureWireless(player)) return 1;
        initializeMatrixNetwork();
        String id = ssid == null ? "" : ssid;
        if (id.length() > 24) id = id.substring(0, 24);
        String pass = password == null ? "" : password;
        if (!networkInitialized) {
            var event = NeoForge.EVENT_BUS.post(new CreateNetworkEvent(matrix, id, pass));
            if (event.result() != 0) return event.result();
        } else {
            if (updatePassword) {
                var event = NeoForge.EVENT_BUS.post(new ChangePassEvent(matrix, pass));
                if (event.result() != 0) return event.result();
            }
            MatrixNetworks.get(serverLevel).rename(matrix, id);
        }
        networkOwner = player.getStringUUID();
        changed();
        return 0;
    }

    public boolean authenticateMatrix(String password) {
        return kind == MachineKind.MATRIX && networkInitialized
                && networkPasswordHash.equals(hashPassword(password == null ? "" : password));
    }

    public boolean authenticateNode(String password) {
        String pass = password == null ? "" : password;
        return kind.isNetworkNode() && kind != MachineKind.MATRIX
                && (nodePasswordHash.isEmpty() ? pass.isEmpty() : nodePasswordHash.equals(hashPassword(pass)));
    }

    /** Links this node to one explicitly authorized matrix. */
    public int linkToMatrix(ServerPlayer player, ACMachineBlockEntity matrix, String matrixPassword) {
        if (!(level instanceof ServerLevel) || !(this instanceof IWirelessNode node)
                || !(matrix instanceof IWirelessMatrix target)) return 3;
        initializeMatrixNetwork(); matrix.initializeMatrixNetwork();
        return NeoForge.EVENT_BUS.post(new LinkNodeEvent(node, target, matrixPassword == null ? "" : matrixPassword)).result();
    }

    /** Links this wireless machine/user to one explicitly authorized node. */
    public int linkToNode(ServerPlayer player, ACMachineBlockEntity node, String nodePassword) {
        if (!(level instanceof ServerLevel) || !(this instanceof IWirelessUser user)
                || !(node instanceof IWirelessNode target)) return 3;
        initializeNodeConnection();
        return NeoForge.EVENT_BUS.post(new LinkUserEvent(user, target, nodePassword == null ? "" : nodePassword)).result();
    }

    private void initializeMatrixNetwork() {
        if (!(level instanceof ServerLevel serverLevel) || !kind.isNetworkNode()) return;
        var data = MatrixNetworks.get(serverLevel);
        if (!matrixNetworkMigrated) {
            if (kind == MachineKind.MATRIX && !networkId.isEmpty() && !data.initialized(worldPosition))
                data.configure(this, networkId, networkPasswordHash, networkEncrypted);
            else if (NodeConnections.isNode(this) && hasMatrixLink)
                data.importNode(this, BlockPos.of(linkedMatrixPos), networkId);
        }
        data.mirror(this);
    }

    /** Mirror of dimension WirelessNet state; removing hardware does not destroy the network. */
    public void updateMatrixNetworkSnapshot(@Nullable BlockPos parent, boolean initialized, String ssid,
                                            String passwordHash, boolean encrypted) {
        if (matrixNetworkMigrated && java.util.Objects.equals(linkedMatrixPos(), parent)
                && networkInitialized == initialized && networkId.equals(ssid)
                && networkPasswordHash.equals(passwordHash) && networkEncrypted == encrypted) return;
        matrixNetworkMigrated = true;
        hasMatrixLink = parent != null;
        if (parent != null) linkedMatrixPos = parent.asLong();
        networkInitialized = initialized;
        networkId = ssid;
        networkPasswordHash = passwordHash;
        networkEncrypted = encrypted;
        if (NodeConnections.isNode(this) && level instanceof ServerLevel serverLevel)
            updateNodeUsersNetwork(LOADED.getOrDefault(serverLevel, Set.of()), ssid);
        changed();
        sync();
    }

    private void initializeNodeConnection() {
        if (!(level instanceof ServerLevel serverLevel) || !NodeConnections.isUser(this)) return;
        var data = NodeConnections.get(serverLevel);
        if (!nodeConnectionMigrated && hasNodeLink) data.importLegacy(this, BlockPos.of(linkedNodePos));
        BlockPos parent = data.linkedNode(worldPosition);
        String id = parent == null ? "" : networkId;
        if (parent != null && level.hasChunkAt(parent)
                && level.getBlockEntity(parent) instanceof ACMachineBlockEntity node && NodeConnections.isNode(node)) id = node.networkId;
        updateNodeConnectionSnapshot(parent, id);
    }

    /** Client/display mirror; the dimension connection table is authoritative. */
    public void updateNodeConnectionSnapshot(@Nullable BlockPos parent, String id) {
        if (nodeConnectionMigrated && java.util.Objects.equals(linkedNodePos(), parent) && networkId.equals(id)) return;
        nodeConnectionMigrated = true;
        hasNodeLink = parent != null;
        if (parent != null) linkedNodePos = parent.asLong();
        networkId = id;
        networkPasswordHash = "";
        hasMatrixLink = false;
        changed();
        sync();
    }

    public void setWirelessEnergy(double value) {
        if (energy.exactEnergy() != value) {
            energy.setExactEnergy(value);
            changed();
        }
    }

    /** Original BlockCatEngine activation: toggle a random reachable node, without password auth. */
    public void interactCatEngine(ServerPlayer player) {
        if (kind != MachineKind.CAT_ENGINE || !(level instanceof ServerLevel serverLevel)
                || !(this instanceof IWirelessGenerator generator)) return;
        initializeNodeConnection();
        var data = NodeConnections.get(serverLevel);
        if (data.linkedNode(worldPosition) != null) {
            NeoForge.EVENT_BUS.post(new UnlinkUserEvent(generator));
            player.sendSystemMessage(Component.translatable("ac.cat_engine.unlink"));
            return;
        }
        var candidates = data.nodesInRange(serverLevel, worldPosition);
        if (candidates.isEmpty()) {
            player.sendSystemMessage(Component.translatable("ac.cat_engine.notfound"));
        } else {
            var node = candidates.get(level.random.nextInt(candidates.size()));
            player.sendSystemMessage(Component.translatable("ac.cat_engine.linked", node.getNodeName()));
            NeoForge.EVENT_BUS.post(new LinkUserEvent(generator, node));
        }
    }

    private static String hashPassword(String password) {
        return cn.academy.energy.WirelessPasswords.hash(password);
    }

    /** Explicit cleanup for test/admin callers; normal block removal waits for the wireless tick. */
    public void disconnectNetworkOnBreak() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Breaking a tile is detected as removal in the original managers, not a user unlink event.
        if (this instanceof IWirelessMatrix) MatrixNetworks.get(serverLevel).remove(serverLevel, worldPosition);
        else if (this instanceof IWirelessNode) {
            MatrixNetworks.get(serverLevel).unlink(worldPosition);
            NodeConnections.get(serverLevel).removeNode(serverLevel, worldPosition);
        } else if (this instanceof IWirelessUser) NodeConnections.get(serverLevel).unlink(worldPosition);
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5, stack.copy()));
                items.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private void changed() {
        setChanged();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Capture now: sendBlockUpdated defers serialization until the chunk broadcast at tick end. */
    private void sendSnapshot(double radius, CompoundTag snapshot) {
        if (level instanceof ServerLevel serverLevel) {
            var packet = ClientboundBlockEntityDataPacket.create(this, (tile, registries) -> snapshot);
            serverLevel.getServer().getPlayerList().broadcast(null,
                    worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5,
                    radius, level.dimension(), packet);
        }
    }

    private void syncMetalFormer() {
        if (level == null || level.isClientSide) return;
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean("FormerStateOnly", true);
        snapshot.putInt("Mode", mode);
        snapshot.putInt("FormerWorkCounter", formerWorkCounter);
        if (currentRecipe != null) {
            snapshot.putInt("FormerRecipeMode", currentRecipe.mode().ordinal());
            snapshot.put("FormerRecipeInput", currentRecipe.input().save(level.registryAccess()));
        }
        sendSnapshot(12, snapshot);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        if (kind == MachineKind.CAT_ENGINE) tag.putDouble("TickGeneration", lastGeneration);
        tag.putDouble("EnergyFraction", energy.exactEnergy() - energy.getEnergyStored());
        tag.putInt("Progress", progress());
        if (kind == MachineKind.IMAG_FUSOR) {
            tag.putDouble("FusorWorkProgress", fusorWorkProgress);
            ItemStack recipeInput = currentRecipe == null ? pendingRecipeInput : currentRecipe.input();
            if (!recipeInput.isEmpty()) tag.put("FusorRecipeInput", recipeInput.save(registries));
        }
        tag.putInt("Mode", mode);
        tag.putInt("PhaseLiquid", phaseLiquid);
        tag.putString("NetworkId", networkId);
        tag.putString("NetworkPasswordHash", networkPasswordHash);
        tag.putString("NetworkOwner", networkOwner);
        tag.putString("NodeName", nodeName);
        tag.putString("NodePasswordHash", nodePasswordHash);
        if (nodePasswordPlain != null) tag.putString("NodePasswordPlain", nodePasswordPlain);
        tag.putBoolean("NetworkEncrypted", networkEncrypted);
        tag.putBoolean("NodeEncrypted", nodeEncrypted);
        tag.putString("NodeOwner", nodeOwner);
        tag.putBoolean("HasMatrixLink", hasMatrixLink);
        tag.putBoolean("MatrixNetworkMigrated", matrixNetworkMigrated);
        tag.putBoolean("NetworkInitialized", networkInitialized);
        if (hasMatrixLink) tag.putLong("LinkedMatrixPos", linkedMatrixPos);
        tag.putBoolean("HasNodeLink", hasNodeLink);
        tag.putBoolean("NodeConnectionMigrated", nodeConnectionMigrated);
        if (hasNodeLink) tag.putLong("LinkedNodePos", linkedNodePos);
        tag.putString("PlacerId", placerId);
        tag.putString("PlacerName", placerName);
        tag.putBoolean("InterfererEnabled", interfererEnabled);
        tag.putDouble("InterfererRange", interfererRange);
        net.minecraft.nbt.ListTag whitelistTag = new net.minecraft.nbt.ListTag();
        interfererWhitelist.forEach(value -> whitelistTag.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("InterfererWhitelist", whitelistTag);
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setExactEnergy(tag.getInt("Energy") + tag.getDouble("EnergyFraction"));
        if (kind == MachineKind.CAT_ENGINE) lastGeneration = tag.getDouble("TickGeneration");
        currentRecipe = null;
        pendingRecipeInput = ItemStack.EMPTY;
        formerWorkCounter = 0;
        fusorWorkProgress = 0;
        fusorCheckCooldown = GameplayData.integer("machines/imag_fusor", "recipe_check_interval");
        mode = Math.max(0, Math.min(3, tag.getInt("Mode")));
        phaseLiquid = Math.max(0, Math.min(GameplayRules.tankCapacity(kind), tag.getInt("PhaseLiquid")));
        networkId = tag.getString("NetworkId");
        networkPasswordHash = tag.getString("NetworkPasswordHash");
        networkOwner = tag.getString("NetworkOwner");
        nodeName = tag.contains("NodeName") ? tag.getString("NodeName") : "Unnamed";
        nodePasswordHash = tag.getString("NodePasswordHash");
        nodePasswordPlain = tag.contains("NodePasswordPlain") ? tag.getString("NodePasswordPlain")
                : nodePasswordHash.isEmpty() || nodePasswordHash.equals(hashPassword("")) ? "" : null;
        networkEncrypted = tag.contains("NetworkEncrypted") ? tag.getBoolean("NetworkEncrypted")
                : !networkPasswordHash.isEmpty() && !networkPasswordHash.equals(hashPassword(""));
        nodeEncrypted = tag.contains("NodeEncrypted") ? tag.getBoolean("NodeEncrypted")
                : !nodePasswordHash.isEmpty() && !nodePasswordHash.equals(hashPassword(""));
        nodeOwner = tag.getString("NodeOwner");
        hasMatrixLink = tag.getBoolean("HasMatrixLink");
        matrixNetworkMigrated = tag.getBoolean("MatrixNetworkMigrated");
        networkInitialized = tag.getBoolean("NetworkInitialized");
        linkedMatrixPos = tag.getLong("LinkedMatrixPos");
        hasNodeLink = tag.getBoolean("HasNodeLink");
        nodeConnectionMigrated = tag.getBoolean("NodeConnectionMigrated");
        linkedNodePos = tag.getLong("LinkedNodePos");
        placerId = tag.getString("PlacerId");
        placerName = tag.getString("PlacerName");
        interfererEnabled = tag.contains("InterfererEnabled") ? tag.getBoolean("InterfererEnabled") : GameplayData.flag("machines/ability_interferer", "default_enabled");
        interfererRange = tag.contains("InterfererRange") ? Math.clamp(tag.getDouble("InterfererRange"), GameplayData.number("machines/ability_interferer", "minimum_range"), GameplayData.number("machines/ability_interferer", "maximum_range")) : GameplayData.number("machines/ability_interferer", "minimum_range");
        interfererWhitelist.clear();
        net.minecraft.nbt.ListTag whitelistTag = tag.getList("InterfererWhitelist", net.minecraft.nbt.Tag.TAG_STRING);
        for (net.minecraft.nbt.Tag value : whitelistTag) interfererWhitelist.add(value.getAsString());
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
        if (kind == MachineKind.IMAG_FUSOR) {
            if (tag.contains("FusorWorkProgress")) {
                fusorWorkProgress = Math.max(0, Math.min(1, tag.getDouble("FusorWorkProgress")));
                pendingRecipeInput = ItemStack.parseOptional(registries, tag.getCompound("FusorRecipeInput"));
            } else if (tag.getInt("Progress") > 0) {
                // Migration from this port's pre-.19 snapshots, not a 1.12.2 save converter.
                fusorWorkProgress = Math.min(1, tag.getInt("Progress") / 120.0);
                pendingRecipeInput = items.getStackInSlot(0).copy();
            }
            pendingRecipeMode = 0;
        } else if (kind == MachineKind.METAL_FORMER && tag.getBoolean("ProcessingSync")) {
            // Runtime update packets carry work state; a disk reload intentionally starts a fresh search.
            formerWorkCounter = tag.getInt("FormerWorkCounter");
            pendingRecipeMode = tag.getInt("FormerRecipeMode");
            pendingRecipeInput = ItemStack.parseOptional(registries, tag.getCompound("FormerRecipeInput"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        if (kind == MachineKind.METAL_FORMER) {
            tag.putBoolean("ProcessingSync", true);
            tag.putInt("FormerWorkCounter", formerWorkCounter);
            if (currentRecipe != null) {
                tag.putInt("FormerRecipeMode", currentRecipe.mode().ordinal());
                tag.put("FormerRecipeInput", currentRecipe.input().save(registries));
            }
        }
        tag.remove("NetworkPasswordHash");
        tag.remove("NetworkOwner");
        tag.remove("NodePasswordHash");
        tag.remove("NodePasswordPlain");
        tag.remove("NodeOwner");
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (kind == MachineKind.METAL_FORMER && tag.getBoolean("FormerStateOnly")) {
            // The original ten-tick message updates only the job and mode, not energy or inventory.
            mode = Math.max(0, Math.min(3, tag.getInt("Mode")));
            formerWorkCounter = tag.getInt("FormerWorkCounter");
            currentRecipe = null;
            pendingRecipeMode = tag.getInt("FormerRecipeMode");
            pendingRecipeInput = ItemStack.parseOptional(registries, tag.getCompound("FormerRecipeInput"));
            restoreProcessingRecipe();
        } else {
            super.onDataPacket(connection, packet, registries);
        }
    }
}
