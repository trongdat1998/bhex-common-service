package io.bhex.base.common.util;

public enum EnableStatusEnum {
    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    private int value;
    private String desc;

    EnableStatusEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }
}
