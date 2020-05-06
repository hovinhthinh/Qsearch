package util;

import com.google.gson.JsonSyntaxException;

public class Gson {
    private static ThreadLocal<com.google.gson.Gson> GSON_LOCAL = ThreadLocal.withInitial(() -> new com.google.gson.Gson());

    public static String toJson(Object src) {
        return GSON_LOCAL.get().toJson(src);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        return GSON_LOCAL.get().fromJson(json, classOfT);
    }
}
