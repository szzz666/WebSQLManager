package top.szzz666.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * JSON 工具类，基于 Gson
 */
public class JsonUtil {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private static final Gson COMPACT_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static String toCompactJson(Object obj) {
        return COMPACT_GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    /**
     * 解析为 Map
     */
    public static Map<String, Object> toMap(String json) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return GSON.fromJson(json, type);
    }

    /**
     * 格式化 JSON 字符串
     */
    public static String pretty(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return GSON.toJson(element);
        } catch (Exception e) {
            return json;
        }
    }
}
