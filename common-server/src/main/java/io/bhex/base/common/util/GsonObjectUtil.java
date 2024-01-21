package io.bhex.base.common.util;

import com.google.gson.JsonObject;

import java.math.BigDecimal;

public class GsonObjectUtil {

    public static String getAsString(JsonObject jo, String key) {
        if (jo == null || key == null) {
            return "";
        }
        if (jo.get(key) == null || jo.get(key).isJsonNull()) {
            return "";
        }
        return jo.get(key).getAsString();
    }

    public static BigDecimal getAsBigDecimal(JsonObject jo, String key) {
        if (jo == null || key == null) {
            return BigDecimal.ZERO;
        }
        if (jo.get(key) == null || jo.get(key).isJsonNull()) {
            return BigDecimal.ZERO;
        }
        return jo.get(key).getAsBigDecimal();
    }

    public static boolean getAsBoolean(JsonObject jo, String key) {
        if (jo == null || key == null) {
            return false;
        }
        if (jo.get(key) == null || jo.get(key).isJsonNull()) {
            return false;
        }
        return jo.get(key).getAsBoolean();
    }

    public static int getAsInt(JsonObject jo, String key, int defaultValue) {
        if (jo == null || key == null) {
            return defaultValue;
        }
        if (jo.get(key) == null || jo.get(key).isJsonNull()) {
            return defaultValue;
        }
        return jo.get(key).getAsInt();
    }
}
