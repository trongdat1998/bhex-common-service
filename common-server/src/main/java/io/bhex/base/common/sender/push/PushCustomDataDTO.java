package io.bhex.base.common.sender.push;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import lombok.Data;

import java.util.Map;

@Data
public class PushCustomDataDTO {
    private String type;
    private String url;
    private String page;
    private String param;
    private String reqOrderId;


    public static Map<String, String> convertMap(PushCustomDataDTO data) {
        Map<String, String> result = Maps.newHashMap();
        result.put("type", Strings.nullToEmpty(data.getType()));
        result.put("url", Strings.nullToEmpty(data.getUrl()));
        result.put("page", Strings.nullToEmpty(data.getPage()));
        result.put("param", Strings.nullToEmpty(data.getParam()));
        result.put("reqOrderId", Strings.nullToEmpty(data.getReqOrderId()));
        return result;
    }

    public static String convertString(PushCustomDataDTO data) {
        Map<String, String> item = convertMap(data);
        StringBuilder builder = new StringBuilder();
        for (String key : item.keySet()) {
            builder.append("&").append(key).append("=").append(item.getOrDefault(key, ""));
        }
        return "?" + builder.toString().replaceFirst("&", "");
    }
}
