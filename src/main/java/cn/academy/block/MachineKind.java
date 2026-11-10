package cn.academy.block;

import cn.academy.data.GameplayRules;

import cn.academy.data.GameplayData;

public enum MachineKind {
    NODE_BASIC,
    NODE_STANDARD,
    NODE_ADVANCED,
    MATRIX,
    CAT_ENGINE,
    SOLAR_GENERATOR,
    PHASE_GENERATOR,
    WIND_BASE,
    WIND_PILLAR,
    WIND_GENERATOR,
    IMAG_FUSOR,
    METAL_FORMER,
    DEVELOPER_NORMAL,
    DEVELOPER_ADVANCED,
    ABILITY_INTERFERER,
    FE_INPUT,
    FE_OUTPUT,
    EU_INPUT,
    EU_OUTPUT;

    private String path() { return GameplayRules.machine(this); }
    public int capacity() { return GameplayData.integer(path(), "capacity"); }
    public int transfer() { return GameplayData.integer(path(), "transfer"); }
    public int generation() { return GameplayData.integer(path(), "generation"); }

    public boolean isGenerator() {
        return this == CAT_ENGINE || this == SOLAR_GENERATOR || this == PHASE_GENERATOR || this == WIND_BASE;
    }

    public boolean isProcessor() {
        return this == IMAG_FUSOR || this == METAL_FORMER;
    }

    public boolean isBridgeInput() {
        return this == FE_INPUT || this == EU_INPUT;
    }

    public boolean isBridgeOutput() {
        return this == FE_OUTPUT || this == EU_OUTPUT;
    }

    public boolean isNetworkNode() {
        return this == NODE_BASIC || this == NODE_STANDARD || this == NODE_ADVANCED || this == MATRIX;
    }
}
