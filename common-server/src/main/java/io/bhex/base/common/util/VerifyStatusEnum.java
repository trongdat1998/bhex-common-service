package io.bhex.base.common.util;

public enum  VerifyStatusEnum {
    PROCESSING(1, "处理中"),
    INIT(0, "初始状态"),
    SUCESS(2,"成功"),
    FAILED(3,"审核未通过");

    private int value;
    private String desc;

    VerifyStatusEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }
}
