package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ACAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AcademyCraft.MOD_ID);
    // Persistence stays in academy:ability for existing worlds; this attachment owns the live instance.
    public static final Supplier<AttachmentType<AbilityState>> ABILITY = TYPES.register("ability_runtime",
            () -> AttachmentType.builder(AbilityState::new).build());

    public static void register(IEventBus bus) { TYPES.register(bus); }

    private ACAttachments() {}
}
