package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.menu.ACMachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AcademyCraft.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ACMachineMenu>> MACHINE =
            MENUS.register("machine", () -> IMenuTypeExtension.create(ACMachineMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    private ACMenus() {}
}
