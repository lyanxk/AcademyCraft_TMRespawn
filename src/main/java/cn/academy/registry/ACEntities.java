package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.entity.ACElectronBallEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACEntities {
    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AcademyCraft.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ACElectronBallEntity>> ELECTRON_BALL =
            TYPES.register("electron_ball", () -> EntityType.Builder.<ACElectronBallEntity>of(
                    ACElectronBallEntity::new, MobCategory.MISC)
                    .sized(.8f, .8f).clientTrackingRange(2).updateInterval(3)
                    .build("academy:electron_ball"));

    public static final DeferredHolder<EntityType<?>, EntityType<cn.academy.entity.MagManipEntityBlock>> MAG_MANIP_BLOCK =
            TYPES.register("mag_manip_block", () -> EntityType.Builder.<cn.academy.entity.MagManipEntityBlock>of(
                    cn.academy.entity.MagManipEntityBlock::new, MobCategory.MISC)
                    .sized(1,1).clientTrackingRange(2).updateInterval(10).build("academy:mag_manip_block"));

    public static final DeferredHolder<EntityType<?>, EntityType<ACThrownItemEntity>> THROWN_ITEM =
            TYPES.register("thrown_item", () -> EntityType.Builder.<ACThrownItemEntity>of(
                    ACThrownItemEntity::new, MobCategory.MISC)
                    .sized(.25f, .25f).clientTrackingRange(2).updateInterval(3)
                    .build("academy:thrown_item"));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private ACEntities() {}
}
