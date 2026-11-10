package cn.academy.event;

import net.neoforged.bus.api.Event;

/** Client settings edit; property is a modern ConfigValue or KeyMapping instead of Forge's legacy Property. */
public final class ConfigModifyEvent extends Event {
    public final String category;
    public final String id;
    public final Object property;

    public ConfigModifyEvent(String category, String id, Object property) {
        this.category = category;
        this.id = id;
        this.property = property;
    }
}
