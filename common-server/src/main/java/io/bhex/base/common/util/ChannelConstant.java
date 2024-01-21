package io.bhex.base.common.util;

/**
 * @Description:
 * @Date: 2019/2/1 下午1:45
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
public class ChannelConstant {

    public static boolean VPN_URL_VALID = false; //国内通道通过vpn访问是否有效

    public static final String AWS = "AWS";

    public static final String TENCENT = "TENCENT";

    public static final String SEND_CLOUD = "SEND_CLOUD";


    public static final Integer BIZ_TYPE_SMS = 1;

    public static final Integer BIZ_TYPE_EMAIL = 2;

    public static final Integer BIZ_TYPE_PUSH = 3;


    public enum RouterType {
        FORBID_CHANNEL(1, "禁用渠道"),
        PRIORITY_CHANNEL(2, "优先选用渠道"),
        BLACK_LIST(3, "临时关停消息发送"),
        NO_OP(4, "消息不发送"),
        EMAIL_MXIP_BLACK_LIST(5, "邮件mx ip黑名单");

        private int value;
        private String desc;

        RouterType(int value, String desc) {
            this.value = value;
            this.desc = desc;
        }

        public int getValue() {
            return value;
        }
    }

    public enum SpType {
        EMAIL,
        SMS,
        VOICE_SMS;
    }



}
