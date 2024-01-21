package io.bhex.base.common.util;

public enum PushChannelEnum {
    APNS,
    APPLE,
    HUAWEI,
    //XIAOMI,
    FCM;
    //OPPO
    public static PushChannelEnum getByName(String pushChannel){
        for (PushChannelEnum c : PushChannelEnum.values()) {
            if (c.name().equalsIgnoreCase(pushChannel)) {
                return c;
            }
        }
        return null;
    }
}
