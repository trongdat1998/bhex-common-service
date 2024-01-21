package io.bhex.base.common.util;


import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class LanguageConstants {

    private static final Table<String, String, String> table = HashBasedTable.create();



    static {
        table.put("antiPhishingCode", "zh_CN", "防钓鱼码");
        table.put("antiPhishingCode", "en_US", "Anti-phishing code");
        table.put("antiPhishingCode", "ko_KR", "피싱 방지 코드");
        table.put("antiPhishingCode", "ja_JP", "アンティフィッシングコード");

        table.put("emailBeginTitle", "zh_CN", "尊敬的~broker~用户您好：");
        table.put("emailBeginTitle", "en_US", "Dear ~broker~ user:  ");
        table.put("emailBeginTitle", "vi_VN", "Kính gửi người dùng: ");
        table.put("emailBeginTitle", "ko_KR", "안녕하세요~broker~회원님：");
        table.put("emailBeginTitle", "ja_JP", "お客様へ：");
        table.put("emailBeginTitle", "es_ES", "Estimado ~broker~ usuario：");
    }

    public static String getAntiPhishingHold(String language) {
        return getString("antiPhishingCode", language);
    }

    public static String getEmailBeginTitle(String language) {
        return getString("emailBeginTitle", language);
    }

    private static String getString(String key, String language) {
        String str = table.get(key, language);
        if (StringUtils.isEmpty(str)) {
            str = table.get(key, Locale.US.toString());
        }
        return Strings.nullToEmpty(str);
    }

}
