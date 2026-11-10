package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AbilityActionPayload(int action, int value, String argument) implements CustomPacketPayload {
    public static final int TOGGLE = 0;
    public static final int USE_SLOT = 1;
    public static final int SELECT_PRESET = 2;
    public static final int REQUEST_SYNC = 3;
    public static final int LEARN_SKILL = 4;
    public static final int SWITCH_PRESET = 6;
    public static final int KEY_DOWN = 7;
    public static final int KEY_UP = 8;
    public static final int KEY_ABORT = 9;
    public static final int MOUSE_WHEEL = 10;
    public static final int MOVEMENT_INPUT = 11;
    public static final int MOVEMENT_ABORT = 12;
    public static final int FLUSH_CONTROLS = 13;

    public static final Type<AbilityActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "ability_action"));
    public static final StreamCodec<ByteBuf, AbilityActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityActionPayload::action,
            ByteBufCodecs.VAR_INT, AbilityActionPayload::value,
            ByteBufCodecs.STRING_UTF8, AbilityActionPayload::argument,
            AbilityActionPayload::new);

    public static AbilityActionPayload toggle() { return new AbilityActionPayload(TOGGLE, 0, ""); }
    public static AbilityActionPayload useSlot(int slot) { return new AbilityActionPayload(USE_SLOT, slot, ""); }
    public static AbilityActionPayload select(int slot, String skill) { return new AbilityActionPayload(SELECT_PRESET, slot, skill); }
    public static AbilityActionPayload requestSync() { return new AbilityActionPayload(REQUEST_SYNC, 0, ""); }
    public static AbilityActionPayload learn(String skill) { return new AbilityActionPayload(LEARN_SKILL, 0, skill); }
    public static AbilityActionPayload switchPreset(int preset) { return new AbilityActionPayload(SWITCH_PRESET, preset, ""); }
    public static AbilityActionPayload keyDown(int slot) { return new AbilityActionPayload(KEY_DOWN, slot, ""); }
    public static AbilityActionPayload keyDown(int slot, int requestId) {
        return new AbilityActionPayload(KEY_DOWN, slot, requestId == 0 ? "" : Integer.toString(requestId));
    }
    public int contextRequestId() {
        try { return Math.max(0, Integer.parseInt(argument)); }
        catch (NumberFormatException ignored) { return 0; }
    }
    public static AbilityActionPayload keyUp(int slot) { return new AbilityActionPayload(KEY_UP, slot, ""); }
    public static AbilityActionPayload keyAbort(int slot) { return new AbilityActionPayload(KEY_ABORT, slot, ""); }
    public static AbilityActionPayload mouseWheel(int delta) { return new AbilityActionPayload(MOUSE_WHEEL, delta, ""); }
    public static AbilityActionPayload movementInput(int bits) { return new AbilityActionPayload(MOVEMENT_INPUT, bits, ""); }
    public static AbilityActionPayload movementAbort(int heldBits) { return new AbilityActionPayload(MOVEMENT_ABORT, heldBits, ""); }
    public static AbilityActionPayload flushControls() { return new AbilityActionPayload(FLUSH_CONTROLS, 0, ""); }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
