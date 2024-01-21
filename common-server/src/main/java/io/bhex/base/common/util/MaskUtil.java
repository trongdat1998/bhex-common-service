/*
 ************************************
 * @项目名称: bhcard
 * @文件名称: MaskUtil
 * @Date 2018/06/27
 * @Author will.zhao@bhex.io
 * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 **************************************
 */
package io.bhex.base.common.util;

import org.apache.commons.lang3.StringUtils;

public class MaskUtil {

    public static String maskMobile(String tel) {
        if (StringUtils.isEmpty(tel)) {
            return "";
        }
        if (tel.length() > 8) {
            return tel.replaceAll("(?<=...).(?=....)", "*");
        } else {
            return tel.replaceAll("(?<=..).(?=..)", "*");
        }
    }

    public static String maskMobile(String areaCode, String number) {
        if (areaCode == "86") {
            return maskMobile(number);
        } else {
            return String.format("(%s)%s", areaCode, maskMobile(number));
        }
    }

    public static String maskEmail(String email) {
        return email.replaceAll("(?<=\\w{4}).*?(?=\\w{2}@)", "*");
    }

    public static String maskValidateCode(String content) {
        return content.replaceAll("\\d{6}", "******");
    }

    public static void main(String[] args) {
        System.out.println(maskEmail("a122239@163.com"));
    }
}