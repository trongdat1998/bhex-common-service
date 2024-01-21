/*
 ************************************
 * @项目名称: broker
 * @文件名称: JsonUtil
 * @Date 2018/05/22
 * @Author will.zhao@bhex.io
 * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 **************************************
 */
package io.bhex.base.common.util;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.gson.*;
import com.google.protobuf.util.JsonFormat;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@Slf4j
public class JsonUtil {

    private JsonUtil() {
    }

    private static final Gson DEFAULT_GSON = new GsonBuilder()
            .setLongSerializationPolicy(LongSerializationPolicy.STRING)
            .registerTypeAdapter(Date.class, (JsonSerializer<Date>) (date, type, context) -> new JsonPrimitive(defaultDateFormat().format(date)))
            .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (date, type, context) -> {
                try {
                    return defaultDateFormat().parse(date.getAsString());
                } catch (ParseException e) {
                    throw new JsonParseException(e);
                }
            })
            .registerTypeAdapter(BigDecimal.class, (JsonSerializer<BigDecimal>) (number, type, context) -> new JsonPrimitive(number.stripTrailingZeros().toPlainString()))
            .registerTypeAdapter(BigDecimal.class, (JsonDeserializer<BigDecimal>) (number, type, context) -> new BigDecimal(number.getAsString()))
            .create();

    public static Gson defaultGson() {
        return DEFAULT_GSON;
    }

    private static final JsonParser DEFAULT_JSON_PARSER = new JsonParser();

    public static JsonParser defaultJsonParser() {
        return DEFAULT_JSON_PARSER;
    }

    private static final JsonFormat.Printer DEFAULT_PROTOBUF_JSON_PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace().includingDefaultValueFields();

    public static JsonFormat.Printer defaultProtobufJsonPrinter() {
        return DEFAULT_PROTOBUF_JSON_PRINTER;
    }

    private static final JsonFormat.Parser DEFAULT_PROTOBUF_JSON_PARSER = JsonFormat.parser().ignoringUnknownFields();

    public static JsonFormat.Parser defaultProtobufJsonParser() {
        return DEFAULT_PROTOBUF_JSON_PARSER;
    }

    private static DateFormat defaultDateFormat() {
        DateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return df;
    }

    /**
     * jsonPath:
     * .user.name
     * [0].item_id
     * .ref_user[0].user_id
     */
    private static JsonElement tryGetElement(JsonElement element, String fullPath, String path) {
        if (path.length() <= 0) {
            return element;
        } else if (path.charAt(0) == '.') {
            int idx = path.length();
            final int dotIdx = path.indexOf('.', 1);
            if (dotIdx >= 0 && dotIdx < idx) {
                idx = dotIdx;
            }
            final int leftBracketIdx = path.indexOf('[', 1);
            if (leftBracketIdx >= 0 && leftBracketIdx < idx) {
                idx = leftBracketIdx;
            }

            String fieldName = path.substring(1, idx);
            if (fieldName.isEmpty()) {
                throw new IllegalArgumentException("invalid json path : '" + fullPath + "'");
            }

            if (fieldName.equals("length") && element.isJsonArray()) {
                return new JsonPrimitive(element.getAsJsonArray().size());
            }

            if (!element.isJsonObject()) {
                return null;
            }

            JsonElement e = element.getAsJsonObject().get(fieldName);
            if (e == null) {
                return null;
            }

            return tryGetElement(e, fullPath, path.substring(idx));
        } else if (path.charAt(0) == '[') {
            int rightBracketIdx = path.indexOf(']', 1);
            if (rightBracketIdx < 0) {
                throw new IllegalArgumentException("invalid json path : '" + fullPath + "'");
            }

            Integer index = Ints.tryParse(path.substring(1, rightBracketIdx));
            if (index == null || index < 0) {
                throw new IllegalArgumentException("invalid json path : '" + fullPath + "'");
            }

            if (!element.isJsonArray()) {
                return null;
            }

            JsonArray array = element.getAsJsonArray();
            JsonElement e = index < array.size() ? array.get(index) : null;

            if (e == null) {
                return null;
            }

            return tryGetElement(e, fullPath, path.substring(rightBracketIdx + 1));
        } else {
            throw new IllegalArgumentException("invalid json path : '" + path + "'");
        }
    }

    public static JsonElement tryGetElement(JsonElement element, String path) {
        return tryGetElement(element, path, path);
    }

    public static JsonObject tryGetObject(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonObject()) {
            return null;
        }
        return e.getAsJsonObject();
    }

    public static JsonArray tryGetArray(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        return e.getAsJsonArray();
    }

    public static String tryGetString(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        return e.getAsString();
    }

    public static String getString(JsonElement element, String path, @Nullable String defaultValue) {
        return nullToValue(tryGetString(element, path), defaultValue);
    }

    public static Long tryGetLong(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsLong();
        } else {
            return Longs.tryParse(p.getAsString());
        }
    }

    public static Long getLong(JsonElement element, String path, @Nullable Long defaultValue) {
        return nullToValue(tryGetLong(element, path), defaultValue);
    }

    public static Integer tryGetInt(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsInt();
        } else {
            return Ints.tryParse(p.getAsString());
        }
    }

    public static Integer getInt(JsonElement element, String path, @Nullable Integer defaultValue) {
        return nullToValue(tryGetInt(element, path), defaultValue);
    }

    public static Double tryGetDouble(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsDouble();
        } else {
            return Doubles.tryParse(p.getAsString());
        }
    }

    public static Double getDouble(JsonElement element, String path, @Nullable Double defaultValue) {
        return nullToValue(tryGetDouble(element, path), defaultValue);
    }

    public static Float tryGetFloat(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsFloat();
        } else {
            return Floats.tryParse(p.getAsString());
        }
    }

    public static Float getFloat(JsonElement element, String path, @Nullable Float defaultValue) {
        return nullToValue(tryGetFloat(element, path), defaultValue);
    }

    public static Boolean tryGetBoolean(JsonElement element, String path) {
        JsonElement e = tryGetElement(element, path);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return p.getAsBoolean();
        } else {
            String str = p.getAsString();
            if ("true".equalsIgnoreCase(str)) {
                return true;
            } else if ("false".equalsIgnoreCase(str)) {
                return false;
            } else {
                return null;
            }
        }
    }

    public static Boolean getBoolean(JsonElement element, String path, @Nullable Boolean defaultValue) {
        return nullToValue(tryGetBoolean(element, path), defaultValue);
    }

    private static <T> T nullToValue(T value, T def) {
        return value == null ? def : value;
    }

}
