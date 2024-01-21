package io.bhex.base.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class NoticeRenderUtil {

    private static final Pattern PARAM_PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Za-z_$]+[A-Za-z_$\\d]*)\\}");


    public static String render(String content, List<String> params, Map<String, String> reqParam) {
        if (!reqParam.isEmpty()) {
            String result = render(content, reqParam);
            if (result.contains("{") && result.contains("}") && !CollectionUtils.isEmpty(params)) {
                log.warn("TEMPLATE_ERROR {}", content);
                return render(content, params);
            }
            return result;
        } else {
            return render(content, params);
        }
    }

    private static String render(String content, List<String> params) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        int index = -1;
        while (matcher.find()) {
            String key = matcher.group();
            content = content.replace(key, params.get(++index));
        }
        return content;
    }

    private static String render(String content, Map<String, String> valueMap) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            String key = matcher.group();
            String keyName = key.substring(1, key.length() - 1).trim();
            if (valueMap.containsKey(keyName)) {
                content = content.replace(key, valueMap.getOrDefault(keyName, ""));
            }
        }
        return content;
    }

    public static String renderEmailContent(String content, List<String> params, Map<String, String> reqParam) {
        if (!reqParam.isEmpty()) {
            String result = renderEmailContent(content, reqParam);
            if (result.contains("{") && result.contains("}") && !CollectionUtils.isEmpty(params)) {
                log.warn("TEMPLATE_ERROR {}", content);
                return renderEmailContent(content, params);
            }
            return result;
        } else {
            return renderEmailContent(content, params);
        }
    }

    private static String renderEmailContent(String content, List<String> params) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        int index = -1;
        while (matcher.find()) {
            String key = matcher.group();
            content = content.replace(key, "<span style=\"font-size:18px;font-weight:700;\">" + params.get(++index) + "</span>");
        }
        return content;
    }

    private static String renderEmailContent(String content, Map<String, String> valueMap) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            String key = matcher.group();
            String keyName = key.substring(1, key.length() - 1).trim();
            if (valueMap.containsKey(keyName)) {
                content = content.replace(key, "<span style=\"font-size:18px;font-weight:700;\">" + valueMap.getOrDefault(keyName, "") + "</span>");
            }
        }
        return content;
    }

    public static String render2SendCloudTmpl(String content) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        int index = 0;
        while (matcher.find()) {
            String key = matcher.group();
            content = content.replace(key, "%" + (++index) + "%");
        }
        return content;
    }

    public static String render2ZhuTongTmpl(String content) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        int index = 0;
        while (matcher.find()) {
            String key = matcher.group();
            content = content.replace(key, "{var_" + (++index) + "}");
        }
        return content;
    }

    public static Combo2<String, Integer> render2ZhuTongTmpl2(String content) {
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(content);
        int index = 0;
        while (matcher.find()) {
            String key = matcher.group();
            System.out.println(key);
            content = content.replace(key, "{var_" + (++index) + "}");
        }
        return new Combo2<>(content, index);
    }


    public static void main(String[] args) {
        String content = " 验证码{code}，您正在~broker~绑定谷歌验证，请勿向任何人提供验证码.";
       // String content = "尊敬的用户：\n\t您的验证码是{p1}，有效期{sdf}分钟，请勿告诉他人。";

        List<String> params = new ArrayList<>();
        params.add("341431");
        params.add("5");
        System.out.println(render2ZhuTongTmpl2(content));

    }

}

